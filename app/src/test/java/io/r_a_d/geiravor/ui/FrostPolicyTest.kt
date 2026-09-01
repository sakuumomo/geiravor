package io.r_a_d.geiravor.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrostPolicyTest {
    @Test
    fun copyMatchesLaidOutWallpaperNotIntrinsicLandscape() {
        val intrinsic = IntSize(1920, 1080)
        val laidOut = IntSize(1080, 1920)
        assertEquals(laidOut, FrostPolicy.copySize(laidOut))
        assertNotEquals(intrinsic, FrostPolicy.copySize(laidOut))
        assertFalse(FrostPolicy.canCopy(IntSize.Zero))
        assertTrue(FrostPolicy.canCopy(laidOut))
        assertEquals(
            IntOffset(0, -200),
            FrostPolicy.offsetPx(Offset.Zero, Offset(0f, 200f)),
        )
    }
}
