package io.r_a_d.geiravor

import android.app.Application
import android.app.UiModeManager
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.annotation.ExperimentalCoilApi
import coil.disk.DiskCache
import coil.imageLoader
import coil.memory.MemoryCache
import io.r_a_d.geiravor.data.GeiravorDb
import io.r_a_d.geiravor.data.LastPaintEntity
import io.r_a_d.geiravor.data.MembershipStore
import io.r_a_d.geiravor.playback.AlarmBootReceiver
import io.r_a_d.geiravor.playback.CarModeReceiver
import io.r_a_d.geiravor.playback.FavePolicy
import io.r_a_d.geiravor.playback.HeadsetReceiver
import io.r_a_d.geiravor.radio.RadioStore
import io.r_a_d.geiravor.radio.SnapshotPolicy
import io.r_a_d.geiravor.settings.SettingsStore
import uniffi.geiravor_core.RadioCore
import uniffi.geiravor_core.Status
import uniffi.geiravor_core.StatusListener
import uniffi.geiravor_core.djImageUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class GeiravorApp : Application(), ImageLoaderFactory {
    lateinit var radio: RadioCore
        private set

    private val headset = HeadsetReceiver()
    private val carMode = CarModeReceiver()
    private val ioScope = CoroutineScope(Dispatchers.IO)
    lateinit var db: GeiravorDb
        private set

    override fun onCreate() {
        super.onCreate()
        radio = RadioCore()
        db = GeiravorDb.get(this)
        val settings = SettingsStore(this)
        val paint = runBlocking(Dispatchers.IO) {
            val fromRoom = db.paint().get()?.blob?.let { SnapshotPolicy.decode(it) }
            fromRoom ?: settings.lastPaint()
        }
        paint?.let { RadioStore.hydrateStatus(SnapshotPolicy.toStatus(it)) }
        val home = runBlocking(Dispatchers.IO) {
            FavePolicy.listNick(settings.favesNick.first())
        }
        if (home.isNotEmpty()) {
            val rows = runBlocking(Dispatchers.IO) { db.faves().forNick(home) }
            radio.importMembership(home, MembershipStore.toRows(rows))
        }
        var lastDjImage = paint?.djImage
        radio.start(
            object : StatusListener {
                override fun onUpdate(status: Status, streamDown: Boolean) {
                    val previous = lastDjImage
                    lastDjImage = status.dj.image
                    RadioStore.onUpdate(status, streamDown)
                    if (!previous.isNullOrEmpty() && previous != status.dj.image) {
                        evictDjImage(previous)
                    }
                    ioScope.launch {
                        db.paint().upsert(
                            LastPaintEntity(blob = SnapshotPolicy.encode(SnapshotPolicy.fromStatus(status))),
                        )
                    }
                }
            },
        )
        ioScope.launch {
            AlarmBootReceiver.restore(this@GeiravorApp)
            if (home.isNotEmpty()) {
                runCatching { radio.prefetchFavorites(home) }
            }
            persistHomeFaves(home)
        }
        ContextCompat.registerReceiver(
            this,
            headset,
            IntentFilter(Intent.ACTION_HEADSET_PLUG),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        ContextCompat.registerReceiver(
            this,
            carMode,
            IntentFilter(UiModeManager.ACTION_ENTER_CAR_MODE),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun persistHomeFaves(nick: String) {
        val home = FavePolicy.listNick(nick)
        ioScope.launch {
            radio.keepMembership(home)
            if (home.isEmpty()) {
                db.faves().deleteAll()
                return@launch
            }
            db.faves().deleteOtherNicks(home)
            db.faves().deleteNick(home)
            db.faves().insertAll(MembershipStore.fromRows(home, radio.exportMembership(home)))
        }
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .build()

    @OptIn(ExperimentalCoilApi::class)
    private fun evictDjImage(image: String) {
        val url = djImageUrl(image)
        val loader = imageLoader
        loader.memoryCache?.remove(MemoryCache.Key(url))
        loader.diskCache?.remove(url)
    }
}
