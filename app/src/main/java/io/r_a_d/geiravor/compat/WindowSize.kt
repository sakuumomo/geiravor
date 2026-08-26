package io.r_a_d.geiravor.compat

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.WindowManager

/**
 * Compat: display width from WindowMetrics. Remove when minSdk >= 30.
 */
fun Context.displayWidthPx(): Int {
    val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    return if (Build.VERSION.SDK_INT >= 30) {
        wm.maximumWindowMetrics.bounds.width()
    } else {
        @Suppress("DEPRECATION")
        val size = Point()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealSize(size)
        size.x
    }
}
