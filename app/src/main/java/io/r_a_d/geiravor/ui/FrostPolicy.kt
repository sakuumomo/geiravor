package io.r_a_d.geiravor.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * Glass frost must copy the **laid-out** page wallpaper (same crop as the background),
 * not the asset's intrinsic size. Holiday jpgs are 1920×1080; a portrait pane using
 * that intrinsic is only ~half the screen tall and looks like a second wallpaper.
 */
object FrostPolicy {
    fun canCopy(laidOut: IntSize): Boolean = laidOut.width > 0 && laidOut.height > 0

    fun copySize(laidOut: IntSize): IntSize = laidOut

    fun offsetPx(wallpaperOrigin: Offset, paneOrigin: Offset): IntOffset = IntOffset(
        (wallpaperOrigin.x - paneOrigin.x).roundToInt(),
        (wallpaperOrigin.y - paneOrigin.y).roundToInt(),
    )
}

data class WallpaperLayout(
    val size: IntSize = IntSize.Zero,
    val position: Offset = Offset.Zero,
)
