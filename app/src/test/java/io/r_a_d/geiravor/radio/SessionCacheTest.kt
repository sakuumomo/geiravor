package io.r_a_d.geiravor.radio

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SessionCacheTest {
    @Before
    fun reset() {
        SessionCache.clearForTests()
    }

    @Test
    fun favesCurrentDefaultsToOne() {
        assertEquals(1, SessionCache.favesCurrent("Geiravor"))
        SessionCache.putFavesCurrent("Geiravor", 3)
        assertEquals(3, SessionCache.favesCurrent("Geiravor"))
    }

    @Test
    fun searchQueryRoundTrip() {
        SessionCache.putSearchQuery("Aimer", 2)
        assertEquals("Aimer", SessionCache.searchQuery())
        assertEquals(2, SessionCache.searchCurrent())
    }

    @Test
    fun boardSectionSurvivesLeave() {
        assertEquals("News", SessionCache.boardSection())
        SessionCache.putBoardSection("Schedule")
        assertEquals("Schedule", SessionCache.boardSection())
    }
}
