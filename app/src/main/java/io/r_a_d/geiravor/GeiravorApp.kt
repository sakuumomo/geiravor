package io.r_a_d.geiravor

import android.app.Application
import io.r_a_d.geiravor.radio.RadioStore
import uniffi.geiravor_core.RadioCore

class GeiravorApp : Application() {
    lateinit var radio: RadioCore
        private set

    override fun onCreate() {
        super.onCreate()
        radio = RadioCore()
        radio.start(RadioStore)
        radio.setUiVisible(true)
    }
}
