package io.r_a_d.geiravor.compat

import android.app.UiModeManager
import android.content.Context
import android.os.Build

/** Compat: UiModeManager.setApplicationNightMode. Remove when minSdk >= 31. */
fun setApplicationNightMode(context: Context, night: Boolean) {
    if (Build.VERSION.SDK_INT >= 31) {
        val ui = context.getSystemService(UiModeManager::class.java) ?: return
        val mode = if (night) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO
        ui.setApplicationNightMode(mode)
    }
}
