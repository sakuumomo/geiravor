package io.r_a_d.geiravor.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesPolicyTest {
    @Test
    fun emptyNickDoesNotFetch() {
        assertFalse(FavoritesPolicy.shouldFetch(""))
        assertFalse(FavoritesPolicy.shouldFetch("  "))
        assertTrue(FavoritesPolicy.shouldFetch("Geiravor"))
    }

    @Test
    fun nullTrackIdCannotRequest() {
        assertFalse(FavoritesPolicy.rowCanRequest(allowed = true, tracksId = null, requestable = true))
        assertTrue(FavoritesPolicy.rowCanRequest(allowed = true, tracksId = 6130, requestable = true))
        assertFalse(FavoritesPolicy.rowCanRequest(allowed = false, tracksId = 6130, requestable = true))
    }

    @Test
    fun randomNeedsNickAndRequestsOn() {
        assertFalse(FavoritesPolicy.randomCanRequest(allowed = true, nick = ""))
        assertFalse(FavoritesPolicy.randomCanRequest(allowed = true, nick = "  "))
        assertFalse(FavoritesPolicy.randomCanRequest(allowed = false, nick = "Geiravor"))
        assertTrue(FavoritesPolicy.randomCanRequest(allowed = true, nick = "Geiravor"))
    }

    @Test
    fun songCooldownDisablesRowEvenWhenAllowed() {
        assertFalse(FavoritesPolicy.rowCanRequest(allowed = true, tracksId = 6130, requestable = false))
        assertTrue(FavoritesPolicy.rowCanRequest(allowed = true, tracksId = 6130, requestable = true))
    }
}
