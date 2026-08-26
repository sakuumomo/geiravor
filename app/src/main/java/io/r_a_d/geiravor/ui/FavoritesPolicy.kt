package io.r_a_d.geiravor.ui

object FavoritesPolicy {
    fun shouldFetch(nick: String): Boolean = nick.trim().isNotEmpty()

    fun rowCanRequest(allowed: Boolean, tracksId: Long?, requestable: Boolean): Boolean =
        allowed && tracksId != null && requestable
}
