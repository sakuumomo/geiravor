package io.r_a_d.geiravor.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.google.common.collect.ImmutableList
import uniffi.geiravor_core.Status

/** Auto browse tree: Songs + Settings. Rows are not playable. */
object AutoBrowse {
    const val ROOT = "root"
    const val SONGS = "songs"
    const val SETTINGS = "settings"
    const val LAST = "lp"
    const val QUEUE = "queue"

    fun children(parentId: String, status: Status?): ImmutableList<MediaItem> {
        val items = when (parentId) {
            ROOT -> listOf(folder(SONGS, "Songs"), folder(SETTINGS, "Settings"))
            SONGS -> {
                val list = mutableListOf(folder(LAST, "Last Played"))
                val afk = status?.isAfk ?: true
                if (afk) list += folder(QUEUE, "Queue")
                list
            }
            LAST -> status?.lp.orEmpty().map { row(it.artist, it.title) }
            QUEUE ->
                if (status?.isAfk == true) {
                    status.queue.map { row(it.artist, it.title) }
                } else {
                    emptyList()
                }
            SETTINGS -> listOf(
                folder("about", "About"),
            )
            else -> emptyList()
        }
        return ImmutableList.copyOf(items)
    }

    private fun folder(id: String, title: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build(),
            )
            .build()

    private fun row(artist: String, title: String): MediaItem =
        MediaItem.Builder()
            .setMediaId("row-$artist-$title")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setIsBrowsable(false)
                    .setIsPlayable(false)
                    .build(),
            )
            .build()
}
