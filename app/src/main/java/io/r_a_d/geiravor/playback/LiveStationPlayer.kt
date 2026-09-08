package io.r_a_d.geiravor.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/** Pause on a live stream stops: drop the buffer, keep the live item, do not reconnect. */
class LiveStationPlayer(private val exo: ExoPlayer) : ForwardingPlayer(exo) {
    fun playLive() {
        val item = MediaItem.fromUri(LivePlaybackPolicy.STREAM_URL)
        exo.setMediaItem(item)
        exo.prepare()
        exo.playWhenReady = true
    }

    fun pauseStops() {
        exo.stop()
        exo.playWhenReady = false
    }

    override fun pause() {
        pauseStops()
    }

    override fun stop() {
        pauseStops()
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady) playLive() else pauseStops()
    }

    override fun getPlaybackState(): Int =
        LivePlaybackPolicy.reportedPlaybackState(playWhenReady, super.getPlaybackState())

    override fun isPlaying(): Boolean =
        playWhenReady && super.getPlaybackState() == Player.STATE_READY
}
