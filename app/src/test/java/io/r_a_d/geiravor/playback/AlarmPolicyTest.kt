package io.r_a_d.geiravor.playback

import io.r_a_d.geiravor.compat.ExactAlarms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

class AlarmPolicyTest {
    @Test
    fun defaultsAreOffAtEightWithTenMinuteSnooze() {
        assertFalse(AlarmPolicy.ENABLED_DEFAULT)
        assertEquals(8, AlarmPolicy.DEFAULT_HOUR)
        assertEquals(0, AlarmPolicy.DEFAULT_MINUTE)
        assertTrue(AlarmPolicy.SNOOZE_ENABLED_DEFAULT)
        assertEquals(10, AlarmPolicy.DEFAULT_SNOOZE_MINUTES)
    }

    @Test
    fun formatAndClampTime() {
        assertEquals("08:00", AlarmPolicy.formatTime(8, 0))
        assertEquals("00:05", AlarmPolicy.formatTime(0, 5))
        assertEquals(23, AlarmPolicy.clampHour(99))
        assertEquals(0, AlarmPolicy.clampHour(-1))
        assertEquals(59, AlarmPolicy.clampMinute(99))
        assertEquals(11, AlarmPolicy.clampSnoozeMinutes(11))
        assertEquals(1, AlarmPolicy.clampSnoozeMinutes(0))
        assertEquals("1 h 15 min", AlarmPolicy.formatSnooze(75))
    }

    @Test
    fun nextTriggerIsTomorrowWhenTodaysTimeHasPassed() {
        val zone = ZoneOffset.UTC
        val now = 1_704_067_200_000L
        val eight = AlarmPolicy.nextTriggerMillis(now, hour = 8, minute = 0, zone)
        assertEquals(now + TimeUnit.HOURS.toMillis(8), eight)
        val afterNine = now + TimeUnit.HOURS.toMillis(9)
        val nextEight = AlarmPolicy.nextTriggerMillis(afterNine, hour = 8, minute = 0, zone)
        assertEquals(now + TimeUnit.DAYS.toMillis(1) + TimeUnit.HOURS.toMillis(8), nextEight)
    }

    @Test
    fun ringingActionsAreLabeledStopAndOptionalSnooze() {
        assertEquals(listOf("Stop", "Snooze"), AlarmPolicy.ringingActions(snoozeEnabled = true))
        assertEquals(listOf("Stop"), AlarmPolicy.ringingActions(snoozeEnabled = false))
    }

    @Test
    fun fallbackWhenStreamDidNotStart() {
        assertFalse(AlarmPolicy.shouldPlayFallback(streamPlaying = true))
        assertTrue(AlarmPolicy.shouldPlayFallback(streamPlaying = false))
    }

    @Test
    fun exactAlarmDenialIsVisible() {
        assertTrue(AlarmPolicy.shouldExplainExactDenied(needsGrant = true, canSchedule = false))
        assertFalse(AlarmPolicy.shouldExplainExactDenied(needsGrant = true, canSchedule = true))
        assertFalse(AlarmPolicy.shouldExplainExactDenied(needsGrant = false, canSchedule = false))
        assertTrue(AlarmPolicy.EXACT_DENIED.isNotBlank())
    }

    @Test
    fun snoozeDelayIsMinutesFromNow() {
        assertEquals(10 * 60_000L, AlarmPolicy.snoozeDelayMillis(10))
        assertEquals(90 * 60_000L, AlarmPolicy.snoozeDelayMillis(90))
    }
}

class ExactAlarmsTest {
    @Test
    fun runtimeGrantStartsAtApi31() {
        assertFalse(ExactAlarms.needsRuntimeGrant(sdkInt = 30))
        assertTrue(ExactAlarms.needsRuntimeGrant(sdkInt = 31))
        assertTrue(ExactAlarms.needsRuntimeGrant(sdkInt = 33))
    }
}
