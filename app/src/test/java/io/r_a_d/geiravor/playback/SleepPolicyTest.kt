package io.r_a_d.geiravor.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepPolicyTest {
    @Test
    fun defaultsAreOffAtThirtyMinutes() {
        assertFalse(SleepPolicy.ENABLED_DEFAULT)
        assertEquals(30, SleepPolicy.DEFAULT_MINUTES)
        assertEquals(listOf(15, 30, 45, 60, 90), SleepPolicy.DURATION_CHOICES)
        assertEquals(15_000L, SleepPolicy.FADE_MS)
    }

    @Test
    fun durationCyclesAndClampsLikeSnooze() {
        assertEquals("30 min", SleepPolicy.formatMinutes(30))
        assertEquals(15, SleepPolicy.clampMinutes(1))
        assertEquals(90, SleepPolicy.clampMinutes(200))
        assertEquals(30, SleepPolicy.nextDurationChoice(15))
        assertEquals(15, SleepPolicy.nextDurationChoice(90))
        assertEquals(30 * 60_000L, SleepPolicy.durationMillis(30))
    }

    @Test
    fun fadeIsFullUntilTheLastFifteenSeconds() {
        assertEquals(1f, SleepPolicy.fadeMultiplier(16_000L), 0f)
        assertEquals(1f, SleepPolicy.fadeMultiplier(15_000L), 0f)
        assertEquals(0.5f, SleepPolicy.fadeMultiplier(7_500L), 0.0001f)
        assertEquals(0f, SleepPolicy.fadeMultiplier(0L), 0f)
        assertEquals(0f, SleepPolicy.fadeMultiplier(-1L), 0f)
        assertEquals(0.4f, SleepPolicy.outputGain(0.8f, 7_500L), 0.0001f)
        assertEquals(0.8f, SleepPolicy.outputGain(0.8f, 20_000L), 0.0001f)
    }

    @Test
    fun stopWhenArmedAndDeadlinePassed() {
        assertFalse(SleepPolicy.shouldStop(enabled = false, remainingMs = 0L))
        assertFalse(SleepPolicy.shouldStop(enabled = true, remainingMs = 1L))
        assertTrue(SleepPolicy.shouldStop(enabled = true, remainingMs = 0L))
    }

    @Test
    fun manualStopCancelsOnlyWhileTheTimerIsStillRunning() {
        val now = 1_000_000L
        val later = now + 60_000L
        assertTrue(SleepPolicy.shouldCancelOnStop(enabled = true, endsAtMillis = later, nowMillis = now))
        assertFalse(SleepPolicy.shouldCancelOnStop(enabled = true, endsAtMillis = now, nowMillis = now))
        assertFalse(SleepPolicy.shouldCancelOnStop(enabled = false, endsAtMillis = later, nowMillis = now))
    }

    @Test
    fun ticksFasterDuringFade() {
        assertEquals(SleepPolicy.TICK_SLOW_MS, SleepPolicy.tickMs(16_000L))
        assertEquals(SleepPolicy.TICK_FADE_MS, SleepPolicy.tickMs(15_000L))
        assertEquals(SleepPolicy.TICK_FADE_MS, SleepPolicy.tickMs(100L))
    }
}
