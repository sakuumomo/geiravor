package io.r_a_d.geiravor.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {
    @Test
    fun formatsMinutesAndZeroPaddedSeconds() {
        assertEquals("0:00", formatMmSs(0))
        assertEquals("3:05", formatMmSs(185))
        assertEquals("10:00", formatMmSs(600))
    }

    @Test
    fun clampsNegativeToZero() {
        assertEquals("0:00", formatMmSs(-12))
    }

    @Test
    fun liveDjHasNoTrackClock() {
        assertEquals(null, formatProgressClock(elapsedSecs = 0, durationSecs = null))
        assertEquals("1:40", formatProgressClock(elapsedSecs = 100, durationSecs = null))
        assertEquals("1:05 / 3:00", formatProgressClock(elapsedSecs = 65, durationSecs = 180))
    }
}
