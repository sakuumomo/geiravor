package io.r_a_d.geiravor.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrostTest {
    @Test
    fun emptyLaidOutIsNotCopied() {
        assertFalse(Frost.canCopy(IntSize.Zero))
        assertTrue(Frost.canCopy(IntSize(1080, 1920)))
    }

    @Test
    fun offsetIsWallpaperMinusPane() {
        val wp = Offset(0f, 80f)
        val pane = Offset(16f, 180f)
        val o = Frost.offsetPx(wp, pane)
        assertEquals(-16, o.x)
        assertEquals(-100, o.y)
    }
}
