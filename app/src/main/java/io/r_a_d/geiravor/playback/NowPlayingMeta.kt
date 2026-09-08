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

    fun extras(artist: String, dj: String): Bundle =
        Bundle().apply {
            putString(EXTRA_ARTIST, artist)
            putString(EXTRA_DJ, dj)
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
