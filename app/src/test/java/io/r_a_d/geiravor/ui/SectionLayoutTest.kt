package io.r_a_d.geiravor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionLayoutTest {
    @Test
    fun twoPaneOnlyAtExpandedWidth() {
        assertFalse(SectionLayout.twoPane(widthDp = 411))
        assertFalse(SectionLayout.twoPane(widthDp = 839))
        assertTrue(SectionLayout.twoPane(widthDp = 840))
        assertTrue(SectionLayout.twoPane(widthDp = 1280))
    }

    @Test
    fun songsSectionsHideQueueWhenNotAfk() {
        assertEquals(
            listOf(SectionLayout.SongsSection.LastPlayed, SectionLayout.SongsSection.Queue),
            SectionLayout.songsSections(isAfkStream = true),
        )
        assertEquals(
            listOf(SectionLayout.SongsSection.LastPlayed),
            SectionLayout.songsSections(isAfkStream = false),
        )
    }

    @Test
    fun queueSelectionFallsBackWhenHidden() {
        assertEquals(
            SectionLayout.SongsSection.LastPlayed,
            SectionLayout.clampSongsSection(
                SectionLayout.SongsSection.Queue,
                isAfkStream = false,
            ),
        )
        assertEquals(
            SectionLayout.SongsSection.Queue,
            SectionLayout.clampSongsSection(
                SectionLayout.SongsSection.Queue,
                isAfkStream = true,
            ),
        )
    }

    @Test
    fun settingsSectionsAreGeneralAndAuto() {
        assertEquals(
            listOf(SectionLayout.SettingsSection.General, SectionLayout.SettingsSection.Auto),
            SectionLayout.settingsSections(),
        )
    }
}
