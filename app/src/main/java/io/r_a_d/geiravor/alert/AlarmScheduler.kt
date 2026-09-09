package io.r_a_d.geiravor.alert

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.r_a_d.geiravor.compat.ExactAlarms
import io.r_a_d.geiravor.ui.Prefs
import uniffi.geiravor_core.RadioCore
import java.util.Calendar
import java.util.concurrent.Executors

object AlarmScheduler {
    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "geiravor-alarm").apply { isDaemon = true }
    }

    fun offMain(block: () -> Unit) {
        io.execute { runCatching(block) }
    }
    fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            10,
            Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.FIRE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pending(context))
    }

    fun schedule(context: Context, core: RadioCore, fail: (String) -> Unit) {
        val on = core.pref(Prefs.ALARM_ON) == "1"
        if (!on) {
            cancel(context)
            return
        }
        if (!ExactAlarms.canSchedule(context)) {
            fail("Allow exact alarms in Settings to ring.")
            return
        }
        val hour = core.pref(Prefs.ALARM_HOUR).toIntOrNull()?.coerceIn(0, 23) ?: 7
        val minute = core.pref(Prefs.ALARM_MINUTE).toIntOrNull()?.coerceIn(0, 59) ?: 0
        val at = nextFireMillis(System.currentTimeMillis(), hour, minute)
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context))
    }

    fun nextFireMillis(nowMillis: Long, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= nowMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return cal.timeInMillis
    }

    fun snooze(context: Context, core: RadioCore) {
        if (core.pref(Prefs.SNOOZE_ON) != "1") return
        val hours = core.pref(Prefs.SNOOZE_HOURS).toUIntOrNull() ?: 0u
        val minutes = core.pref(Prefs.SNOOZE_MINUTES).toUIntOrNull() ?: 10u
        val mins = uniffi.geiravor_core.alertDurationMinutes(hours, minutes).toInt()
        val at = System.currentTimeMillis() + mins * 60_000L
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context))
    }
}
