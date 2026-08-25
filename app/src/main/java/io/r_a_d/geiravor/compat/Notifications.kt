package io.r_a_d.geiravor.compat

import android.Manifest
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Compat: POST_NOTIFICATIONS. Remove when minSdk >= 33.
 */
object Notifications {
    val needed: Boolean
        get() = Build.VERSION.SDK_INT >= 33

    @RequiresApi(33)
    fun permission(): String = Manifest.permission.POST_NOTIFICATIONS
}
