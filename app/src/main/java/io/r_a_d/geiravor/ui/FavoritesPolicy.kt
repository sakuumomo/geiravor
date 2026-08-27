package io.r_a_d.geiravor.ui

object FavoritesPolicy {
    const val RANDOM_BUSY_ID = 0L

    fun shouldFetch(nick: String): Boolean = nick.trim().isNotEmpty()

    fun rowCanRequest(allowed: Boolean, tracksId: Long?, requestable: Boolean): Boolean =
        allowed && tracksId != null && requestable

    fun randomCanRequest(allowed: Boolean, nick: String): Boolean =
        allowed && shouldFetch(nick)
}
