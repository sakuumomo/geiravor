package io.r_a_d.geiravor.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.geiravor_core.ListEntry

class AutoBrowseTest {
    @Test
    fun browseRootIsSongsAndSettingsTabs() {
        val root = AutoBrowse.rootChildren()
        assertEquals(listOf(AutoBrowse.SONGS, AutoBrowse.SETTINGS), root.map { it.id })
        assertTrue(root.all { it.browsable && !it.playable })
        assertTrue(root.none { it.id == AutoBrowse.NOW_PLAYING })
        assertTrue(root.none { it.id == AutoBrowse.QUEUE })
        assertTrue(root.none { it.title.equals("News", ignoreCase = true) })
        assertTrue(root.none { it.title.equals("Board", ignoreCase = true) })
        assertTrue(root.none { it.title.equals("Schedule", ignoreCase = true) })
        assertTrue(root.none { it.title.equals("Staff", ignoreCase = true) })
        assertTrue(root.none { it.id.contains("news", ignoreCase = true) })
    }

    @Test
    fun songsFolderIsLastPlayedAndQueueOnly() {
        val afk = AutoBrowse.children(AutoBrowse.SONGS, sampleStatus(isAfkStream = true))
        assertEquals(listOf(AutoBrowse.LAST_PLAYED, AutoBrowse.QUEUE), afk.map { it.id })
        assertTrue(afk.all { it.browsable && !it.playable })
        val liveDj = AutoBrowse.children(AutoBrowse.SONGS, sampleStatus(isAfkStream = false))
        assertEquals(listOf(AutoBrowse.LAST_PLAYED), liveDj.map { it.id })
    }

    @Test
    fun coldStartUsesHydratedAfkWhenLiveSnapshotIsStillEmpty() {
        assertEquals(null, AutoBrowse.statusForBrowse(live = null, hydrated = null))
        val paint = sampleStatus(isAfkStream = true)
        val used = AutoBrowse.statusForBrowse(live = null, hydrated = paint)
        assertEquals(paint, used)
        assertEquals(
            listOf(AutoBrowse.LAST_PLAYED, AutoBrowse.QUEUE),
            AutoBrowse.children(AutoBrowse.SONGS, used).map { it.id },
        )
        val live = sampleStatus(isAfkStream = false)
        assertEquals(live, AutoBrowse.statusForBrowse(live = live, hydrated = paint))
        assertEquals(
            listOf(AutoBrowse.LAST_PLAYED),
            AutoBrowse.children(AutoBrowse.SONGS, null).map { it.id },
        )
    }

    @Test
    fun songRowsDoNotAllowPlayback() {
        assertFalse(AutoBrowse.allowsPlayback(mediaId = "lp:0", uri = null))
        assertFalse(AutoBrowse.allowsPlayback(mediaId = AutoBrowse.LAST_PLAYED, uri = null))
        assertFalse(AutoBrowse.allowsPlayback(mediaId = AutoBrowse.QUEUE, uri = null))
        assertFalse(AutoBrowse.allowsPlayback(mediaId = AutoBrowse.SONGS, uri = null))
        assertFalse(AutoBrowse.allowsPlayback(mediaId = AutoBrowse.SETTINGS, uri = null))
        assertFalse(AutoBrowse.allowsPlayback(mediaId = AutoBrowse.SETTING_VEHICLE, uri = LivePlaybackPolicy.STREAM_URL))
        assertTrue(
            AutoBrowse.allowsPlayback(
                mediaId = AutoBrowse.NOW_PLAYING,
                uri = LivePlaybackPolicy.STREAM_URL,
            ),
        )
        assertFalse(AutoBrowse.allowsPlayback(mediaId = AutoBrowse.ROOT, uri = null))
        assertFalse(
            AutoBrowse.allowsPlayback(
                mediaId = AutoBrowse.ROOT,
                uri = LivePlaybackPolicy.STREAM_URL,
            ),
        )
    }

    @Test
    fun lastPlayedChildrenAreDisplayOnly() {
        val children = AutoBrowse.children(
            AutoBrowse.LAST_PLAYED,
            sampleStatus(lastPlayed = listOf(entry("Hirasawa Susumu - Gats"))),
        )
        assertEquals(1, children.size)
        assertEquals("Hirasawa Susumu - Gats", children[0].title)
        assertFalse(children[0].playable)
        assertFalse(children[0].browsable)
    }

