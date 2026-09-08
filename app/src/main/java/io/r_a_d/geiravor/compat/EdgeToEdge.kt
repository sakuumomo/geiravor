package io.r_a_d.geiravor.compat

import android.app.Activity
import android.os.Build
import androidx.core.view.WindowCompat

/**
 * Compat: edge-to-edge. Remove when minSdk >= 35.
 */
fun Activity.enableEdgeToEdge() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    if (Build.VERSION.SDK_INT >= 29) {
        window.isNavigationBarContrastEnforced = false
    }
}
