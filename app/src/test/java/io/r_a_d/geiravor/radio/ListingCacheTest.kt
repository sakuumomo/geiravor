package io.r_a_d.geiravor.radio

import io.r_a_d.geiravor.ui.PanePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import uniffi.geiravor_core.FavoriteRow
import uniffi.geiravor_core.FavoritesPage
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
    fun visibleTracksTheCurrentPane() {
        assertEquals(6, ListingCache.freezeSearchVisible(6))
        assertEquals(3, ListingCache.freezeSearchVisible(3))
    }

    @Test
    fun favesTotalWaitsForLeftoverLastPage() {
        ListingCache.putFaves("k", favesPage(page = 1, last = 8, count = 100, start = 1))
        assertEquals(8, ListingCache.favesServerLast("k"))
        assertNull(ListingCache.favesTotal("k"))
        ListingCache.putFaves("k", favesPage(page = 8, last = 8, count = 30, start = 701))
        assertEquals(7 * 100 + 30, ListingCache.favesTotal("k"))
        assertEquals(61, PanePolicy.lastPage(ListingCache.favesTotal("k")!!, 12))
    }

    @Test
    fun favesLastPageTrimDropsInflatedLast() {
        ListingCache.putFaves("k", favesPage(page = 1, last = 8, count = 100, start = 1))
        ListingCache.putFaves(
            "k",
            FavoritesPage(currentPage = 8, lastPage = 7, data = emptyList()),
        )
        assertEquals(7, ListingCache.favesServerLast("k"))
        assertNull(ListingCache.faves("k", 8))
        ListingCache.putFaves("k", favesPage(page = 7, last = 7, count = 40, start = 601))
        assertEquals(6 * 100 + 40, ListingCache.favesTotal("k"))
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

    private fun favesPage(page: Int, last: Int, count: Int, start: Int) = FavoritesPage(
        currentPage = page,
        lastPage = last,
        data = (start until start + count).map { n ->
            FavoriteRow(
                tracksId = n.toLong(),
                meta = "A - $n",
                artist = "A",
                title = "$n",
                lastRequested = null,
                lastPlayed = null,
                requestCount = null,
            )
        },
    )
}
