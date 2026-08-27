package io.r_a_d.geiravor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionLayoutTest {
    @Test
    fun songsSectionsHideQueueWhenNotAfk() {
        assertEquals(
            listOf(
                SectionLayout.SongsSection.LastPlayed,
                SectionLayout.SongsSection.Queue,
                SectionLayout.SongsSection.Request,
                SectionLayout.SongsSection.Favorites,
            ),
            SectionLayout.songsSections(isAfkStream = true),
        )
        assertEquals(
            listOf(
                SectionLayout.SongsSection.LastPlayed,
                SectionLayout.SongsSection.Request,
                SectionLayout.SongsSection.Favorites,
            ),
            SectionLayout.songsSections(isAfkStream = false),
        )
    }

    @Test
    fun songsSectionLabelsAreNotFavesOrLp() {
        assertEquals("Last Played", SectionLayout.SongsSection.LastPlayed.label)
        assertEquals("Favorites", SectionLayout.SongsSection.Favorites.label)
        assertTrue(
            SectionLayout.SongsSection.entries.none { section ->
                section.label == "Faves" || section.label == "LP"
            },
        )
    }

    @Test
    fun favoritesStaysSelectedWhenQueueIsHidden() {
        assertEquals(
            SectionLayout.SongsSection.Favorites,
            SectionLayout.clampSongsSection(
                SectionLayout.SongsSection.Favorites,
                isAfkStream = false,
            ),
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
    fun settingsSectionsAreGeneralAutoConnectionAlerts() {
        assertEquals(
            listOf(
                SectionLayout.SettingsSection.General,
                SectionLayout.SettingsSection.Auto,
                SectionLayout.SettingsSection.Connection,
                SectionLayout.SettingsSection.Alerts,
            ),
            SectionLayout.settingsSections(),
        )
    }
}
