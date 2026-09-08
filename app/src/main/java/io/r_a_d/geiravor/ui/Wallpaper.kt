package io.r_a_d.geiravor.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.IntSize

data class WallpaperRoot(
    val coords: LayoutCoordinates? = null,
    val size: IntSize = IntSize.Zero,
)

val LocalWallpaperRoot = staticCompositionLocalOf { WallpaperRoot() }
