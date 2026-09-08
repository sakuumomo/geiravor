package io.r_a_d.geiravor.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/** Pause on a live stream stops: drop the buffer, keep the live item, do not reconnect. */
class LiveStationPlayer(private val player: ExoPlayer) {
    fun playLive() {
        val item = MediaItem.fromUri(LivePlaybackPolicy.STREAM_URL)
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
    }

    fun pauseStops() {
        player.stop()
        player.playWhenReady = false
    }

    fun isPlaying(): Boolean = player.playbackState == Player.STATE_READY && player.playWhenReady
}
