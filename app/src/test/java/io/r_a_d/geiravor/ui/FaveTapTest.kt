package io.r_a_d.geiravor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.geiravor_core.FaveRow

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

class FavesPolicyTest {
    @Test
    fun lastPageDropsOverlap() {
        fun row(id: Long) = FaveRow(
            tracksId = id,
            artist = "A",
            title = "T$id",
            lastrequested = 0,
            lastplayed = 0,
            requestcount = 0,
        )
        val prev = listOf(row(1), row(2), row(3))
        val last = listOf(row(3), row(4))
        val leftover = FavesPolicy.dropOverlap(prev, last)
        assertEquals(1, leftover.size)
        assertEquals(4L, leftover[0].tracksId)
    }
}
