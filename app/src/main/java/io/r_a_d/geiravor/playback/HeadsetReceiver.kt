package io.r_a_d.geiravor.playback

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

class HeadsetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_HEADSET_PLUG) {
            return
        }
        val plugged = SettingsPolicy.isHeadsetPlugged(intent.getIntExtra("state", 0))
        val sticky = isInitialStickyBroadcast
        if (!plugged || sticky) {
            return
        }
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val enabled = SettingsStore(appContext).autoStartOnPlug.first()
                if (!SettingsPolicy.shouldStartOnPlug(enabled, plugged, sticky)) {
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
