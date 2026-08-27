package io.r_a_d.geiravor.compat

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Compat: SCHEDULE_EXACT_ALARM. Remove when minSdk >= 33 and USE_EXACT_ALARM is enough.
 */
object ExactAlarms {
    fun needsRuntimeGrant(sdkInt: Int = Build.VERSION.SDK_INT): Boolean = sdkInt >= 31

    fun canSchedule(context: Context): Boolean {
        if (!needsRuntimeGrant()) {
            return true
        }
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return false
        return alarms.canScheduleExactAlarms()
    }

    fun settingsIntent(context: Context): Intent {
        val intent = if (Build.VERSION.SDK_INT >= 31) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
        return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
