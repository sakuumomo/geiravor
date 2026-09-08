package io.r_a_d.geiravor

import android.app.Application
import android.os.Handler
import android.os.Looper
import coil.ImageLoader
import coil.ImageLoaderFactory
import io.r_a_d.geiravor.compat.addGifDecoder
import io.r_a_d.geiravor.settings.SecretsStore
import io.r_a_d.geiravor.ui.UiState
import uniffi.geiravor_core.RadioCore
import uniffi.geiravor_core.Status
import uniffi.geiravor_core.StatusListener

class GeiravorApp : Application(), ImageLoaderFactory {
    lateinit var core: RadioCore
        private set
    lateinit var secrets: SecretsStore
        private set
    val ui = UiState()

    override fun onCreate() {
        super.onCreate()
        System.loadLibrary("geiravor_core")
        core = RadioCore(filesDir.absolutePath)
        secrets = SecretsStore(this)
        core.addListener(object : StatusListener {
            override fun onStatus(status: Status, streamDown: Boolean, playing: Boolean) {
                Handler(Looper.getMainLooper()).post {
                    ui.applyStatus(status, streamDown, playing)
                }
            }
        })
        ui.load(core, secrets)
        core.startPoller()
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this).addGifDecoder().build()
}
