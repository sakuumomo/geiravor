package io.r_a_d.geiravor.playback

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.r_a_d.geiravor.MainActivity
import io.r_a_d.geiravor.compat.ExactAlarms

object AlarmScheduler {
    fun scheduleDaily(
        context: Context,
        enabled: Boolean,
        hour: Int,
        minute: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!enabled) {
            cancel(context)
            return true
        }
        if (!ExactAlarms.canSchedule(context)) {
            cancel(context)
            return false
        }
        val trigger = AlarmPolicy.nextTriggerMillis(nowMillis, hour, minute)
        setClock(context, trigger)
        return true
    }

    fun scheduleSnooze(context: Context, minutes: Int, nowMillis: Long = System.currentTimeMillis()) {
        if (!ExactAlarms.canSchedule(context)) {
            return
        }
        setClock(context, nowMillis + AlarmPolicy.snoozeDelayMillis(minutes))
    }

    fun cancel(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        alarms.cancel(firePending(context))
    }

    private fun setClock(context: Context, triggerAt: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val show = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        alarms.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, show), firePending(context))
    }

    private fun firePending(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).setAction(AlarmPolicy.ACTION_FIRE)
        return PendingIntent.getBroadcast(
            context,
            1,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
