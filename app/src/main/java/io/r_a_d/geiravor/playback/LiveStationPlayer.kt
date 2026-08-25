package io.r_a_d.geiravor.playback

import android.os.Handler
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
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

    @Volatile
    var wantsPlayback: Boolean = false
        private set

    var onWantsPlayback: ((Boolean) -> Unit)? = null

    private val mainHandler = Handler(exo.applicationLooper)
    private val reconnect = Runnable {
        if (!LivePlaybackPolicy.shouldReconnect(wantsPlayback)) {
            return@Runnable
        }
        play()
    }

    fun applyStatus(status: Status?) {
        val meta = SessionMetadata.fromStatus(status)
        apiMetadata = meta
        if (meta != null) {
            exo.setPlaylistMetadata(meta)
        }
    }

    init {
        exo.addListener(
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    scheduleReconnect()
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == STATE_ENDED) {
                        scheduleReconnect()
                    }
                }
            },
        )
    }

    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands().buildUpon()
            .addAll(COMMAND_PLAY_PAUSE, COMMAND_PREPARE, COMMAND_STOP)
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
        setWantsPlayback(true)
        ensureLiveItem()
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

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (!playWhenReady && LivePlaybackPolicy.onPause() == LivePlaybackPolicy.Action.STOP) {
            teardown()
            return
        }
        if (playWhenReady) {
            setWantsPlayback(true)
            ensureLiveItem()
        }
        super.setPlayWhenReady(playWhenReady)
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

    private fun liveItem(): MediaItem = MediaItem.fromUri(LivePlaybackPolicy.STREAM_URL)

    private fun ensureLiveItem() {
        val idle = exo.playbackState == STATE_IDLE || exo.playbackState == STATE_ENDED
        if (exo.currentMediaItem == null || idle) {
            exo.setMediaItem(liveItem())
            exo.prepare()
        }
    }

    private fun scheduleReconnect() {
        if (!LivePlaybackPolicy.shouldReconnect(wantsPlayback)) {
            return
        }
        mainHandler.removeCallbacks(reconnect)
        mainHandler.postDelayed(reconnect, LivePlaybackPolicy.reconnectDelayMs())
    }

    private fun setWantsPlayback(value: Boolean) {
        if (wantsPlayback == value) {
            return
        }
        wantsPlayback = value
        onWantsPlayback?.invoke(value)
    }

    private fun teardown() {
        setWantsPlayback(false)
        mainHandler.removeCallbacks(reconnect)
        exo.playWhenReady = false
        exo.stop()
        exo.clearMediaItems()
        if (LivePlaybackPolicy.leaveUnpreparedLiveItemAfterStop()) {
            exo.setMediaItem(liveItem())
        }
    }
}
