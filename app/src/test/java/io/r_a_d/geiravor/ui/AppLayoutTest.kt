package io.r_a_d.geiravor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLayoutTest {
    @Test
    fun twoPaneStartsAtExpandedWidth() {
        assertFalse(AppLayout.twoPane(411))
        assertFalse(AppLayout.twoPane(839))
        assertTrue(AppLayout.twoPane(840))
        assertTrue(AppLayout.twoPane(1280))
    }

    @Test
    fun twoPaneOmitsNowPlayingTab() {
        assertEquals(AppTab.entries.toList(), AppLayout.tabs(twoPane = false))
        assertEquals(listOf(AppTab.Songs, AppTab.Settings), AppLayout.tabs(twoPane = true))
    }

    @Test
    fun twoPaneUsesSongsWhenNowPlayingWasSelected() {
        assertEquals(AppTab.NowPlaying, AppLayout.clampTab(AppTab.NowPlaying, twoPane = false))
        assertEquals(AppTab.Songs, AppLayout.clampTab(AppTab.NowPlaying, twoPane = true))
        assertEquals(AppTab.Settings, AppLayout.clampTab(AppTab.Settings, twoPane = true))
    }
}
