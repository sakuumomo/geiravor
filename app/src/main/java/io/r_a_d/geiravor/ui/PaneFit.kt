package io.r_a_d.geiravor.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration

/** Portrait or two-pane is normal viewing; rotation keeps that row count. */
fun lockedPaneFit(measured: UInt, portrait: Boolean, twoPane: Boolean, locked: UInt): UInt {
    val n = measured.coerceAtLeast(1u)
    return if (twoPane || portrait) {
        n
    } else if (locked == 0u) {
        n
    } else {
        locked
    }
}

@Composable
fun lockPaneFit(measured: UInt, locked: UInt, onLock: (UInt) -> Unit): UInt {
    val cfg = LocalConfiguration.current
    val portrait = cfg.orientation == Configuration.ORIENTATION_PORTRAIT
    val twoPane = cfg.screenWidthDp >= 840 && cfg.smallestScreenWidthDp >= 600
    val fit = lockedPaneFit(measured, portrait, twoPane, locked)
    if (fit != locked) onLock(fit)
    return fit
}
