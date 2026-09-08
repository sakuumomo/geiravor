package io.r_a_d.geiravor

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import io.r_a_d.geiravor.compat.addGifDecoder
import uniffi.geiravor_core.RadioCore

class GeiravorApp : Application(), ImageLoaderFactory {
    lateinit var core: RadioCore
        private set

    override fun onCreate() {
        super.onCreate()
        System.loadLibrary("geiravor_core")
        core = RadioCore(filesDir.absolutePath)
        core.startPoller()
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this).addGifDecoder().build()
}