    @Test
    fun browseChildrenEqualOnlyWhenRowsUnchanged() {
        val first = sampleStatus(
            lastPlayed = listOf(entry("A"), entry("B")),
            queue = listOf(entry("C")),
        )
        val same = sampleStatus(
            lastPlayed = listOf(entry("A"), entry("B")),
            queue = listOf(entry("C")),
        )
        val moved = sampleStatus(
            lastPlayed = listOf(entry("B"), entry("D")),
            queue = listOf(entry("A")),
        )
        val off = AutoSettingsSnapshot(vehicleOn = false, plugOn = true, versionName = "0.3.0")
        val on = AutoSettingsSnapshot(vehicleOn = true, plugOn = true, versionName = "0.3.0")
        assertEquals(
            AutoBrowse.children(AutoBrowse.LAST_PLAYED, first),
            AutoBrowse.children(AutoBrowse.LAST_PLAYED, same),
        )
        assertNotEquals(
            AutoBrowse.children(AutoBrowse.LAST_PLAYED, first),
            AutoBrowse.children(AutoBrowse.LAST_PLAYED, moved),
        )
        assertEquals(
            AutoBrowse.children(AutoBrowse.QUEUE, first),
            AutoBrowse.children(AutoBrowse.QUEUE, same),
        )
        assertNotEquals(
            AutoBrowse.children(AutoBrowse.QUEUE, first),
            AutoBrowse.children(AutoBrowse.QUEUE, moved),
        )
        assertEquals(
            AutoBrowse.children(AutoBrowse.SETTINGS, first, off),
            AutoBrowse.children(AutoBrowse.SETTINGS, moved, off),
        )
        assertNotEquals(
            AutoBrowse.children(AutoBrowse.SETTINGS, first, off),
            AutoBrowse.children(AutoBrowse.SETTINGS, first, on),
        )
    }

    @Test
    fun queueChildrenHiddenWhenNotAfkEvenIfApiSentRows() {
        val liveDj = sampleStatus(
            isAfkStream = false,
            queue = listOf(entry("Should Be Hidden - Track")),
        )
        assertTrue(AutoBrowse.children(AutoBrowse.SONGS, liveDj).none { it.id == AutoBrowse.QUEUE })
        assertTrue(AutoBrowse.children(AutoBrowse.QUEUE, liveDj).isEmpty())
        assertTrue(AutoBrowse.rootChildren().none { it.id == AutoBrowse.QUEUE })
    }

    @Test
    fun settingsTogglesArePlayableButNeverTheLiveStream() {
        val off = AutoBrowse.children(
            AutoBrowse.SETTINGS,
            sampleStatus(),
            AutoSettingsSnapshot(vehicleOn = false, plugOn = true, versionName = "0.3.0"),
        )
        assertEquals(
            listOf(AutoBrowse.SETTING_VEHICLE, AutoBrowse.SETTING_PLUG, AutoBrowse.SETTING_ABOUT),
            off.map { it.id },
        )
        assertEquals("Off", off[0].subtitle)
        assertEquals("On", off[1].subtitle)
        assertTrue(off[0].playable && !off[0].browsable)
        assertTrue(off[1].playable && !off[1].browsable)
        assertFalse(off[2].playable)
        assertFalse(off[2].browsable)
        assertTrue(AutoBrowse.isSettingsToggle(AutoBrowse.SETTING_VEHICLE))
        assertTrue(AutoBrowse.isSettingsToggle(AutoBrowse.SETTING_PLUG))
        assertFalse(AutoBrowse.isSettingsToggle(AutoBrowse.SETTING_ABOUT))
        assertFalse(
            AutoBrowse.allowsPlayback(
                mediaId = AutoBrowse.SETTING_VEHICLE,
                uri = "content://io.r_a_d.geiravor/settings/vehicle",
            ),
        )
        assertTrue(
            AutoBrowse.children(
                AutoBrowse.SETTING_VEHICLE,
                sampleStatus(),
                AutoSettingsSnapshot(vehicleOn = true, plugOn = false),
            ).isEmpty(),
        )
    }

