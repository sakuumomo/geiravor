package io.r_a_d.geiravor.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import uniffi.geiravor_core.SongProgress
import uniffi.geiravor_core.Status

internal class LiveStationPlayer(
    private val exo: ExoPlayer,
    private val songWindow: () -> SongProgress? = { null },
) : ForwardingPlayer(exo) {
    @Volatile
    private var apiMetadata: MediaMetadata? = null

    fun applyStatus(status: Status?) {
        val meta = SessionMetadata.fromStatus(status)
        apiMetadata = meta
        if (meta != null) {
            exo.setPlaylistMetadata(meta)
        }
    }

    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands().buildUpon()
            .removeAll(
                COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                COMMAND_SEEK_TO_NEXT,
                COMMAND_SEEK_TO_PREVIOUS,
                COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                COMMAND_SEEK_BACK,
                COMMAND_SEEK_FORWARD,
                COMMAND_SEEK_TO_DEFAULT_POSITION,
                COMMAND_SEEK_TO_MEDIA_ITEM,
            )
            .build()
    }

    override fun play() {
        if (LivePlaybackPolicy.onPause() == LivePlaybackPolicy.Action.STOP) {
            ensureLiveItem()
        }
        super.play()
    }

    override fun pause() {
        if (LivePlaybackPolicy.onPause() == LivePlaybackPolicy.Action.STOP) {
            teardown()
        } else {
            super.pause()
        }
    }

    override fun stop() {
        teardown()
    }

    override fun seekTo(positionMs: Long) {
        if (LivePlaybackPolicy.onSeek() == LivePlaybackPolicy.Action.REJECT) {
            return
        }
        super.seekTo(positionMs)
    }

    override fun seekToNext() {
        if (LivePlaybackPolicy.onSkipNext() == LivePlaybackPolicy.Action.REJECT) {
            return
        }
        super.seekToNext()
    }

    override fun seekToPrevious() {
        if (LivePlaybackPolicy.onSkipPrevious() == LivePlaybackPolicy.Action.REJECT) {
            return
        }
        super.seekToPrevious()
    }

    override fun getMediaMetadata(): MediaMetadata {
        return apiMetadata ?: super.getMediaMetadata()
    }

    override fun getDuration(): Long = SessionMetadata.durationMs(songWindow())

    override fun getCurrentPosition(): Long = SessionMetadata.positionMs(songWindow())

    override fun getContentDuration(): Long = duration

    override fun getContentPosition(): Long = currentPosition

    override fun isCurrentMediaItemSeekable(): Boolean = false

    override fun isCurrentMediaItemLive(): Boolean = true

    private fun ensureLiveItem() {
        val idle = exo.playbackState == STATE_IDLE || exo.playbackState == STATE_ENDED
        if (exo.currentMediaItem == null || idle) {
            exo.setMediaItem(MediaItem.fromUri(LivePlaybackPolicy.STREAM_URL))
            exo.prepare()
        }
    }

    private fun teardown() {
        exo.stop()
        exo.clearMediaItems()
    }
}
