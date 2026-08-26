package io.r_a_d.geiravor.playback

import android.app.UiModeManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.r_a_d.geiravor.settings.SettingsPolicy
import io.r_a_d.geiravor.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CarModeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != UiModeManager.ACTION_ENTER_CAR_MODE) {
            return
        }
        val sticky = isInitialStickyBroadcast
        if (sticky) {
            return
        }
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val enabled = SettingsStore(appContext).autoStartInVehicle.first()
                if (!SettingsPolicy.shouldStartInVehicle(enabled, enteredCar = true, sticky)) {
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    playLiveStream(appContext)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
