package io.r_a_d.geiravor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class SchedulePolicyTest {
    private val pacific = ZoneId.of("America/Los_Angeles")
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val tuesday = LocalDate.of(2026, 1, 13)

    @Test
    fun weekdaysAreMondayFirst() {
        assertEquals(
            listOf(
                "Monday", "Tuesday", "Wednesday", "Thursday",
                "Friday", "Saturday", "Sunday",
            ),
            SchedulePolicy.WEEKDAYS,
        )
        assertEquals(ZoneId.of("America/New_York"), SchedulePolicy.SOURCE_ZONE)
        assertFalse(SchedulePolicy.CONVERT_DEFAULT)
    }

    @Test
    fun todayUsesDeviceWeekday() {
        assertTrue(SchedulePolicy.isToday("Tuesday", DayOfWeek.TUESDAY))
        assertFalse(SchedulePolicy.isToday("Monday", DayOfWeek.TUESDAY))
    }

    @Test
    fun offLeavesBodyAlone() {
        val body = "streams at 8pm"
        assertEquals(
            body,
            SchedulePolicy.displayBody(body, "Monday", convert = false, zone = pacific, nowEt = tuesday),
        )
    }

    @Test
    fun convertsUnambiguousClocksInPlace() {
        assertEquals(
            "streams at 5pm",
            SchedulePolicy.displayBody(
                "streams at 8pm",
                "Tuesday",
                convert = true,
                zone = pacific,
                nowEt = tuesday,
            ),
        )
        assertEquals(
            "Sundays: 5 pm | Tuesday: 8:59 pm - Tuesday's gone with the wind.",
            SchedulePolicy.displayBody(
                "Sundays: 8 pm EST | Tuesday: 11:59 pm EST - Tuesday's gone with the wind.",
                "Tuesday",
                convert = true,
                zone = pacific,
                nowEt = tuesday,
            ),
        )
        assertEquals(
            "Should be around 19:00.",
            SchedulePolicy.displayBody(
                "Should be around 22:00.",
                "Wednesday",
                convert = true,
                zone = pacific,
                nowEt = tuesday,
            ),
        )
        assertEquals(
            "Usually starts around 11AM.",
            SchedulePolicy.displayBody(
                "Usually starts around 2PM.",
                "Thursday",
                convert = true,
                zone = pacific,
                nowEt = tuesday,
            ),
        )
    }

    @Test
    fun dayCrossingAppendsLocalWeekday() {
        assertEquals(
            "streams at 10am Wednesday",
            SchedulePolicy.displayBody(
                "streams at 8pm",
                "Tuesday",
                convert = true,
                zone = tokyo,
                nowEt = tuesday,
            ),
        )
    }

    @Test
    fun leavesUnmatchedText() {
        val body = "life which takes all the same. See 2026-02-02 and >>12"
        assertEquals(
            body,
            SchedulePolicy.displayBody(body, "Monday", convert = true, zone = pacific, nowEt = tuesday),
        )
    }
}
