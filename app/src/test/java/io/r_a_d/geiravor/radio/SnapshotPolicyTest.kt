package io.r_a_d.geiravor.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import uniffi.geiravor_core.Dj
import uniffi.geiravor_core.ListEntry
import uniffi.geiravor_core.Status

class SnapshotPolicyTest {
    @Test
    fun roundTripKeepsNpDjPreviousAndThread() {
        val status = Status(
            np = "Some Live DJ - Mix",
            artist = "Some Live DJ",
            title = "Mix",
            listeners = 12,
            isAfkStream = false,
            requesting = false,
            current = 1000,
            startTime = 900,
            endTime = 0,
            trackId = 0,
            thread = "https://boards.4chan.org/a/thread/1",
            dj = Dj(id = 7, name = "Ojiisan", image = "7-abc.png"),
            queue = listOf(
                ListEntry("Should Hide - Track", "Should Hide", "Track", 1100, false),
            ),
            lastPlayed = listOf(
                ListEntry("Previous - Song", "Previous", "Song", 800, false),
            ),
            tags = listOf("live"),
        )
        val restored = SnapshotPolicy.toStatus(
            requireNotNull(SnapshotPolicy.decode(SnapshotPolicy.encode(SnapshotPolicy.fromStatus(status)))),
        )
        assertEquals("Some Live DJ - Mix", restored.np)
        assertEquals("Ojiisan", restored.dj.name)
        assertEquals("7-abc.png", restored.dj.image)
        assertEquals("https://boards.4chan.org/a/thread/1", restored.thread)
        assertFalse(restored.isAfkStream)
        assertEquals("Previous - Song", restored.lastPlayed[0].meta)
        assertEquals("Should Hide - Track", restored.queue[0].meta)
    }

    @Test
    fun hydrateDoesNotRestoreStreamDown() {
        assertFalse(SnapshotPolicy.RESTORE_STREAM_DOWN)
    }

    @Test
    fun emptyBlobIsIgnored() {
        assertNull(SnapshotPolicy.decode(""))
        assertNull(SnapshotPolicy.decode("   "))
    }
}
