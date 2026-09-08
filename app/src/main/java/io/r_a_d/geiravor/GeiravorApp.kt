package io.r_a_d.geiravor

import android.app.Application
import android.os.Handler
import android.os.Looper
import coil.ImageLoader
import coil.ImageLoaderFactory
import io.r_a_d.geiravor.alert.AlarmScheduler
import io.r_a_d.geiravor.alert.StationWatch
import io.r_a_d.geiravor.compat.addGifDecoder
import io.r_a_d.geiravor.notify.Alerts
import io.r_a_d.geiravor.settings.SecretsStore
import io.r_a_d.geiravor.ui.UiState
import uniffi.geiravor_core.RadioCore
import uniffi.geiravor_core.Status
import uniffi.geiravor_core.StatusListener
import uniffi.geiravor_core.initLogging

class GeiravorApp : Application(), ImageLoaderFactory {
    lateinit var core: RadioCore
        private set
    lateinit var secrets: SecretsStore
        private set
    lateinit var stillImages: ImageLoader
        private set
    val ui = UiState()
    var heartPaint: (() -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        System.loadLibrary("geiravor_core")
        initLogging(BuildConfig.DEBUG)
        core = RadioCore(filesDir.absolutePath)
        secrets = SecretsStore(this)
        stillImages = ImageLoader.Builder(this).build()
        Alerts.ensureChannel(this)
        core.addListener(object : StatusListener {
            override fun onStatus(status: Status, streamDown: Boolean, playing: Boolean) {
                val nicks = ui.membershipNicks()
                val member = nicks.any { nick ->
                    runCatching {
                        core.membershipHas(
                            nick,
                            if (status.isAfk) status.trackId else 0,
                            status.np,
                        )
                    }.getOrDefault(false)
                }
                Handler(Looper.getMainLooper()).post {
                    ui.applyStatus(status, streamDown, playing)
                    StationWatch.onSnapshot(
                        this@GeiravorApp,
                        status,
                        streamDown,
                        playing,
                        ui.djNotifier,
                        ui.favePlaying,
                        member,
                    )
                }
            }
        })
        ui.load(core, secrets)
        core.startPoller()
        Handler(Looper.getMainLooper()).post {
            StationWatch.sync(this, ui.djNotifier, ui.favePlaying)
            AlarmScheduler.schedule(this, core) {}
        }
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this).addGifDecoder().build()
}
