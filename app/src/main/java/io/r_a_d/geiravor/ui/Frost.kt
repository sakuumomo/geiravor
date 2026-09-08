package io.r_a_d.geiravor.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * Glass frost copies the **laid-out** page wallpaper (same crop as the
 * background), not the jpg’s intrinsic 1920×1080.
 */
object Frost {
    fun canCopy(laidOut: IntSize): Boolean = laidOut.width > 0 && laidOut.height > 0

    fun offsetPx(wallpaperOrigin: Offset, paneOrigin: Offset): IntOffset =
        IntOffset(
            (wallpaperOrigin.x - paneOrigin.x).roundToInt(),
            (wallpaperOrigin.y - paneOrigin.y).roundToInt(),
        )
}

data class WallpaperLayout(
    val size: IntSize = IntSize.Zero,
    val position: Offset = Offset.Zero,
)
