package io.r_a_d.geiravor.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.geiravor_core.Dj

class DjNotifierPolicyTest {
    @Test
    fun defaultsOffAndDocumentsBattery() {
        assertFalse(DjNotifierPolicy.ENABLED_DEFAULT)
        assertEquals(15L, DjNotifierPolicy.PERIOD_MINUTES)
        assertTrue(DjNotifierPolicy.BATTERY.isNotBlank())
        assertTrue(DjNotifierPolicy.NOTIFICATIONS_DENIED.isNotBlank())
    }

    @Test
    fun firstSampleDoesNotNotify() {
        val live = seen(isAfk = false, id = 7, name = "exci")
        assertFalse(
            DjNotifierPolicy.shouldNotify(
                enabled = true,
                previous = null,
                next = live,
                streamDown = false,
            ),
        )
    }

    @Test
    fun afkToLiveNotifies() {
        val afk = seen(isAfk = true, id = 1, name = "Hanyuu-sama")
        val live = seen(isAfk = false, id = 7, name = "exci")
        assertTrue(
            DjNotifierPolicy.shouldNotify(
                enabled = true,
                previous = afk,
                next = live,
                streamDown = false,
            ),
        )
        assertFalse(
            DjNotifierPolicy.shouldNotify(
                enabled = false,
                previous = afk,
                next = live,
                streamDown = false,
            ),
        )
    }

    @Test
    fun liveToAfkDoesNotNotify() {
        val live = seen(isAfk = false, id = 7, name = "exci")
        val afk = seen(isAfk = true, id = 1, name = "Hanyuu-sama")
        assertFalse(
            DjNotifierPolicy.shouldNotify(
                enabled = true,
                previous = live,
                next = afk,
                streamDown = false,
            ),
        )
    }

    @Test
    fun liveDjChangeNotifies() {
        val first = seen(isAfk = false, id = 7, name = "exci")
        val second = seen(isAfk = false, id = 9, name = "kipukun")
        assertTrue(
            DjNotifierPolicy.shouldNotify(
                enabled = true,
                previous = first,
                next = second,
                streamDown = false,
            ),
        )
        assertFalse(
            DjNotifierPolicy.shouldNotify(
                enabled = true,
                previous = first,
                next = first,
                streamDown = false,
            ),
        )
    }

    @Test
    fun streamDownDoesNotNotify() {
        val afk = seen(isAfk = true, id = 1, name = "Hanyuu-sama")
        val live = seen(isAfk = false, id = 7, name = "exci")
        assertFalse(
            DjNotifierPolicy.shouldNotify(
                enabled = true,
                previous = afk,
                next = live,
                streamDown = true,
            ),
        )
    }

    @Test
    fun fromStatusAndBody() {
        val status = sampleStatus(isAfkStream = false).copy(
            dj = Dj(id = 7, name = "exci", image = "x"),
        )
        assertEquals(
            DjNotifierPolicy.Seen(isAfk = false, djId = 7, djName = "exci"),
            DjNotifierPolicy.fromStatus(status),
        )
        assertEquals("exci is online", DjNotifierPolicy.body("exci"))
        assertEquals("A DJ is online", DjNotifierPolicy.body("  "))
    }

    private fun seen(isAfk: Boolean, id: Long, name: String) =
        DjNotifierPolicy.Seen(isAfk = isAfk, djId = id, djName = name)
}
