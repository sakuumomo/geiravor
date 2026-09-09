package io.r_a_d.geiravor.alert

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.r_a_d.geiravor.GeiravorApp
import io.r_a_d.geiravor.notify.Alerts
import io.r_a_d.geiravor.playback.PlaybackService
import io.r_a_d.geiravor.ui.Prefs

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? GeiravorApp ?: return
        val pending = goAsync()
        AlarmScheduler.offMain {
            var posted = false
            try {
                when (intent.action) {
                    FIRE -> {
                        if (app.core.pref(Prefs.ALARM_ON) != "1") return@offMain
                        AlarmScheduler.schedule(context, app.core) {}
                        posted = true
                        app.ui.onMain {
                            try {
                                val snoozePref = app.core.pref(Prefs.SNOOZE_ON)
                                Alerts.show(
                                    context,
                                    Alerts.ID_ALARM,
                                    "r/a/dio alarm",
                                    alarmActions = true,
                                    snooze = snoozePref.isEmpty() || snoozePref == "1",
                                )
                                ContextCompat.startForegroundService(
                                    context,
                                    PlaybackService.alarmIntent(context),
                                )
                            } finally {
                                pending.finish()
                            }
                        }
                    }
                    SNOOZE -> {
                        Alerts.cancel(context, Alerts.ID_ALARM)
                        context.startService(PlaybackService.stopIntent(context))
                        AlarmScheduler.snooze(context, app.core)
                    }
                }
            } finally {
                if (!posted) pending.finish()
            }
        }
    }

    companion object {
        const val FIRE = "io.r_a_d.geiravor.ALARM_FIRE"
        const val SNOOZE = "io.r_a_d.geiravor.ALARM_SNOOZE"
    }
}
