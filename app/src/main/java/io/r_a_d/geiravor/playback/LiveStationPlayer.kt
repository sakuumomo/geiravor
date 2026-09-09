package io.r_a_d.geiravor.playback

import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.util.IdentityHashMap

/** Pause on a live stream stops: drop the buffer, keep the live item, do not reconnect. */
class LiveStationPlayer(private val exo: ExoPlayer) : ForwardingPlayer(exo) {
    /** One-shot: swallow Auto connect/resume or a settings-tap Play. Not a timer. */
    var skipNextPlay = false
    private val listeners = IdentityHashMap<Player.Listener, Player.Listener>()
    @Volatile
    private var sessionMeta: MediaMetadata? = null
    @Volatile
    private var progressElapsedMs = 0L
    @Volatile
    private var progressDurationMs = C.TIME_UNSET
    @Volatile
    private var progressAtMs = 0L

    fun playLive() {
        skipNextPlay = false
        if (!LivePlaybackPolicy.shouldRestartLive(exo.playWhenReady, exo.playbackState)) {
            return
        }
        if (LivePlaybackPolicy.shouldSetMediaItemOnPlay(currentIsLive())) {
            exo.setMediaItem(LivePlaybackPolicy.liveItem())
        }
        exo.prepare()
        exo.playWhenReady = true
    }

    fun pauseStops() {
        exo.playWhenReady = false
        exo.stop()
    }

    /** /api paints the live card in place. Do not replace the Icecast item. */
    fun publishMetadata(meta: MediaMetadata, elapsedMs: Long, durationMs: Long) {
        progressElapsedMs = elapsedMs
        progressDurationMs = durationMs
        progressAtMs = System.currentTimeMillis()
        if (sessionMeta == meta) return
        sessionMeta = meta
        exo.setPlaylistMetadata(meta)
        listeners.values.forEach { it.onMediaMetadataChanged(meta) }
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

    override fun prepare() {
        if (!LivePlaybackPolicy.shouldPrepare(exo.playWhenReady, exo.playbackState)) return
        super.prepare()
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

    override fun addListener(listener: Player.Listener) {
        val wrapped = object : Player.Listener by listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                listener.onPlaybackStateChanged(getPlaybackState())
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                listener.onIsPlayingChanged(isPlaying())
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                listener.onPlayWhenReadyChanged(getPlayWhenReady(), reason)
            }

            override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
                listener.onAvailableCommandsChanged(getAvailableCommands())
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                listener.onMediaMetadataChanged(sessionMeta ?: mediaMetadata)
            }
        }
        listeners[listener] = wrapped
        super.addListener(wrapped)
    }

    override fun removeListener(listener: Player.Listener) {
        super.removeListener(listeners.remove(listener) ?: listener)
    }

    override fun getAvailableCommands(): Player.Commands =
        LivePlaybackPolicy.availableCommands(super.getAvailableCommands())

    override fun isCommandAvailable(command: Int): Boolean =
        getAvailableCommands().contains(command)

    override fun getPlaybackState(): Int =
        LivePlaybackPolicy.reportedPlaybackState(playWhenReady, super.getPlaybackState())

    override fun isPlaying(): Boolean =
        playWhenReady && super.getPlaybackState() == Player.STATE_READY

    override fun getMediaMetadata(): MediaMetadata = sessionMeta ?: super.getMediaMetadata()

    override fun getCurrentMediaItem(): MediaItem? {
        val current = super.getCurrentMediaItem() ?: return null
        val meta = sessionMeta ?: return current
        if (current.mediaMetadata == meta) return current
        return current.buildUpon().setMediaMetadata(meta).build()
    }

    override fun isCurrentMediaItemLive(): Boolean =
        NowPlayingMeta.isLive(duration)

    override fun getDuration(): Long =
        if (progressDurationMs != C.TIME_UNSET && progressDurationMs > 0L) {
            progressDurationMs
        } else {
            NowPlayingMeta.songDurationMs(mediaMetadata)
        }

    override fun getCurrentPosition(): Long =
        NowPlayingMeta.songPositionMs(
            progressElapsedMs,
            progressAtMs,
            duration,
            System.currentTimeMillis(),
        )

    override fun getContentDuration(): Long = duration

    override fun getContentPosition(): Long = currentPosition

    override fun getBufferedPosition(): Long =
        LivePlaybackPolicy.songBufferedPositionMs(currentPosition)

    override fun getContentBufferedPosition(): Long = bufferedPosition

    override fun getTotalBufferedDuration(): Long = LivePlaybackPolicy.totalBufferedDurationMs()

    override fun getBufferedPercentage(): Int =
        LivePlaybackPolicy.songBufferedPercentage(currentPosition, duration)

    override fun isCurrentMediaItemSeekable(): Boolean = LivePlaybackPolicy.isSeekable()

    override fun seekTo(positionMs: Long) {}

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {}

    override fun seekToDefaultPosition() {}

    override fun seekToDefaultPosition(mediaItemIndex: Int) {}

    override fun seekToNext() {}

    override fun seekToPrevious() {}

    override fun seekToNextMediaItem() {}

    override fun seekToPreviousMediaItem() {}

    override fun seekBack() {}

    override fun seekForward() {}

    private fun takeSkip(): Boolean {
        if (!skipNextPlay) return false
        skipNextPlay = false
        return true
    }

    private fun shouldApplyMediaItem(item: MediaItem): Boolean {
        if (AutoBrowse.isFunctionItem(item.mediaId)) return false
        val uri = item.localConfiguration?.uri?.toString()
        if (!AutoBrowse.isLiveStream(item.mediaId, uri)) return false
        return LivePlaybackPolicy.shouldApplyLiveMediaItem(
            exo.playWhenReady,
            currentIsLive(),
        )
    }

    private fun currentIsLive(): Boolean {
        val item = exo.currentMediaItem ?: return false
        return AutoBrowse.isLiveStream(item.mediaId, item.localConfiguration?.uri?.toString())
    }
}
