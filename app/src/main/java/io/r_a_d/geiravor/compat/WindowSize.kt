package io.r_a_d.geiravor.compat

import android.content.Context
import android.os.Build
import android.view.WindowManager

/**
 * Compat: display width from WindowMetrics. Remove when minSdk >= 30.
 */
fun displayWidthPx(context: Context): Int {
    val wm = context.getSystemService(WindowManager::class.java) ?: return 0
    return if (Build.VERSION.SDK_INT >= 30) {
        wm.currentWindowMetrics.bounds.width()
    } else {
        @Suppress("DEPRECATION")
        val d = android.graphics.Point()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealSize(d)
        d.x
    }
}

/** Two-pane: width ≥ 840dp and smallest width ≥ 600dp. */
fun isTwoPane(context: Context): Boolean {
    val metrics = context.resources.displayMetrics
    val widthDp = metrics.widthPixels / metrics.density
    val smallest = context.resources.configuration.smallestScreenWidthDp
    return widthDp >= 840f && smallest >= 600
}
