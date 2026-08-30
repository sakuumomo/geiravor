package io.r_a_d.geiravor

import android.app.Application
import android.app.UiModeManager
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
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
import io.r_a_d.geiravor.playback.DjNotifier
import io.r_a_d.geiravor.playback.CarModeReceiver
import io.r_a_d.geiravor.playback.FavePolicy
import io.r_a_d.geiravor.playback.HeadsetReceiver
import io.r_a_d.geiravor.radio.RadioStore
import io.r_a_d.geiravor.radio.SnapshotPolicy
import io.r_a_d.geiravor.settings.SettingsStore
import io.r_a_d.geiravor.compat.setApplicationNight
import io.r_a_d.geiravor.ui.RadioPacks
import io.r_a_d.geiravor.ui.RadioTheme
import io.r_a_d.geiravor.ui.ThemePolicy
import uniffi.geiravor_core.RadioCore
import uniffi.geiravor_core.Status
import uniffi.geiravor_core.StatusListener
import uniffi.geiravor_core.djImageUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import kotlin.coroutines.coroutineContext

class GeiravorApp : Application(), ImageLoaderFactory {
    lateinit var radio: RadioCore
        private set

    @Volatile
    private var nightApplied: Boolean? = null

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
        var lastPaint = paint
        val home = runBlocking(Dispatchers.IO) {
            FavePolicy.listNick(settings.favesNick.first(), settings.ircNick.first())
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
                    val next = SnapshotPolicy.fromStatus(status)
                    if (!SnapshotPolicy.sameOnDisk(lastPaint, next)) {
                        lastPaint = next
                        ioScope.launch {
                            db.paint().upsert(
                                LastPaintEntity(blob = SnapshotPolicy.encode(next)),
                            )
                        }
                    }
                    ioScope.launch {
                        DjNotifier.consider(this@GeiravorApp, settings, status, streamDown)
                    }
                }
            },
        )
        ioScope.launch {
            AlarmBootReceiver.restore(this@GeiravorApp)
            val djOn = settings.djNotifierEnabled.first()
            DjNotifier.enqueue(this@GeiravorApp, djOn)
            if (djOn) {
                DjNotifier.ensureChannel(this@GeiravorApp)
            }
            if (home.isNotEmpty()) {
                runCatching { radio.prefetchFavorites(home) }
            }
            persistHomeFaves(home)
            var start = true
            while (coroutineContext.isActive) {
                applyTheme(processStart = start)
                start = false
                delay(ThemePolicy.delayMs(LocalDate.now(), java.time.ZonedDateTime.now()))
            }
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

    fun refreshTheme(processStart: Boolean) {
        ioScope.launch { applyTheme(processStart) }
    }

    private suspend fun applyTheme(processStart: Boolean) {
        val settings = SettingsStore(this)
        val date = LocalDate.now()
        val now = Instant.now()
        val last = settings.themeLastSniff.first().takeIf { it > 0L }?.let(Instant::ofEpochMilli)
        val seen = settings.themeSeenOn.first().ifEmpty { null }
        val optOut = settings.holidayOptOut.first()
        val userPick = settings.themePack.first()
        var sniffed = radio.themeName()
        if (ThemePolicy.shouldSniff(date, now, last, seen, processStart)) {
            sniffed = runCatching { radio.sniffTheme() }.getOrNull() ?: sniffed
            settings.setThemeLastSniff(now.toEpochMilli())
        }
        val car = !ThemePolicy.holidayPacksOnThisUi(
            resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK,
        )
        val phonePack = ThemePolicy.activePack(userPick, sniffed, optOut, date)
        val pack = if (car) ThemePolicy.autoPack(phonePack) else phonePack
        val key = ThemePolicy.windowKey(date)
        if (key != null && sniffed != null && sniffed in RadioPacks.HOLIDAYS) {
            settings.setThemeSeenOn(key)
        }
        val night = ThemePolicy.isNight(phonePack)
        withContext(Dispatchers.Main) {
            RadioTheme.apply(pack)
            if (ThemePolicy.nightModeChanged(nightApplied, night)) {
                nightApplied = night
                setApplicationNight(this@GeiravorApp, night)
            }
        }
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
            val incoming = MembershipStore.fromRows(home, radio.exportMembership(home))
            val existing = db.faves().forNick(home)
            if (!MembershipStore.changed(existing, incoming)) {
                return@launch
            }
            db.faves().deleteNick(home)
            db.faves().insertAll(incoming)
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
