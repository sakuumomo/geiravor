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
    fun hanyuuDoesNotNotifyEvenIfMarkedLive() {
        val afk = seen(isAfk = true, id = 18, name = "Hanyuu-sama")
        val hanyuuLive = seen(isAfk = false, id = 18, name = "Hanyuu-sama")
        val live = seen(isAfk = false, id = 7, name = "exci")
        assertFalse(
            DjNotifierPolicy.shouldNotify(
                enabled = true,
                previous = afk,
                next = hanyuuLive,
                streamDown = false,
            ),
        )
        assertTrue(
            DjNotifierPolicy.shouldNotify(
                enabled = true,
                previous = live,
                next = hanyuuLive,
                streamDown = false,
            ),
        )
        assertTrue(
            DjNotifierPolicy.shouldNotify(
                enabled = true,
                previous = live,
                next = seen(isAfk = false, id = 18, name = "Hanyuu"),
                streamDown = false,
            ),
        )
        assertTrue(DjNotifierPolicy.isHanyuu("Hanyuu-sama"))
        assertTrue(DjNotifierPolicy.isAfkDj(hanyuuLive))
    }

    @Test
    fun hanyuuToLiveDjNotifies() {
        val hanyuu = seen(isAfk = false, id = 18, name = "Hanyuu-sama")
        val live = seen(isAfk = false, id = 7, name = "exci")
        assertTrue(
            DjNotifierPolicy.shouldNotify(
                enabled = true,
                previous = hanyuu,
                next = live,
                streamDown = false,
            ),
        )
    }

    @Test
    fun liveToAfkNotifiesHanyuuBack() {
        val live = seen(isAfk = false, id = 7, name = "exci")
        val afk = seen(isAfk = true, id = 1, name = "Hanyuu-sama")
        assertTrue(
            DjNotifierPolicy.shouldNotify(
                enabled = true,
                previous = live,
                next = afk,
                streamDown = false,
            ),
        )
        assertEquals("Hanyuu-sama is back", DjNotifierPolicy.body(afk))
        assertEquals(
            "Hanyuu is back",
            DjNotifierPolicy.body(seen(isAfk = false, id = 18, name = "Hanyuu")),
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
            DjNotifierPolicy.Seen(isAfk = false, djId = 7, djName = "exci", djImage = "x"),
            DjNotifierPolicy.fromStatus(status),
        )
        assertEquals("r/a/dio", DjNotifierPolicy.TITLE)
        assertEquals("exci is LIVE", DjNotifierPolicy.body(DjNotifierPolicy.fromStatus(status)))
        assertEquals(
            "A DJ is LIVE",
            DjNotifierPolicy.body(seen(isAfk = false, id = 7, name = "  ")),
        )
        assertEquals(
            "Hanyuu-sama is back",
            DjNotifierPolicy.body(seen(isAfk = true, id = 1, name = "")),
        )
        assertFalse(DjNotifierPolicy.isHanyuu("exci"))
        assertEquals("x", DjNotifierPolicy.fromStatus(status).djImage)
        assertEquals(
            "https://r-a-d.io/api/dj-image/59-abc.gif",
            DjNotifierPolicy.artworkUrl("59-abc.gif"),
        )
        assertEquals(null, DjNotifierPolicy.artworkUrl(""))
        assertEquals(null, DjNotifierPolicy.artworkUrl("  "))
    }

    private fun seen(isAfk: Boolean, id: Long, name: String) =
        DjNotifierPolicy.Seen(isAfk = isAfk, djId = id, djName = name)
}
