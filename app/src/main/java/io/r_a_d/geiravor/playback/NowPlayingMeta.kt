package io.r_a_d.geiravor.playback

import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata

/**
 * Auto card vs phone shade. `docs/spec/android-auto.md`, `docs/spec/playback.md`.
 *
 * Auto: [displayTitle] + artist subtitle + DJ description.
 * Shade line 2 is [ShadeLine] from the extras, not Auto's subtitle.
 */
object NowPlayingMeta {
    const val EXTRA_ARTIST = "io.r_a_d.geiravor.artist"
    const val EXTRA_DJ = "io.r_a_d.geiravor.dj"
    const val EXTRA_ELAPSED_MS = "io.r_a_d.geiravor.elapsed_ms"
    const val EXTRA_FETCHED_AT_MS = "io.r_a_d.geiravor.fetched_at_ms"
    const val EXTRA_DURATION_MS = "io.r_a_d.geiravor.duration_ms"

    data class Fields(
        val title: String,
        val subtitle: String,
        val description: String,
        val artist: String,
        val dj: String,
    )

    fun fields(title: String, artist: String, np: String, dj: String): Fields {
        val t = title.ifBlank { np }
        val a = artist.trim()
        val d = dj.trim()
        return Fields(
            title = t,
            subtitle = a.ifEmpty { d },
            description = if (a.isEmpty()) "" else d,
            artist = a,
            dj = d,
        )
    }

    fun sessionMetadata(
        fields: Fields,
        durationMs: Long,
        artworkUri: android.net.Uri?,
        extras: Bundle,
    ): MediaMetadata =
        MediaMetadata.Builder()
            .setDisplayTitle(fields.title)
            .setTitle(fields.title)
            .setArtist(fields.subtitle)
            .setDescription(fields.description)
            .setAlbumArtist(fields.dj)
            .setDurationMs(durationMs)
            .setArtworkUri(artworkUri)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setExtras(extras)
            .build()

    fun extras(
        artist: String,
        dj: String,
        elapsedMs: Long = 0L,
        fetchedAtMs: Long = 0L,
        durationMs: Long = C.TIME_UNSET,
    ): Bundle =
        Bundle().apply {
            putString(EXTRA_ARTIST, artist)
            putString(EXTRA_DJ, dj)
            putLong(EXTRA_ELAPSED_MS, elapsedMs)
            putLong(EXTRA_FETCHED_AT_MS, fetchedAtMs)
            putLong(EXTRA_DURATION_MS, durationMs)
        }

    fun songDurationMs(metadata: MediaMetadata): Long {
        val extra = metadata.extras?.getLong(EXTRA_DURATION_MS, C.TIME_UNSET) ?: C.TIME_UNSET
        if (extra != C.TIME_UNSET && extra > 0L) return extra
        return metadata.durationMs ?: C.TIME_UNSET
    }

    fun songPositionMs(
        elapsedMs: Long,
        fetchedAtMs: Long,
        durationMs: Long,
        nowMs: Long,
    ): Long {
        if (isLive(durationMs)) return 0L
        val pos = if (fetchedAtMs <= 0L) elapsedMs else elapsedMs + (nowMs - fetchedAtMs)
        return pos.coerceIn(0L, durationMs)
    }

    fun songPositionMs(metadata: MediaMetadata, nowMs: Long): Long {
        val extras = metadata.extras
        return songPositionMs(
            extras?.getLong(EXTRA_ELAPSED_MS, 0L) ?: 0L,
            extras?.getLong(EXTRA_FETCHED_AT_MS, 0L) ?: 0L,
            songDurationMs(metadata),
            nowMs,
        )
    }

    fun rawArtist(metadata: MediaMetadata): String =
        metadata.extras?.getString(EXTRA_ARTIST).orEmpty()

    fun dj(metadata: MediaMetadata): String =
        metadata.extras?.getString(EXTRA_DJ).orEmpty().ifEmpty {
            metadata.albumArtist?.toString().orEmpty()
        }

    fun isLive(durationMs: Long): Boolean =
        durationMs == C.TIME_UNSET || durationMs < 0
}
