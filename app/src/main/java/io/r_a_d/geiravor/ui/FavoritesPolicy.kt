package io.r_a_d.geiravor.ui

object FavoritesPolicy {
    const val RANDOM_BUSY_ID = 0L

    fun shouldFetch(nick: String): Boolean = nick.trim().isNotEmpty()

    fun isPeek(home: String, listing: String): Boolean {
        val stored = home.trim()
        val typed = listing.trim()
        return typed.isNotEmpty() && stored.isNotEmpty() && typed != stored
    }

    fun shouldCommitHome(home: String, listing: String): Boolean {
        val stored = home.trim()
        val typed = listing.trim()
        return stored.isEmpty() && typed.isNotEmpty()
    }

    fun rowCanRequest(allowed: Boolean, tracksId: Long?, requestable: Boolean): Boolean =
        allowed && tracksId != null && requestable

    fun randomCanRequest(allowed: Boolean, nick: String): Boolean =
        allowed && shouldFetch(nick)
}
