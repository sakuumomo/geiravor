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
        return typed.isNotEmpty() && typed != stored
    }

    /** Persist after a successful GET of the typed nick, not the Connection fallback. */
    fun shouldRememberAfterFetch(home: String, typed: String, fetched: String): Boolean {
        val listing = fetched.trim()
        return listing.isNotEmpty() &&
            listing == typed.trim() &&
            shouldCommitHome(home, listing)
    }

    fun shouldPersistListing(keep: List<String>, listing: String): Boolean {
        val nick = listing.trim()
        return nick.isNotEmpty() && keep.any { it == nick }
    }

    fun shouldWriteListing(
        keep: List<String>,
        home: String,
        typed: String,
        fetched: String,
    ): Boolean = shouldRememberAfterFetch(home, typed, fetched) ||
        shouldPersistListing(keep, fetched)

    fun rowCanRequest(allowed: Boolean, tracksId: Long?, requestable: Boolean): Boolean =
        allowed && tracksId != null && requestable

    fun randomCanRequest(allowed: Boolean, nick: String): Boolean =
        allowed && shouldFetch(nick)
}
