package io.r_a_d.geiravor.playback

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/** Pause on a live stream stops: drop the buffer, keep the live item, do not reconnect. */
class LiveStationPlayer(private val exo: ExoPlayer) : ForwardingPlayer(exo) {
    /** One-shot: swallow Auto connect/resume or a settings-tap Play. Not a timer. */
    var skipNextPlay = false

    fun playLive() {
        skipNextPlay = false
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

    override fun play() {
        if (takeSkip()) return
        playLive()
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady && takeSkip()) return
        if (playWhenReady) playLive() else pauseStops()
    }

    override fun setMediaItem(mediaItem: MediaItem) {
        if (!shouldApplyMediaItem(mediaItem)) return
        super.setMediaItem(mediaItem)
    }

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
        if (!shouldApplyMediaItem(mediaItem)) return
        super.setMediaItem(mediaItem, resetPosition)
    }

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
        if (!shouldApplyMediaItem(mediaItem)) return
        super.setMediaItem(mediaItem, startPositionMs)
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>) {
        val first = mediaItems.firstOrNull() ?: return
        if (!shouldApplyMediaItem(first)) return
        super.setMediaItems(mediaItems)
    }

    override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
        val first = mediaItems.firstOrNull() ?: return
        if (!shouldApplyMediaItem(first)) return
        super.setMediaItems(mediaItems, resetPosition)
    }

    override fun setMediaItems(
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ) {
        val first = mediaItems.firstOrNull() ?: return
        if (!shouldApplyMediaItem(first)) return
        super.setMediaItems(mediaItems, startIndex, startPositionMs)
    }

    override fun getPlaybackState(): Int =
        LivePlaybackPolicy.reportedPlaybackState(playWhenReady, super.getPlaybackState())

    override fun isPlaying(): Boolean =
        playWhenReady && super.getPlaybackState() == Player.STATE_READY

    private fun takeSkip(): Boolean {
        if (!skipNextPlay) return false
        skipNextPlay = false
        return true
    }

    private fun shouldApplyMediaItem(item: MediaItem): Boolean {
        if (AutoBrowse.isSettingsToggle(item.mediaId) || item.mediaId == AutoBrowse.ABOUT) {
            return false
        }
        val uri = item.localConfiguration?.uri?.toString()
        val live = uri == LivePlaybackPolicy.STREAM_URL ||
            item.mediaId == LivePlaybackPolicy.STREAM_URL
        return !(live && exo.isPlaying)
    }
}
