package io.r_a_d.geiravor.compat

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

/** Compat: UiModeManager.setApplicationNightMode. Remove when minSdk >= 31. */
fun setApplicationNight(context: Context, night: Boolean) {
    if (Build.VERSION.SDK_INT >= 31) {
        setApplicationNight31(context, night)
    }
}

@RequiresApi(31)
private fun setApplicationNight31(context: Context, night: Boolean) {
    val ui = context.getSystemService(UiModeManager::class.java) ?: return
    ui.setApplicationNightMode(
        if (night) UiModeManager.MODE_NIGHT_YES else UiModeManager.MODE_NIGHT_NO,
    )
}
