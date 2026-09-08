package io.r_a_d.geiravor.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaveErrorTest {
    @Test
    fun npChangeWithErrorFades() {
        assertTrue(FaveError.shouldFade("failed", "A - 1", "B - 2"))
    }

    @Test
    fun sameNpKeepsError() {
        assertFalse(FaveError.shouldFade("failed", "A - 1", "A - 1"))
    }

    @Test
    fun blankErrorNeverFades() {
        assertFalse(FaveError.shouldFade(null, "A", "B"))
        assertFalse(FaveError.shouldFade("", "A", "B"))
    }
}
