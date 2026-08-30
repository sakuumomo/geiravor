package io.r_a_d.geiravor.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import uniffi.geiravor_core.ScheduleDay

class ScheduleStoreTest {
    @Test
    fun writeSkipsWhenWeekMatches() {
        val days = listOf(
            ScheduleDay("Monday", "streams at 8pm", "kipukun", "45.png"),
        )
        assertNull(ScheduleStore.write(ScheduleStore.fromDays(days), days))
    }

    @Test
    fun writeWhenBodyChanges() {
        val existing = ScheduleStore.fromDays(
            listOf(ScheduleDay("Monday", "streams at 8pm", "kipukun", "45.png")),
        )
        val write = ScheduleStore.write(
            existing,
            listOf(ScheduleDay("Monday", "streams at 9pm", "kipukun", "45.png")),
        )
        assertEquals("streams at 9pm", write!!.single().body)
    }
}
