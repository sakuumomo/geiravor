package io.r_a_d.geiravor.playback

import androidx.media3.session.CommandButton
import uniffi.geiravor_core.FaveConfig
import uniffi.geiravor_core.FaveKind
import uniffi.geiravor_core.FaveResult
import uniffi.geiravor_core.IrcProfile
import uniffi.geiravor_core.Status

object FavePolicy {
    const val DEFAULT_BOUNCER_PORT = 6697
    const val PORT_MIN = 1
    const val PORT_MAX = 65535
    const val PORT_DIGITS = 5

    data class HeartUpdate(
        val filled: Boolean,
        val notice: String?,
        val bumpList: Boolean,
    )

    fun canFave(nick: String): Boolean = nick.trim().isNotEmpty()

    fun ircNick(connectionNick: String, listNick: String): String {
        val connection = connectionNick.trim()
        return connection.ifEmpty { listNick.trim() }
    }

    fun listNick(favesNick: String, connectionNick: String = ""): String {
        val list = favesNick.trim()
        return list.ifEmpty { connectionNick.trim() }
    }

    fun sanitizePort(port: Int): Int =
        if (port in PORT_MIN..PORT_MAX) port else DEFAULT_BOUNCER_PORT

    fun portInput(raw: String): String =
        raw.filter { it.isDigit() }.take(PORT_DIGITS)

    fun config(
        nick: String,
        profile: IrcProfile,
        nickservPassword: String,
        bouncerHost: String,
        bouncerPort: Int,
        bouncerPass: String,
        allowInsecureTls: Boolean,
        saslUsername: String,
        saslPassword: String,
        clientCertPem: String,
        clientKeyPem: String,
        tlsFingerprint: String,
        listNick: String = "",
    ): FaveConfig = FaveConfig(
        nick = nick,
        listNick = listNick(listNick).ifEmpty { nick.trim() },
        profile = profile,
        nickservPassword = nickservPassword,
        bouncerHost = bouncerHost,
        bouncerPort = sanitizePort(bouncerPort).toUShort(),
        bouncerPass = bouncerPass,
        allowInsecureTls = allowInsecureTls,
        saslUsername = saslUsername,
        saslPassword = saslPassword,
        clientCertPem = clientCertPem,
        clientKeyPem = clientKeyPem,
        tlsFingerprint = tlsFingerprint,
    )

    fun allowsClipboardCopy(secret: Boolean): Boolean = !secret

    fun fingerprintInput(raw: String): String =
        raw.filter { it.isLetterOrDigit() || it == ':' || it == ' ' }

    fun fingerprintsMatch(expected: String, actual: String): Boolean {
        val want = normalizeFingerprint(expected)
        return want.isEmpty() || want == normalizeFingerprint(actual)
    }

    fun normalizeFingerprint(value: String): String =
        value.filter { it.isLetterOrDigit() }.uppercase()

    fun certFingerprintLabel(fingerprint: String): String =
        if (fingerprint.isBlank()) "" else "SHA-256 $fingerprint"

    const val CLEAR_CERT_TITLE = "Clear client certificate?"
    const val CLEAR_KEY_TITLE = "Clear client key?"
    const val CLEAR_PEM_BODY = "This removes it from the device. You cannot undo this."

    fun catalogTrackId(isAfk: Boolean, trackId: Long): Long =
        if (isAfk && trackId > 0) trackId else 0L

    fun isListed(
        nick: String,
        status: Status?,
        query: (String, Long, String) -> Boolean,
    ): Boolean {
        if (nick.isEmpty()) {
            return false
        }
        return query(
            nick,
            catalogTrackId(status?.isAfkStream == true, status?.trackId ?: 0L),
            status?.np.orEmpty(),
        )
    }

    fun probeMessage(result: FaveResult): Pair<Boolean, String> =
        when (result.kind) {
            FaveKind.SUCCESS -> true to result.message.ifBlank { "Connected." }
            FaveKind.NOOP -> false to "Set a connection nick."
            FaveKind.FAILED -> false to result.message.ifBlank { "Connection failed." }
        }

    fun phoneMessage(result: FaveResult): String? =
        when (result.kind) {
            FaveKind.NOOP -> "Set your Rizon nick in Favorites."
            FaveKind.SUCCESS -> null
            FaveKind.FAILED -> result.message.ifBlank { "Fave failed." }
        }

    fun keepFaveNotice(previousNp: String?, nextNp: String?): Boolean {
        val prev = previousNp?.trim().orEmpty()
        val next = nextNp?.trim().orEmpty()
        return prev.isEmpty() || prev.equals(next, ignoreCase = true)
    }

    fun heartAfterResult(wasFilled: Boolean, result: FaveResult): Boolean =
        if (result.kind == FaveKind.SUCCESS) result.favorited else wasFilled

    fun bumpFavoritesList(result: FaveResult): Boolean = result.kind == FaveKind.SUCCESS

    fun heartUpdate(wasFilled: Boolean, result: FaveResult): HeartUpdate = HeartUpdate(
        filled = heartAfterResult(wasFilled, result),
        notice = phoneMessage(result),
        bumpList = bumpFavoritesList(result),
    )

    fun heartIcon(filled: Boolean): Int =
        if (filled) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED

    /** Compact Auto card: forward slot (skip-next is not advertised). Overflow when focused. */
    fun faveSlots(): IntArray =
        intArrayOf(CommandButton.SLOT_FORWARD, CommandButton.SLOT_OVERFLOW)
}
