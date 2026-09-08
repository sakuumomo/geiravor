package io.r_a_d.geiravor.compat

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Compat: SCHEDULE_EXACT_ALARM. Keep a named helper after minSdk catch-up.
 */
object ExactAlarms {
    fun needsRuntimeGrant(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= 31

    fun canSchedule(context: Context): Boolean {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= 31) {
            alarms.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun settingsIntent(): Intent =
        if (Build.VERSION.SDK_INT >= 31) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        } else {
            Intent(Settings.ACTION_SETTINGS)
        }
}
