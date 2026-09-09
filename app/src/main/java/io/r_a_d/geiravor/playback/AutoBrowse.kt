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
    const val VEHICLE = "autostart-vehicle"
    const val PLUG = "autostart-plug"
    const val ABOUT = "about"

    data class Flags(
        val vehicle: Boolean,
        val plug: Boolean,
        val version: String,
    )

    fun shouldShowQueue(status: Status?): Boolean = status?.isAfk ?: true

    fun isSettingsToggle(mediaId: String): Boolean =
        mediaId == VEHICLE || mediaId == PLUG

    fun isLiveId(mediaId: String): Boolean =
        mediaId == LivePlaybackPolicy.STREAM_URL

    fun onOff(on: Boolean): String = if (on) "On" else "Off"

    fun songsSignature(status: Status?): String {
        if (status == null) return "cold"
        val lp = status.lp.joinToString { "${it.artist}\t${it.title}\t${it.timestamp}" }
        val queue = if (status.isAfk) {
            status.queue.joinToString { "${it.artist}\t${it.title}\t${it.timestamp}" }
        } else {
            "hidden"
        }
        return "${status.isAfk}|$lp|$queue"
    }

    fun children(parentId: String, status: Status?, flags: Flags): ImmutableList<MediaItem> {
        val items = when (parentId) {
            ROOT -> listOf(folder(SONGS, "Songs"), folder(SETTINGS, "Settings"))
            SONGS -> {
                val list = mutableListOf(folder(LAST, "Last Played"))
                if (shouldShowQueue(status)) list += folder(QUEUE, "Queue")
                list
            }
            LAST -> status?.lp.orEmpty().map { row(it.artist, it.title) }
            QUEUE ->
                if (shouldShowQueue(status)) {
                    status?.queue.orEmpty().map { row(it.artist, it.title) }
                } else {
                    emptyList()
                }
            SETTINGS -> listOf(
                toggle(VEHICLE, "Auto-start in vehicle", flags.vehicle),
                toggle(PLUG, "Auto-start on plug", flags.plug),
                about(flags.version),
            )
            ABOUT -> emptyList()
            else -> emptyList()
        }
        return ImmutableList.copyOf(items)
    }

    fun item(mediaId: String, status: Status?, flags: Flags): MediaItem? {
        when (mediaId) {
            SONGS -> return folder(SONGS, "Songs")
            SETTINGS -> return folder(SETTINGS, "Settings")
            LAST -> return folder(LAST, "Last Played")
            QUEUE -> return if (shouldShowQueue(status)) folder(QUEUE, "Queue") else null
            VEHICLE -> return toggle(VEHICLE, "Auto-start in vehicle", flags.vehicle)
            PLUG -> return toggle(PLUG, "Auto-start on plug", flags.plug)
            ABOUT -> return about(flags.version)
        }
        children(LAST, status, flags).find { it.mediaId == mediaId }?.let { return it }
        children(QUEUE, status, flags).find { it.mediaId == mediaId }?.let { return it }
        return null
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

    private fun toggle(id: String, title: String, on: Boolean): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setSubtitle(onOff(on))
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build(),
            )
            .build()

    private fun about(version: String): MediaItem =
        MediaItem.Builder()
            .setMediaId(ABOUT)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("About")
                    .setSubtitle("Geiravor $version")
                    .setIsBrowsable(false)
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
