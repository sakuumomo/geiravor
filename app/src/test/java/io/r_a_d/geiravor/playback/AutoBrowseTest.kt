package io.r_a_d.geiravor.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.geiravor_core.Dj
import uniffi.geiravor_core.ListEntry
import uniffi.geiravor_core.Status

class AutoBrowseTest {
    @Test
    fun rootIsSongsAndSettings() {
        val kids = AutoBrowse.children(AutoBrowse.ROOT, null)
        assertEquals(2, kids.size)
        assertEquals("songs", kids[0].mediaId)
        assertEquals("settings", kids[1].mediaId)
        kids.forEach { assertTrue(it.mediaMetadata.isBrowsable == true) }
        kids.forEach { assertTrue(it.mediaMetadata.isPlayable != true) }
    }

    @Test
    fun queueHiddenWhenLiveDj() {
        val live = sample(isAfk = false)
        val songs = AutoBrowse.children(AutoBrowse.SONGS, live)
        assertTrue(songs.none { it.mediaId == AutoBrowse.QUEUE })
        val afk = sample(isAfk = true)
        val afkSongs = AutoBrowse.children(AutoBrowse.SONGS, afk)
        assertTrue(afkSongs.any { it.mediaId == AutoBrowse.QUEUE })
    }

    private fun sample(isAfk: Boolean) = Status(
        np = "A - B",
        artist = "A",
        title = "B",
        listeners = 1u,
        isAfk = isAfk,
        current = 1,
        startTime = 0,
        endTime = 2,
        trackId = 1,
        thread = "none",
        requesting = true,
        dj = Dj(id = 18, name = "Hanyuu-sama", image = "x.png"),
        queue = listOf(
            ListEntry(artist = "Q", title = "1", timestamp = 2, isRequest = false),
        ),
        lp = listOf(
            ListEntry(artist = "L", title = "1", timestamp = 0, isRequest = false),
        ),
        tags = emptyList(),
    )
}
