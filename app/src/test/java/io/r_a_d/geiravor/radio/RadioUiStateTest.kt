package io.r_a_d.geiravor.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RadioUiStateTest {
    @Test
    fun snapshotCopyKeepsCanRequest() {
        val state = RadioUiState(canRequest = false, streamDown = false)
        val next = state.copy(streamDown = true)
        assertEquals(false, next.canRequest)
        assertFalse(next.canRequest == null)
    }
}
