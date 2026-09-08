package io.r_a_d.geiravor.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.r_a_d.geiravor.GeiravorApp
import io.r_a_d.geiravor.notify.Alerts
import io.r_a_d.geiravor.playback.PlaybackService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            FIRE, Intent.ACTION_BOOT_COMPLETED -> {
                val app = context.applicationContext
                if (app is GeiravorApp) {
                    if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                        AlarmScheduler.schedule(context, app.core) {}
                        return
                    }
                }
                Alerts.show(context, Alerts.ID_ALARM, "r/a/dio alarm", alarmActions = true)
                ContextCompat.startForegroundService(context, PlaybackService.alarmIntent(context))
            }
            SNOOZE -> {
                val app = context.applicationContext as? GeiravorApp ?: return
                context.startService(PlaybackService.stopIntent(context))
                AlarmScheduler.snooze(context, app.core)
            }
        }
    }

    companion object {
        const val FIRE = "io.r_a_d.geiravor.ALARM_FIRE"
        const val SNOOZE = "io.r_a_d.geiravor.ALARM_SNOOZE"
    }
}
