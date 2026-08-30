package io.r_a_d.geiravor.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationPolicyTest {
    @Test
    fun clampAndSplitHoursMinutes() {
        assertEquals(1, DurationPolicy.clampMinutes(0))
        assertEquals(1, DurationPolicy.clampMinutes(-4))
        assertEquals(12 * 60, DurationPolicy.clampMinutes(13 * 60))
        assertEquals(8, DurationPolicy.hours(8 * 60 + 5))
        assertEquals(5, DurationPolicy.minutePart(8 * 60 + 5))
        assertEquals(90, DurationPolicy.fromHoursMinutes(1, 30))
        assertEquals(1, DurationPolicy.fromHoursMinutes(0, 0))
        assertEquals(12 * 60, DurationPolicy.fromHoursMinutes(12, 30))
    }

    @Test
    fun formatOmitsZeroParts() {
        assertEquals("30 min", DurationPolicy.format(30))
        assertEquals("1 h", DurationPolicy.format(60))
        assertEquals("1 h 30 min", DurationPolicy.format(90))
        assertEquals("8 h", DurationPolicy.format(8 * 60))
    }
}
