package io.r_a_d.geiravor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaveTapTest {
    @Test
    fun firstTapStartsWindow() {
        assertTrue(FaveTap.accept(busy = false, tapsInWindow = 0))
        assertEquals(1, FaveTap.afterAccept(busy = false, tapsInWindow = 0))
    }

    @Test
    fun queuesSecondAndThird() {
        assertTrue(FaveTap.accept(busy = true, tapsInWindow = 1))
        assertEquals(2, FaveTap.afterAccept(busy = true, tapsInWindow = 1))
        assertTrue(FaveTap.accept(busy = true, tapsInWindow = 2))
        assertEquals(3, FaveTap.afterAccept(busy = true, tapsInWindow = 2))
    }

    @Test
    fun fourthInWindowIsIgnored() {
        assertFalse(FaveTap.accept(busy = true, tapsInWindow = 3))
        assertEquals(3, FaveTap.afterAccept(busy = true, tapsInWindow = 3))
    }
}
