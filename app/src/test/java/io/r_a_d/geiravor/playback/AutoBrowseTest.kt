package io.r_a_d.geiravor.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.geiravor_core.Dj
import uniffi.geiravor_core.ListEntry
import uniffi.geiravor_core.Status

class AutoBrowseTest {
    private val flags = AutoBrowse.Flags(vehicle = false, plug = true, version = "0.3.0")

    @Test
    fun libraryRootIsBrowsableNotPlayable() {
        val root = AutoBrowse.rootItem()
        assertEquals(AutoBrowse.ROOT, root.mediaId)
        assertTrue(root.mediaMetadata.isBrowsable == true)
        assertTrue(root.mediaMetadata.isPlayable != true)
        assertEquals("r/a/dio", root.mediaMetadata.title.toString())
    }

    @Test
    fun rootIsSongsAndSettings() {
        val kids = AutoBrowse.children(AutoBrowse.ROOT, null, flags)
        assertEquals(2, kids.size)
        assertEquals("songs", kids[0].mediaId)
        assertEquals("settings", kids[1].mediaId)
        kids.forEach { assertTrue(it.mediaMetadata.isBrowsable == true) }
        kids.forEach { assertTrue(it.mediaMetadata.isPlayable != true) }
    }

    @Test
    fun queueHiddenWhenLiveDj() {
        val live = sample(isAfk = false)
        val songs = AutoBrowse.children(AutoBrowse.SONGS, live, flags)
        assertTrue(songs.none { it.mediaId == AutoBrowse.QUEUE })
        val afk = sample(isAfk = true)
        val afkSongs = AutoBrowse.children(AutoBrowse.SONGS, afk, flags)
        assertTrue(afkSongs.any { it.mediaId == AutoBrowse.QUEUE })
    }

    @Test
    fun coldAutoShowsQueueUntilSnapshot() {
        assertTrue(AutoBrowse.shouldShowQueue(null))
        val songs = AutoBrowse.children(AutoBrowse.SONGS, null, flags)
        assertTrue(songs.any { it.mediaId == AutoBrowse.QUEUE })
    }

    @Test
    fun settingsAreVehiclePlugAbout() {
        val kids = AutoBrowse.children(AutoBrowse.SETTINGS, null, flags)
        assertEquals(3, kids.size)
        assertEquals(AutoBrowse.VEHICLE, kids[0].mediaId)
        assertEquals(AutoBrowse.PLUG, kids[1].mediaId)
        assertEquals(AutoBrowse.ABOUT, kids[2].mediaId)
        assertEquals("On", kids[1].mediaMetadata.subtitle.toString())
        assertEquals("Off", kids[0].mediaMetadata.subtitle.toString())
        assertTrue(kids[0].mediaMetadata.isPlayable == true)
        assertTrue(kids[0].mediaMetadata.isBrowsable != true)
        assertTrue(kids[1].mediaMetadata.isPlayable == true)
        assertTrue(kids[2].mediaMetadata.isPlayable != true)
        assertTrue(kids[2].mediaMetadata.isBrowsable != true)
        assertEquals("Geiravor 0.3.0", kids[2].mediaMetadata.subtitle.toString())
        assertTrue(kids.none { it.mediaId == AutoBrowse.SETTINGS })
    }

    @Test
    fun aboutHasNoSelfChild() {
        val kids = AutoBrowse.children(AutoBrowse.ABOUT, null, flags)
        assertTrue(kids.isEmpty())
    }

    @Test
    fun songsSignatureIgnoresUnrelatedFields() {
        val a = sample(isAfk = true)
        val b = sample(isAfk = true).let { it }
        assertEquals(AutoBrowse.songsSignature(a), AutoBrowse.songsSignature(b))
        val live = sample(isAfk = false)
        assertNotEquals(AutoBrowse.songsSignature(a), AutoBrowse.songsSignature(live))
    }

    @Test
    fun getItemReturnsLiveAndSettings() {
        assertTrue(AutoBrowse.isLiveId(LivePlaybackPolicy.STREAM_URL))
        assertTrue(
            AutoBrowse.isLiveStream(LivePlaybackPolicy.STREAM_URL, LivePlaybackPolicy.STREAM_URL),
        )
        assertFalse(AutoBrowse.isLiveStream(AutoBrowse.SONGS, null))
        assertFalse(AutoBrowse.isLiveId(AutoBrowse.ROOT))
        assertTrue(AutoBrowse.isSettingsToggle(AutoBrowse.VEHICLE))
        assertTrue(AutoBrowse.isFunctionItem(AutoBrowse.ABOUT))
        assertFalse(AutoBrowse.isFunctionItem(AutoBrowse.SONGS))
        assertFalse(AutoBrowse.isSettingsToggle(AutoBrowse.ABOUT))
        val about = AutoBrowse.item(AutoBrowse.ABOUT, null, flags)
        assertEquals("About", about?.mediaMetadata?.title.toString())
        assertNull(AutoBrowse.item(AutoBrowse.QUEUE, sample(isAfk = false), flags))
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
