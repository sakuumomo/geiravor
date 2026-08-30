package io.r_a_d.geiravor.radio

/** UI listing position for this process. HTTP pages live in RadioCore. */
object SessionCache {
    private val lock = Any()
    private val favesCurrent = HashMap<String, Int>()
    private var searchQuery: String = ""
    private var searchCurrent: Int = 1
    private var newsCurrent: Int = 1
    private var boardSection: String = "News"

    fun favesCurrent(nick: String): Int = synchronized(lock) { favesCurrent[nick.trim()] ?: 1 }

    fun putFavesCurrent(nick: String, page: Int) {
        val key = nick.trim()
        if (key.isEmpty()) {
            return
        }
        synchronized(lock) { favesCurrent[key] = page.coerceAtLeast(1) }
    }

    fun searchQuery(): String = synchronized(lock) { searchQuery }

    fun searchCurrent(): Int = synchronized(lock) { searchCurrent }

    fun putSearchQuery(query: String, page: Int = 1) {
        synchronized(lock) {
            searchQuery = query.trim()
            searchCurrent = page.coerceAtLeast(1)
        }
    }

    fun newsCurrent(): Int = synchronized(lock) { newsCurrent }

    fun putNewsCurrent(page: Int) {
        synchronized(lock) { newsCurrent = page.coerceAtLeast(1) }
    }

    fun boardSection(): String = synchronized(lock) { boardSection }

    fun putBoardSection(section: String) {
        synchronized(lock) { boardSection = section }
    }

    fun clearForTests() {
        synchronized(lock) {
            favesCurrent.clear()
            searchQuery = ""
            searchCurrent = 1
            newsCurrent = 1
            boardSection = "News"
        }
    }
}
