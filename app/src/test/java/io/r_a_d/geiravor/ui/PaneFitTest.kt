package io.r_a_d.geiravor.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PaneFitTest {
    @Test
    fun portraitTakesMeasured() {
        assertEquals(8u, lockedPaneFit(8u, portrait = true, twoPane = false, locked = 0u))
    }

    @Test
    fun landscapeKeepsPortraitLock() {
        assertEquals(8u, lockedPaneFit(5u, portrait = false, twoPane = false, locked = 8u))
    }

    @Test
    fun firstLandscapeUsesMeasured() {
        assertEquals(5u, lockedPaneFit(5u, portrait = false, twoPane = false, locked = 0u))
    }

    @Test
    fun twoPaneRemeasures() {
        assertEquals(6u, lockedPaneFit(6u, portrait = false, twoPane = true, locked = 8u))
    }

    @Test
    fun hugSheetIsNotFillMaxSize() {
        val hug = paneSheetModifier(hug = true)
        val fill = paneSheetModifier(hug = false)
        assertEquals(false, hug == fill)
    }
}
