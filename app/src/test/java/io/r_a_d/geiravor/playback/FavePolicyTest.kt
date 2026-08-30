package io.r_a_d.geiravor.playback

import androidx.media3.session.CommandButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.geiravor_core.FaveKind
import uniffi.geiravor_core.FaveResult

class FavePolicyTest {
    @Test
    fun connectionNickOverridesListNickForIrc() {
        assertEquals("IrcNick", FavePolicy.ircNick("IrcNick", "ListNick"))
        assertEquals("ListNick", FavePolicy.ircNick("", "ListNick"))
        assertEquals("ListNick", FavePolicy.ircNick("  ", "ListNick"))
        assertEquals("", FavePolicy.ircNick("", "  "))
        assertEquals("ListNick", FavePolicy.listNick("ListNick"))
        assertEquals("ListNick", FavePolicy.listNick("  ListNick  "))
        assertEquals("", FavePolicy.listNick("  "))
        assertEquals("IrcNick", FavePolicy.listNick("", "IrcNick"))
        assertEquals("IrcNick", FavePolicy.listNick("  ", "IrcNick"))
        assertEquals("ListNick", FavePolicy.listNick("ListNick", "IrcNick"))
    }

    @Test
    fun emptyNickIsNoFave() {
        assertFalse(FavePolicy.canFave(""))
        assertFalse(FavePolicy.canFave("  "))
        assertTrue(FavePolicy.canFave("Geiravor"))
    }

    @Test
    fun noopTellsPhoneToSetNick() {
        val msg = FavePolicy.phoneMessage(FaveResult(FaveKind.NOOP, "", false))
        assertEquals("Set your Rizon nick in Favorites.", msg)
    }

    @Test
    fun successHasNoBanner() {
        assertEquals(
            null,
            FavePolicy.phoneMessage(
                FaveResult(FaveKind.SUCCESS, "Hirasawa Susumu - Gats", true),
            ),
        )
        assertEquals(
            null,
            FavePolicy.phoneMessage(
                FaveResult(FaveKind.SUCCESS, "Hirasawa Susumu - Gats", false),
            ),
        )
    }

    @Test
    fun faveNoticeClearsWhenNpChanges() {
        assertTrue(FavePolicy.keepFaveNotice("Hirasawa Susumu - Gats", "Hirasawa Susumu - Gats"))
        assertTrue(FavePolicy.keepFaveNotice("Hirasawa Susumu - Gats", "hirasawa susumu - gats"))
        assertFalse(FavePolicy.keepFaveNotice("Hirasawa Susumu - Gats", "Someone - Else"))
        assertTrue(FavePolicy.keepFaveNotice(null, "Hirasawa Susumu - Gats"))
        assertTrue(FavePolicy.keepFaveNotice("", "Hirasawa Susumu - Gats"))
    }

    @Test
    fun failureShowsOnPhone() {
        assertEquals(
            "Need a catalog ID to unfave.",
            FavePolicy.phoneMessage(
                FaveResult(FaveKind.FAILED, "Need a catalog ID to unfave.", false),
            ),
        )
    }

    @Test
    fun successFillsOrUnfillsEverySurface() {
        val faved = FaveResult(FaveKind.SUCCESS, "Hirasawa Susumu - Gats", true)
        val unfaved = FaveResult(FaveKind.SUCCESS, "Hirasawa Susumu - Gats", false)
        val failed = FaveResult(FaveKind.FAILED, "Need a catalog ID to unfave.", false)
        assertTrue(FavePolicy.heartAfterResult(wasFilled = false, result = faved))
        assertFalse(FavePolicy.heartAfterResult(wasFilled = true, result = unfaved))
        assertTrue(FavePolicy.heartAfterResult(wasFilled = true, result = failed))
        assertFalse(FavePolicy.heartAfterResult(wasFilled = false, result = failed))
        assertFalse(
            FavePolicy.heartAfterResult(
                wasFilled = false,
                result = FaveResult(FaveKind.FAILED, "Connected as Geiravor_, not Geiravor. Fave cancelled.", false),
            ),
        )
        assertTrue(
            FavePolicy.heartAfterResult(
                wasFilled = true,
                result = FaveResult(FaveKind.FAILED, "That nick is already in use.", false),
            ),
        )
        assertTrue(FavePolicy.bumpFavoritesList(faved))
        assertTrue(FavePolicy.bumpFavoritesList(unfaved))
        assertFalse(FavePolicy.bumpFavoritesList(failed))
        assertFalse(
            FavePolicy.bumpFavoritesList(FaveResult(FaveKind.NOOP, "", false)),
        )
    }

    @Test
    fun portIsTcpRangeNotACharacterGuess() {
        assertEquals(FavePolicy.DEFAULT_BOUNCER_PORT, FavePolicy.sanitizePort(0))
        assertEquals(FavePolicy.DEFAULT_BOUNCER_PORT, FavePolicy.sanitizePort(70000))
        assertEquals(6697, FavePolicy.sanitizePort(6697))
        assertEquals("65535", FavePolicy.portInput("655351x"))
        assertEquals("443", FavePolicy.portInput("443"))
    }

    @Test
    fun catalogTrackIdIgnoresLiveDjLeftover() {
        assertEquals(42L, FavePolicy.catalogTrackId(isAfk = true, trackId = 42))
        assertEquals(0L, FavePolicy.catalogTrackId(isAfk = true, trackId = 0))
        assertEquals(0L, FavePolicy.catalogTrackId(isAfk = false, trackId = 99))
    }

    @Test
    fun autoAndShadeHeartMatchesFilledState() {
        assertEquals(CommandButton.ICON_HEART_FILLED, FavePolicy.heartIcon(true))
        assertEquals(CommandButton.ICON_HEART_UNFILLED, FavePolicy.heartIcon(false))
        val slots = FavePolicy.faveSlots()
        assertEquals(CommandButton.SLOT_FORWARD, slots[0])
        assertEquals(CommandButton.SLOT_OVERFLOW, slots[1])
        assertEquals(2, slots.size)
    }

    @Test
    fun secretsCannotBeCopied() {
        assertFalse(FavePolicy.allowsClipboardCopy(secret = true))
        assertTrue(FavePolicy.allowsClipboardCopy(secret = false))
    }

    @Test
    fun fingerprintCompareIgnoresColonsAndCase() {
        assertTrue(FavePolicy.fingerprintsMatch("", "AA:BB"))
        assertTrue(FavePolicy.fingerprintsMatch("aa:bb", "AABB"))
        assertTrue(FavePolicy.fingerprintsMatch("AA BB", "aabb"))
        assertFalse(FavePolicy.fingerprintsMatch("00:11", "AA:BB"))
        assertEquals("AB:CD 12", FavePolicy.fingerprintInput("AB:CD 12!"))
    }

    @Test
    fun certFingerprintLabelIsEmptyWhenMissing() {
        assertEquals("", FavePolicy.certFingerprintLabel(""))
        assertEquals("SHA-256 AA:BB", FavePolicy.certFingerprintLabel("AA:BB"))
        assertEquals(FavePolicy.CLEAR_CERT_TITLE, "Clear client certificate?")
        assertEquals(FavePolicy.CLEAR_KEY_TITLE, "Clear client key?")
    }
}
