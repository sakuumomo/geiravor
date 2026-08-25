package io.r_a_d.geiravor.playback

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.ForwardingPlayer

internal class LiveStationPlayer(
    private val exo: ExoPlayer,
) : ForwardingPlayer(exo) {
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
