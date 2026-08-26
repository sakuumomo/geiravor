package io.r_a_d.geiravor.playback

import android.content.res.Resources
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import uniffi.geiravor_core.SongProgress
import uniffi.geiravor_core.Status

data class NowPlayingCard(
    val title: String,
    val artist: String,
    val albumArtist: String,
    val artworkUrl: String,
)

object SessionMetadata {
    private const val ELLIPSIS = "…"
    private const val SHADE_CHROME_DP = 168f
    private const val SHADE_MIN_TEXT_DP = 96f
    private const val SHADE_TEXT_SP = 14f
    private const val SHADE_EM = 0.55f

    fun shadeMaxChars(resources: Resources): Int {
        val dm = resources.displayMetrics
        return shadeMaxChars(dm.widthPixels, dm.density, resources.configuration.fontScale)
    }

    fun shadeMaxChars(widthPx: Int, density: Float, fontScale: Float): Int {
        val textPx = (widthPx - SHADE_CHROME_DP * density).coerceAtLeast(SHADE_MIN_TEXT_DP * density)
        val em = SHADE_TEXT_SP * density * fontScale
        return (textPx / (em * SHADE_EM)).toInt().coerceAtLeast(8)
    }

    fun present(value: String): String? = value.trim().takeIf { it.isNotEmpty() }

    fun shadeArtist(card: NowPlayingCard, maxChars: Int? = null): String {
        val artist = present(card.artist)
        val dj = present(card.albumArtist)
        return when {
            artist == null -> dj.orEmpty()
            dj == null -> artist
            else -> ellipsizeKeepingSuffix(artist, " | $dj", maxChars)
        }
    }

    fun ellipsizeKeepingSuffix(prefix: String, suffix: String, maxChars: Int?): String {
        val full = prefix + suffix
        if (maxChars == null || full.length <= maxChars) {
            return full
        }
        val reserved = suffix.length + ELLIPSIS.length
        if (maxChars <= reserved) {
            return ELLIPSIS + suffix
        }
        return prefix.take(maxChars - reserved) + ELLIPSIS + suffix
    }

    fun card(status: Status?): NowPlayingCard? {
        if (status == null) return null
        return NowPlayingCard(
            title = status.title.trim(),
            artist = status.artist.trim(),
            albumArtist = status.dj.name.trim(),
            artworkUrl = "https://r-a-d.io/api/dj-image/${status.dj.image}",
        )
    }

    fun fromStatus(status: Status?, shadeMaxChars: Int? = null): MediaMetadata? {
        val card = card(status) ?: return null
        return sessionMetadata(card, shadeArtist(card, shadeMaxChars)).buildUpon()
            .setArtworkUri(Uri.parse(card.artworkUrl))
            .build()
    }

    fun sessionMetadata(card: NowPlayingCard, shadeLine: String = shadeArtist(card)): MediaMetadata {
        val artist = present(card.artist)
        val dj = present(card.albumArtist)
        val builder = MediaMetadata.Builder()
            .setTitle(card.title)
            .setDisplayTitle(card.title)
            .setArtist(shadeLine)
        dj?.let { builder.setAlbumArtist(it) }
        if (artist != null) {
            builder.setSubtitle(artist)
            dj?.let { builder.setDescription(it) }
        } else {
            dj?.let { builder.setSubtitle(it) }
        }
        return builder.build()
    }

    fun liveMediaItem(
        status: Status?,
        mediaId: String = AutoBrowse.NOW_PLAYING,
        shadeMaxChars: Int? = null,
    ): MediaItem = liveMediaItem(fromStatus(status, shadeMaxChars), mediaId)

    fun liveMediaItem(
        metadata: MediaMetadata?,
        mediaId: String = AutoBrowse.NOW_PLAYING,
    ): MediaItem {
        val builder = MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(LivePlaybackPolicy.STREAM_URL)
        metadata?.let { builder.setMediaMetadata(it) }
        return builder.build()
    }

    fun replaceLiveMetadata(current: MediaItem?, metadata: MediaMetadata?): MediaItem? {
        if (current == null || metadata == null) {
            return null
        }
        if (current.mediaMetadata == metadata) {
            return null
        }
        return current.buildUpon().setMediaMetadata(metadata).build()
    }

    fun published(api: MediaMetadata?, exo: MediaMetadata): MediaMetadata = api ?: exo

    fun positionMs(progress: SongProgress?): Long =
        (progress?.elapsedSecs ?: 0).coerceAtLeast(0) * 1000

    fun durationMs(progress: SongProgress?): Long {
        val secs = progress?.durationSecs ?: return C.TIME_UNSET
        return secs.coerceAtLeast(0) * 1000
    }
}
