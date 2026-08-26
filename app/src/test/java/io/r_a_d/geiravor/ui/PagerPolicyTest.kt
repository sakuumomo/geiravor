package io.r_a_d.geiravor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PagerPolicyTest {
    @Test
    fun hiddenWhenOnlyOnePage() {
        assertFalse(PagerPolicy.visible(1))
        assertTrue(PagerPolicy.visible(2))
    }

    @Test
    fun firstAndLastAlwaysPresentWithJumpBeforeLast() {
        val labels = PagerPolicy.items(current = 1, last = 12).map { PagerPolicy.label(it) }
        assertEquals(listOf("<", "1", "2", "3", "4", "...", "12", ">"), labels)
    }

    @Test
    fun windowFollowsCurrent() {
        val labels = PagerPolicy.items(current = 5, last = 12).map { PagerPolicy.label(it) }
        assertEquals(listOf("<", "1", "4", "5", "6", "...", "12", ">"), labels)
    }

    @Test
    fun windowShiftsLeftNearTheEnd() {
        val labels = PagerPolicy.items(current = 12, last = 12).map { PagerPolicy.label(it) }
        assertEquals(listOf("<", "1", "9", "10", "11", "...", "12", ">"), labels)
    }

    @Test
    fun twoPagesAreFirstJumpLast() {
        val labels = PagerPolicy.items(current = 1, last = 2).map { PagerPolicy.label(it) }
        assertEquals(listOf("<", "1", "...", "2", ">"), labels)
    }

    @Test
    fun prevDisabledOnFirstNextDisabledOnLast() {
        val first = PagerPolicy.items(1, 5)
        assertFalse(first.first { it.kind == PagerPolicy.Kind.Prev }.enabled)
        assertTrue(first.first { it.kind == PagerPolicy.Kind.Next }.enabled)
        val last = PagerPolicy.items(5, 5)
        assertTrue(last.first { it.kind == PagerPolicy.Kind.Prev }.enabled)
        assertFalse(last.first { it.kind == PagerPolicy.Kind.Next }.enabled)
    }

    @Test
    fun currentPageIsMarked() {
        val items = PagerPolicy.items(4, 10)
        val current = items.filter { it.current }
        assertEquals(1, current.size)
        assertEquals(4, current[0].page)
    }

    @Test
    fun jumpClampsToRange() {
        assertEquals(1, PagerPolicy.clampJump(0, 8))
        assertEquals(8, PagerPolicy.clampJump(99, 8))
        assertEquals(3, PagerPolicy.clampJump(3, 8))
    }

    @Test
    fun digitCountFollowsLastPage() {
        assertEquals(1, PagerPolicy.digitCount(9))
        assertEquals(2, PagerPolicy.digitCount(10))
        assertEquals(3, PagerPolicy.digitCount(512))
    }
}
