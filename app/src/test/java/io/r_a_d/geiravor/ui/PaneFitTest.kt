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

    @Test
    fun innerSectionsUseBorderAndPad() {
        assertEquals(8, InnerSection.GAP_DP)
        assertEquals(12, InnerSection.PAD_DP)
        assertEquals(8, InnerSection.ROW_PAD_DP)
        assertEquals(1, InnerSection.BORDER_DP)
        assertEquals(68, InnerSection.SONG_ROW_DP)
        assertEquals(88, InnerSection.NEWS_ROW_DP)
        assertEquals(20, InnerSection.ARTICLE_COMMENT_GAP_DP)
    }
}