    @Test
    fun skipReplacingLiveStreamOnlyWhileActuallyPlaying() {
        assertTrue(
            AutoBrowse.skipRedundantLiveSet(
                currentMediaId = AutoBrowse.NOW_PLAYING,
                currentUri = LivePlaybackPolicy.STREAM_URL,
                incoming = listOf(AutoBrowse.NOW_PLAYING to LivePlaybackPolicy.STREAM_URL),
                activelyPlaying = true,
            ),
        )
        assertTrue(
            AutoBrowse.skipRedundantLiveSet(
                currentMediaId = AutoBrowse.NOW_PLAYING,
                currentUri = LivePlaybackPolicy.STREAM_URL,
                incoming = listOf(AutoBrowse.SETTING_VEHICLE to "content://io.r_a_d.geiravor/settings/vehicle"),
                activelyPlaying = true,
            ),
        )
        assertFalse(
            AutoBrowse.skipRedundantLiveSet(
                currentMediaId = AutoBrowse.NOW_PLAYING,
                currentUri = LivePlaybackPolicy.STREAM_URL,
                incoming = listOf(AutoBrowse.NOW_PLAYING to LivePlaybackPolicy.STREAM_URL),
                activelyPlaying = false,
            ),
        )
        assertTrue(
            AutoBrowse.skipRedundantLiveSet(
                currentMediaId = AutoBrowse.NOW_PLAYING,
                currentUri = LivePlaybackPolicy.STREAM_URL,
                incoming = listOf(AutoBrowse.SETTING_PLUG to "content://io.r_a_d.geiravor/settings/plug"),
                activelyPlaying = false,
            ),
        )
        assertFalse(
            AutoBrowse.skipRedundantLiveSet(
                currentMediaId = null,
                currentUri = null,
                incoming = listOf(AutoBrowse.NOW_PLAYING to LivePlaybackPolicy.STREAM_URL),
                activelyPlaying = true,
            ),
        )
        assertFalse(
            AutoBrowse.skipRedundantLiveSet(
                currentMediaId = AutoBrowse.NOW_PLAYING,
                currentUri = LivePlaybackPolicy.STREAM_URL,
                incoming = listOf(AutoBrowse.NOW_PLAYING to LivePlaybackPolicy.STREAM_URL),
                activelyPlaying = true,
                metadataOnly = true,
            ),
        )
    }

    @Test
    fun referenceTapsAreNotTheLiveStream() {
        assertTrue(AutoBrowse.isReferenceTap("lp:0"))
        assertTrue(AutoBrowse.isReferenceTap(AutoBrowse.SETTING_ABOUT))
        assertFalse(AutoBrowse.isReferenceTap(AutoBrowse.SETTING_VEHICLE))
        assertFalse(AutoBrowse.isReferenceTap(AutoBrowse.NOW_PLAYING))
    }

    @Test
    fun liveStreamIdsAreThePlayableNowPlayingItem() {
        assertTrue(AutoBrowse.isLiveStream(AutoBrowse.NOW_PLAYING))
        assertFalse(AutoBrowse.isLiveStream(AutoBrowse.ROOT))
        assertFalse(AutoBrowse.isLiveStream(AutoBrowse.LAST_PLAYED))
        assertFalse(AutoBrowse.isLiveStream("lp:0"))
    }

    @Test
    fun lookupFindsNowPlayingEvenThoughItIsNotInTheBrowseTree() {
        val np = AutoBrowse.lookup(AutoBrowse.NOW_PLAYING, sampleStatus(isAfkStream = true))
        assertEquals(AutoBrowse.NOW_PLAYING, np?.id)
        assertTrue(np?.playable == true)
        assertFalse(np?.browsable == true)
        assertTrue(AutoBrowse.children(AutoBrowse.ROOT, sampleStatus(isAfkStream = true)).none { it.id == AutoBrowse.NOW_PLAYING })
        assertEquals(
            AutoBrowse.SETTING_VEHICLE,
            AutoBrowse.lookup(AutoBrowse.SETTING_VEHICLE, null)?.id,
        )
        assertEquals(null, AutoBrowse.lookup("missing", null))
    }
}

private fun entry(meta: String) = ListEntry(
    meta = meta,
    artist = "",
    title = meta,
    timestamp = 0,
    isRequest = false,
)
