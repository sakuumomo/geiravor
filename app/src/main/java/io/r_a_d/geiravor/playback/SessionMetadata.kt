package io.r_a_d.geiravor.playback

import android.net.Uri
import androidx.media3.common.C
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
    fun card(status: Status?): NowPlayingCard? {
        if (status == null) return null
        return NowPlayingCard(
            title = status.title,
            artist = status.artist,
            albumArtist = status.dj.name,
            artworkUrl = "https://r-a-d.io/api/dj-image/${status.dj.image}",
        )
    }

    fun fromStatus(status: Status?): MediaMetadata? {
        val card = card(status) ?: return null
        return MediaMetadata.Builder()
            .setTitle(card.title)
            .setArtist(card.artist)
            .setAlbumArtist(card.albumArtist)
            .setArtworkUri(Uri.parse(card.artworkUrl))
            .build()
    }

    fun positionMs(progress: SongProgress?): Long =
        (progress?.elapsedSecs ?: 0).coerceAtLeast(0) * 1000

    fun durationMs(progress: SongProgress?): Long {
        val secs = progress?.durationSecs ?: return C.TIME_UNSET
        return secs.coerceAtLeast(0) * 1000
    }
}
