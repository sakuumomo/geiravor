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

    @Test
    fun rememberLastSuccessfulNickNotFirst() {
        assertFalse(FavoritesPolicy.isPeek(home = "", listing = "Alice"))
        assertFalse(FavoritesPolicy.isPeek(home = "Alice", listing = "Alice"))
        assertFalse(FavoritesPolicy.isPeek(home = "Alice", listing = "  Alice  "))
        assertTrue(FavoritesPolicy.isPeek(home = "Alice", listing = "Bob"))
        assertFalse(FavoritesPolicy.isPeek(home = "Alice", listing = ""))
        assertTrue(FavoritesPolicy.shouldCommitHome(home = "", listing = "Alice"))
        assertTrue(FavoritesPolicy.shouldCommitHome(home = "s", listing = "sakurai"))
        assertTrue(FavoritesPolicy.shouldCommitHome(home = "Alice", listing = "Bob"))
        assertFalse(FavoritesPolicy.shouldCommitHome(home = "Alice", listing = "Alice"))
        assertFalse(FavoritesPolicy.shouldCommitHome(home = "", listing = "  "))
        assertTrue(
            FavoritesPolicy.shouldRememberAfterFetch(
                home = "s",
                typed = "sakurai",
                fetched = "sakurai",
            ),
        )
        assertFalse(
            FavoritesPolicy.shouldRememberAfterFetch(
                home = "s",
                typed = "sakurai",
                fetched = "s",
            ),
        )
        assertFalse(
            FavoritesPolicy.shouldRememberAfterFetch(
                home = "Alice",
                typed = "",
                fetched = "IrcNick",
            ),
        )
        assertFalse(
            FavoritesPolicy.shouldRememberAfterFetch(
                home = "Alice",
                typed = "Alice",
                fetched = "Alice",
            ),
        )
        assertTrue(FavoritesPolicy.shouldPersistListing(listOf("Alice"), "Alice"))
        assertTrue(FavoritesPolicy.shouldPersistListing(listOf("Same"), "Same"))
        assertFalse(FavoritesPolicy.shouldPersistListing(listOf("Alice"), "Bob"))
        assertFalse(FavoritesPolicy.shouldPersistListing(listOf("Alice"), ""))
        assertTrue(
            FavoritesPolicy.shouldWriteListing(
                keep = listOf("Alice"),
                home = "s",
                typed = "Alice",
                fetched = "Alice",
            ),
        )
        assertTrue(
            FavoritesPolicy.shouldWriteListing(
                keep = listOf("Alice"),
                home = "Alice",
                typed = "Bob",
                fetched = "Bob",
            ),
        )
        assertFalse(
            FavoritesPolicy.shouldWriteListing(
                keep = listOf("Alice"),
                home = "Alice",
                typed = "Bob",
                fetched = "Al",
            ),
        )
    }
}
