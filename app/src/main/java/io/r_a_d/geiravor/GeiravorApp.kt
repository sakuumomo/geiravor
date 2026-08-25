package io.r_a_d.geiravor

import android.app.Application
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import io.r_a_d.geiravor.playback.HeadsetReceiver
import io.r_a_d.geiravor.radio.RadioStore
import uniffi.geiravor_core.RadioCore

class GeiravorApp : Application() {
    lateinit var radio: RadioCore
        private set

    private val headset = HeadsetReceiver()

    override fun onCreate() {
        super.onCreate()
        radio = RadioCore()
        radio.start(RadioStore)
        radio.setUiVisible(true)
        ContextCompat.registerReceiver(
            this,
            headset,
            IntentFilter(Intent.ACTION_HEADSET_PLUG),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}
