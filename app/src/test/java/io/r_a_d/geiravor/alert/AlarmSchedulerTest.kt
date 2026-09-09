package io.r_a_d.geiravor.alert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AlarmSchedulerTest {
    @Test
    fun nextFireIsTomorrowWhenTodaysTimeHasPassed() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = cal.timeInMillis
        val next = AlarmScheduler.nextFireMillis(now, 7, 0)
        val expect = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 7)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(expect.timeInMillis, next)
    }

    @Test
    fun nextFireIsLaterTodayWhenTimeHasNotPassed() {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 6)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = cal.timeInMillis
        val next = AlarmScheduler.nextFireMillis(now, 7, 30)
        assertTrue(next > now)
        val got = Calendar.getInstance().apply { timeInMillis = next }
        assertEquals(7, got.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, got.get(Calendar.MINUTE))
        assertEquals(cal.get(Calendar.DAY_OF_YEAR), got.get(Calendar.DAY_OF_YEAR))
    }
}
