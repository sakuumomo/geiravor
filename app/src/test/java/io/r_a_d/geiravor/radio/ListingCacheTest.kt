package io.r_a_d.geiravor.radio

import io.r_a_d.geiravor.ui.PanePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import uniffi.geiravor_core.SearchHit
import uniffi.geiravor_core.SearchPage

class ListingCacheTest {
    @Before
    fun reset() {
        ListingCache.clearForTests()
    }

    @Test
    fun searchPageStaysUntilDropped() {
        ListingCache.putSearch("aimer", page(current = 1, total = 40, last = 2, from = 0))
        ListingCache.putSearch("aimer", page(current = 2, total = 40, last = 2, from = 20))
        assertEquals(40, ListingCache.searchTotal("aimer"))
        assertEquals(2, ListingCache.searchServerLast("aimer"))
        assertEquals(20, ListingCache.search(query = "aimer", page = 1)!!.data.size)
        assertNull(ListingCache.search(query = "other", page = 1))
        val window = PanePolicy.window(
            ListingCache.searchRows("aimer"),
            start = 18,
            count = 6,
            perServer = PanePolicy.SEARCH_PER_PAGE,
        )
        assertEquals(6, window!!.size)
        assertEquals(18L, window.first().id)
        assertEquals(23L, window.last().id)
    }

    @Test
    fun freezeSearchVisibleDoesNotShrinkOnRotate() {
        assertEquals(6, ListingCache.freezeSearchVisible(6))
        assertEquals(6, ListingCache.freezeSearchVisible(3))
    }

    private fun page(current: Int, total: Int, last: Int, from: Int) = SearchPage(
        total = total.toLong(),
        perPage = 20,
        currentPage = current.toLong(),
        lastPage = last.toLong(),
        from = from.toLong(),
        to = (from + 19).toLong(),
        data = (from until from + 20).map { n ->
            SearchHit(
                artist = "A",
                title = "$n",
                id = n.toLong(),
                lastPlayed = 0,
                lastRequested = 0,
                requestable = true,
            )
        },
    )
}
