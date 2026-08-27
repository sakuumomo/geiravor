package io.r_a_d.geiravor

import android.app.Application
import android.app.UiModeManager
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import io.r_a_d.geiravor.playback.AlarmBootReceiver
import io.r_a_d.geiravor.playback.CarModeReceiver
import io.r_a_d.geiravor.playback.HeadsetReceiver
import io.r_a_d.geiravor.radio.RadioStore
import uniffi.geiravor_core.RadioCore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GeiravorApp : Application() {
    lateinit var radio: RadioCore
        private set

    private val headset = HeadsetReceiver()
    private val carMode = CarModeReceiver()

    override fun onCreate() {
        super.onCreate()
        radio = RadioCore()
        radio.start(RadioStore)
        CoroutineScope(Dispatchers.IO).launch {
            AlarmBootReceiver.restore(this@GeiravorApp)
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
}
