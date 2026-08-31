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
    private val favesLast = HashMap<String, Int>()
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
        synchronized(lock) {
            if (page.currentPage <= page.lastPage) {
                faves[key to page.currentPage] = page
            }
            faves.keys.filter { it.first == key && it.second > page.lastPage }.forEach { faves.remove(it) }
            val stored = favesLast[key]
            favesLast[key] = when {
                stored == null -> page.lastPage
                page.currentPage >= page.lastPage -> page.lastPage
                page.currentPage == 1 -> page.lastPage
                else -> stored
            }
        }
    }

    fun favesRows(nick: String): Map<Int, List<FavoriteRow>> = synchronized(lock) {
        val key = nick.trim()
        faves.filterKeys { it.first == key }.mapKeys { it.key.second }.mapValues { it.value.data }
    }

    fun favesServerLast(nick: String): Int? = synchronized(lock) {
        favesLast[nick.trim()]
    }

    fun favesTotal(nick: String): Int? {
        val last = favesServerLast(nick) ?: return null
        val lastRows = faves(nick, last)?.data ?: return null
        return PanePolicy.favesCatalogSize(last, lastRows.size)
    }

    fun dropFaves(nick: String) {
        val key = nick.trim()
        synchronized(lock) {
            faves.keys.filter { it.first == key }.forEach { faves.remove(it) }
            favesLast.remove(key)
        }
    }

    fun searchVisible(): Int = synchronized(lock) { searchVisible }

    fun freezeSearchVisible(measured: Int): Int = synchronized(lock) {
        searchVisible = measured.coerceAtLeast(1)
        searchVisible
    }

    fun favesVisible(): Int = synchronized(lock) { favesVisible }

    fun freezeFavesVisible(measured: Int): Int = synchronized(lock) {
        favesVisible = measured.coerceAtLeast(1)
        favesVisible
    }

    fun clearForTests() {
        synchronized(lock) {
            search.clear()
            faves.clear()
            favesLast.clear()
            searchVisible = 0
            favesVisible = 0
        }
    }
}
