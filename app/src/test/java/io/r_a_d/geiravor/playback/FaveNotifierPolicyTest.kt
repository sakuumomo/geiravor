package io.r_a_d.geiravor.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaveNotifierPolicyTest {
    @Test
    fun defaultsOffAndDocumentsBattery() {
        assertFalse(FaveNotifierPolicy.ENABLED_DEFAULT)
        assertTrue(FaveNotifierPolicy.BATTERY.isNotBlank())
        assertTrue(FaveNotifierPolicy.NOTIFICATIONS_DENIED.isNotBlank())
        assertEquals("r/a/dio", FaveNotifierPolicy.TITLE)
        assertTrue(FaveNotifierPolicy.workerNeeded(djOn = true, faveOn = false))
        assertTrue(FaveNotifierPolicy.workerNeeded(djOn = false, faveOn = true))
        assertFalse(FaveNotifierPolicy.workerNeeded(djOn = false, faveOn = false))
    }

    @Test
    fun firstSampleDoesNotNotify() {
        val next = FaveNotifierPolicy.Seen(trackId = 42, np = "Hirasawa Susumu - Gats")
        assertFalse(
            FaveNotifierPolicy.shouldNotify(
                enabled = true,
                previous = null,
                next = next,
                isMember = true,
                streamDown = false,
                playing = false,
            ),
        )
    }

    @Test
    fun newFaveOnAirNotifies() {
        val previous = FaveNotifierPolicy.Seen(trackId = 1, np = "other")
        val next = FaveNotifierPolicy.Seen(trackId = 42, np = "Hirasawa Susumu - Gats")
        assertTrue(
            FaveNotifierPolicy.shouldNotify(
                enabled = true,
                previous = previous,
                next = next,
                isMember = true,
                streamDown = false,
                playing = false,
            ),
        )
        assertFalse(
            FaveNotifierPolicy.shouldNotify(
                enabled = false,
                previous = previous,
                next = next,
                isMember = true,
                streamDown = false,
                playing = false,
            ),
        )
        assertEquals("Hirasawa Susumu - Gats", FaveNotifierPolicy.body(next.np))
    }

    @Test
    fun sameTrackDoesNotRefire() {
        val seen = FaveNotifierPolicy.Seen(trackId = 42, np = "Hirasawa Susumu - Gats")
        assertFalse(
            FaveNotifierPolicy.shouldNotify(
                enabled = true,
                previous = seen,
                next = seen,
                isMember = true,
                streamDown = false,
                playing = false,
            ),
        )
        assertTrue(
            FaveNotifierPolicy.sameTrack(
                seen,
                FaveNotifierPolicy.Seen(trackId = 42, np = "Hirasawa Susumu - Gats"),
            ),
        )
        assertTrue(
            FaveNotifierPolicy.sameTrack(
                FaveNotifierPolicy.Seen(trackId = 0, np = "Live Song"),
                FaveNotifierPolicy.Seen(trackId = 0, np = "live song"),
            ),
        )
        assertFalse(
            FaveNotifierPolicy.sameTrack(
                FaveNotifierPolicy.Seen(trackId = 1, np = "A"),
                FaveNotifierPolicy.Seen(trackId = 2, np = "B"),
            ),
        )
    }

    @Test
    fun playingOrStreamDownOrUnknownDoesNotNotify() {
        val previous = FaveNotifierPolicy.Seen(trackId = 1, np = "other")
        val next = FaveNotifierPolicy.Seen(trackId = 42, np = "Hirasawa Susumu - Gats")
        assertFalse(
            FaveNotifierPolicy.shouldNotify(
                enabled = true,
                previous = previous,
                next = next,
                isMember = true,
                streamDown = false,
                playing = true,
            ),
        )
        assertFalse(
            FaveNotifierPolicy.shouldNotify(
                enabled = true,
                previous = previous,
                next = next,
                isMember = true,
                streamDown = true,
                playing = false,
            ),
        )
        assertFalse(
            FaveNotifierPolicy.shouldNotify(
                enabled = true,
                previous = previous,
                next = next,
                isMember = false,
                streamDown = false,
                playing = false,
            ),
        )
    }

    @Test
    fun fromStatusUsesCatalogIdOnlyWhenAfk() {
        val afk = sampleStatus(isAfkStream = true)
        val live = sampleStatus(isAfkStream = false).copy(np = "DJ Live - Track")
        assertEquals(
            FaveNotifierPolicy.Seen(trackId = 42, np = afk.np),
            FaveNotifierPolicy.fromStatus(afk),
        )
        assertEquals(
            FaveNotifierPolicy.Seen(trackId = 0, np = "DJ Live - Track"),
            FaveNotifierPolicy.fromStatus(live),
        )
        assertEquals("A favorite is playing", FaveNotifierPolicy.body("  "))
    }
}
