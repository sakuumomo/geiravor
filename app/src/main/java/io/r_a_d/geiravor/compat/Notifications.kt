package io.r_a_d.geiravor.compat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * Compat: POST_NOTIFICATIONS. Remove when minSdk >= 33.
 */
object Notifications {
    const val DENIED =
        "Allow notifications in Settings to get DJ and fave alerts."

    val needed: Boolean
        get() = Build.VERSION.SDK_INT >= 33

    @RequiresApi(33)
    fun permission(): String = Manifest.permission.POST_NOTIFICATIONS

    fun shouldRequest(needsRuntimePermission: Boolean, alreadyGranted: Boolean): Boolean =
        needsRuntimePermission && !alreadyGranted

    fun deniedCopy(granted: Boolean): String? = if (granted) null else DENIED

    fun granted(context: Context): Boolean {
        if (!needed) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestIfNeeded(activity: android.app.Activity, requestCode: Int) {
        if (Build.VERSION.SDK_INT < 33) return
        if (granted(activity)) return
        activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), requestCode)
    }
}
