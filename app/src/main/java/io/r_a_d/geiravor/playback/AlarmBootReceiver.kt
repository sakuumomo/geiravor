package io.r_a_d.geiravor.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.r_a_d.geiravor.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AlarmBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                restore(app)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        suspend fun restore(context: Context) {
            val settings = SettingsStore(context)
            AlarmScheduler.scheduleDaily(
                context,
                enabled = settings.alarmEnabled.first(),
                hour = settings.alarmHour.first(),
                minute = settings.alarmMinute.first(),
            )
        }
    }
}
