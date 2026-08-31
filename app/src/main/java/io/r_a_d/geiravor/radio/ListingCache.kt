package io.r_a_d.geiravor.radio

import io.r_a_d.geiravor.ui.PanePolicy
import uniffi.geiravor_core.FavoriteRow
import uniffi.geiravor_core.FavoritesPage
import uniffi.geiravor_core.SearchHit
import uniffi.geiravor_core.SearchPage

/** Process RAM for search/faves server pages. Not disk. */
object ListingCache {
    private val lock = Any()
    private val search = HashMap<Pair<String, Int>, SearchPage>()
    private val faves = HashMap<Pair<String, Int>, FavoritesPage>()
    private var searchVisible = 0
    private var favesVisible = 0

    fun search(query: String, page: Int): SearchPage? = synchronized(lock) {
        search[query.trim() to page]
    }

    fun putSearch(query: String, page: SearchPage) {
        val key = query.trim()
        if (key.isEmpty()) {
            return
        }
        synchronized(lock) { search[key to page.currentPage.toInt()] = page }
    }

    fun searchRows(query: String): Map<Int, List<SearchHit>> = synchronized(lock) {
        val key = query.trim()
        search.filterKeys { it.first == key }.mapKeys { it.key.second }.mapValues { it.value.data }
    }

    fun searchTotal(query: String): Int? = synchronized(lock) {
        val key = query.trim()
        search.entries.firstOrNull { it.key.first == key }?.value?.total?.toInt()
    }

    fun searchServerLast(query: String): Int? = synchronized(lock) {
        val key = query.trim()
        search.entries.firstOrNull { it.key.first == key }?.value?.lastPage?.toInt()
    }

    fun faves(nick: String, page: Int): FavoritesPage? = synchronized(lock) {
        faves[nick.trim() to page]
    }

    fun putFaves(nick: String, page: FavoritesPage) {
        val key = nick.trim()
        if (key.isEmpty()) {
            return
        }
        synchronized(lock) { faves[key to page.currentPage] = page }
    }

    fun favesRows(nick: String): Map<Int, List<FavoriteRow>> = synchronized(lock) {
        val key = nick.trim()
        faves.filterKeys { it.first == key }.mapKeys { it.key.second }.mapValues { it.value.data }
    }

    fun favesServerLast(nick: String): Int? = synchronized(lock) {
        val key = nick.trim()
        faves.entries.filter { it.key.first == key }.maxOfOrNull { it.value.lastPage }
    }

    fun favesTotal(nick: String): Int? {
        val last = favesServerLast(nick) ?: return null
        val lastRows = faves(nick, last)?.data?.size
        return if (last == 1) {
            lastRows ?: faves(nick, 1)?.data?.size
        } else {
            val leftover = lastRows ?: PanePolicy.FAVES_PER_PAGE
            (last - 1) * PanePolicy.FAVES_PER_PAGE + leftover
        }
    }

    fun dropFaves(nick: String) {
        val key = nick.trim()
        synchronized(lock) {
            faves.keys.filter { it.first == key }.forEach { faves.remove(it) }
        }
    }

    fun searchVisible(): Int = synchronized(lock) { searchVisible }

    fun freezeSearchVisible(measured: Int): Int = synchronized(lock) {
        searchVisible = PanePolicy.freezeVisible(searchVisible, measured)
        searchVisible
    }

    fun favesVisible(): Int = synchronized(lock) { favesVisible }

    fun freezeFavesVisible(measured: Int): Int = synchronized(lock) {
        favesVisible = PanePolicy.freezeVisible(favesVisible, measured)
        favesVisible
    }

    fun clearForTests() {
        synchronized(lock) {
            search.clear()
            faves.clear()
            searchVisible = 0
            favesVisible = 0
        }
    }
}
