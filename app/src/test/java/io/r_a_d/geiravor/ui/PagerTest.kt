package io.r_a_d.geiravor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PagerTest {
    @Test
    fun singlePageHasNoBar() {
        assertTrue(pagerSlots(1u, 1u).isEmpty())
    }

    @Test
    fun edgesStayPinned() {
        val s = pagerSlots(10u, 20u)
        assertEquals(PagerSlot.Prev, s.first())
        assertEquals(PagerSlot.Next, s.last())
        assertEquals(PagerSlot.Page(1u), s[1])
        assertEquals(PagerSlot.Page(20u), s[s.lastIndex - 1])
        assertEquals(1, s.count { it == PagerSlot.Ellipsis })
        assertTrue(s.contains(PagerSlot.Page(10u)))
        assertEquals(
            listOf(
                PagerSlot.Prev,
                PagerSlot.Page(1u),
                PagerSlot.Page(9u),
                PagerSlot.Page(10u),
                PagerSlot.Page(11u),
                PagerSlot.Ellipsis,
                PagerSlot.Page(20u),
                PagerSlot.Next,
            ),
            s,
        )
    }

    @Test
    fun firstPageHasNoLeadingEllipsis() {
        val s = pagerSlots(1u, 8u)
        assertEquals(listOf(PagerSlot.Prev, PagerSlot.Page(1u), PagerSlot.Page(2u), PagerSlot.Ellipsis, PagerSlot.Page(8u), PagerSlot.Next), s)
    }
}
