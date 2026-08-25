package io.r_a_d.geiravor.compat

import android.app.Activity
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge

/**
 * Compat: edge-to-edge. Remove when minSdk >= 35.
 */
fun Activity.applyEdgeToEdge() {
    if (this is ComponentActivity) {
        enableEdgeToEdge()
    }
}
