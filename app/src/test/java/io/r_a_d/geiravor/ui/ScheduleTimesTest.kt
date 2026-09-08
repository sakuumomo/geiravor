package io.r_a_d.geiravor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ScheduleTimesTest {
    private val mondayNoonEtToUtc = Instant.parse("2026-01-05T17:00:00Z").epochSecond

    @Test
    fun eightPmEstBecomesUtcAndNextWeekday() {
        val out = ScheduleTimes.rewrite(
            "streams at 8pm EST",
            "Monday",
            "America/New_York",
            "UTC",
            mondayNoonEtToUtc,
        )
        assertEquals("streams at 1am Tuesday", out)
    }

    @Test
    fun twentyFourHourStaysTwentyFour() {
        val out = ScheduleTimes.rewrite(
            "around 22:00",
            "Wednesday",
            "America/New_York",
            "UTC",
            mondayNoonEtToUtc,
        )
        assertTrue(out.contains("3:00") || out.contains("02:00") || out.contains("3:00"))
        assertFalse(out.contains("pm"))
    }

    @Test
    fun urlsAndDatesLeftAlone() {
        val body = "archive: https://tunes.apt-get.xyz on 2026-01-05 >>12"
        val out = ScheduleTimes.rewrite(
            body,
            "Thursday",
            "America/New_York",
            "UTC",
            mondayNoonEtToUtc,
        )
        assertTrue(out.contains("https://tunes.apt-get.xyz"))
        assertTrue(out.contains("2026-01-05"))
        assertTrue(out.contains(">>12"))
    }
}
