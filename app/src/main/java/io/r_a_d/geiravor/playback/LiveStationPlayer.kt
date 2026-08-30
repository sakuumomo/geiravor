package io.r_a_d.geiravor.playback

import android.os.Handler
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.util.IdentityHashMap
import uniffi.geiravor_core.SongProgress
import uniffi.geiravor_core.Status

internal class LiveStationPlayer(
    private val exo: ExoPlayer,
    private val songWindow: () -> SongProgress? = { null },
    private val shadeSpace: () -> SessionMetadata.ShadeSpace? = { null },
) : ForwardingPlayer(exo) {
    @Volatile
    private var apiMetadata: MediaMetadata? = null

    @Volatile
    var wantsPlayback: Boolean = false
        private set

    var onWantsPlayback: ((Boolean) -> Unit)? = null

    private var userGain: Float = LivePlaybackPolicy.DEFAULT_GAIN

    var sleepFade: Float = 1f
        set(value) {
            field = value.coerceIn(0f, 1f)
            exo.volume = userGain * field
        }

    private val mainHandler = Handler(exo.applicationLooper)
    private val clearIgnorePlay = Runnable { ignorePlay = false }
    private var ignorePlay = false
    private var holdAsPaused = false
    private var tearingDown = false
    private val listeners = IdentityHashMap<Player.Listener, Player.Listener>()
    private val reconnect = Runnable {
        if (!LivePlaybackPolicy.shouldReconnect(wantsPlayback)) {
            return@Runnable
        }
        play()
    }

    fun applyStatus(status: Status?) {
        val meta = SessionMetadata.fromStatus(
            status,
            shadeSpace(),
            SessionMetadata.durationMs(songWindow()),
        )
        val previous = apiMetadata
        apiMetadata = meta
        if (meta != null) {
            exo.setPlaylistMetadata(meta)
        }
        if (meta != null && meta != previous) {
            listeners.values.forEach { listener ->
                listener.onMediaMetadataChanged(meta)
            }
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

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (!playWhenReady && !tearingDown) {
                        teardown()
                    }
                }
            },
        )
    }

    override fun getAvailableCommands(): Player.Commands {
        val commands = super.getAvailableCommands().buildUpon()
            .addAll(
                COMMAND_PLAY_PAUSE,
                COMMAND_PREPARE,
                COMMAND_STOP,
                COMMAND_SET_MEDIA_ITEM,
                COMMAND_GET_VOLUME,
                COMMAND_SET_VOLUME,
            )
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
            .remove(COMMAND_GET_TIMELINE)
            .remove(COMMAND_CHANGE_MEDIA_ITEMS)
        return commands.build()
    }

    override fun isCommandAvailable(command: Int): Boolean =
        getAvailableCommands().contains(command)

    override fun getVolume(): Float = userGain

    override fun setVolume(volume: Float) {
        userGain = volume.coerceIn(0f, 1f)
        exo.volume = userGain * sleepFade
    }

    override fun addListener(listener: Player.Listener) {
        val wrapped = HeldPausedListener(listener)
        listeners[listener] = wrapped
        super.addListener(wrapped)
    }

    override fun removeListener(listener: Player.Listener) {
        super.removeListener(listeners.remove(listener) ?: listener)
    }

    override fun getPlaybackState(): Int = sessionState().state

    override fun getPlayWhenReady(): Boolean = sessionState().playWhenReady

    override fun isPlaying(): Boolean =
        LivePlaybackPolicy.showAsPlaying(
            playbackState = getPlaybackState(),
            isPlaying = super.isPlaying(),
            playWhenReady = getPlayWhenReady(),
        )

    fun ignoreNextPlay() {
        ignorePlay = true
        mainHandler.removeCallbacks(clearIgnorePlay)
        mainHandler.postDelayed(clearIgnorePlay, 750)
    }

    override fun play() {
        if (consumeIgnorePlay()) {
            return
        }
        holdAsPaused = false
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
            if (consumeIgnorePlay()) {
                return
            }
            holdAsPaused = false
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
        return SessionMetadata.published(apiMetadata, super.getMediaMetadata())
    }

    override fun getCurrentMediaItem(): MediaItem? {
        val current = super.getCurrentMediaItem() ?: return null
        return SessionMetadata.replaceLiveMetadata(current, apiMetadata) ?: current
    }

    override fun getDuration(): Long = SessionMetadata.durationMs(songWindow())

    override fun getCurrentPosition(): Long = SessionMetadata.positionMs(songWindow())

    override fun getContentDuration(): Long = duration

    override fun getContentPosition(): Long = currentPosition

    override fun getBufferedPosition(): Long =
        LivePlaybackPolicy.songBufferedPositionMs(currentPosition)

    override fun getContentBufferedPosition(): Long = bufferedPosition

    override fun getTotalBufferedDuration(): Long = 0L

    override fun isCurrentMediaItemSeekable(): Boolean = false

    override fun isCurrentMediaItemLive(): Boolean =
        LivePlaybackPolicy.isLiveBroadcast(duration)

    override fun setMediaItem(mediaItem: MediaItem) {
        if (skipRedundant(listOf(mediaItem))) {
            return
        }
        super.setMediaItem(mediaItem)
    }

    override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {
        if (skipRedundant(listOf(mediaItem))) {
            return
        }
        super.setMediaItem(mediaItem, resetPosition)
    }

    override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
        if (skipRedundant(listOf(mediaItem))) {
            return
        }
        super.setMediaItem(mediaItem, startPositionMs)
    }

    override fun setMediaItems(mediaItems: List<MediaItem>) {
        if (skipRedundant(mediaItems)) {
            return
        }
        super.setMediaItems(mediaItems)
    }

    override fun setMediaItems(mediaItems: List<MediaItem>, resetPosition: Boolean) {
        if (skipRedundant(mediaItems)) {
            return
        }
        super.setMediaItems(mediaItems, resetPosition)
    }

    override fun setMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ) {
        if (skipRedundant(mediaItems)) {
            return
        }
        super.setMediaItems(mediaItems, startIndex, startPositionMs)
    }

    override fun replaceMediaItem(index: Int, mediaItem: MediaItem) {
        if (skipRedundant(listOf(mediaItem), metadataOnly = true)) {
            return
        }
        super.replaceMediaItem(index, mediaItem)
    }

    override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: List<MediaItem>) {
        if (skipRedundant(mediaItems, metadataOnly = true)) {
            return
        }
        super.replaceMediaItems(fromIndex, toIndex, mediaItems)
    }

    private fun liveItem(): MediaItem = SessionMetadata.liveMediaItem(apiMetadata)

    private fun skipRedundant(items: List<MediaItem>, metadataOnly: Boolean = false): Boolean {
        val current = exo.currentMediaItem
        val activelyPlaying = exo.playWhenReady &&
            (exo.playbackState == STATE_READY || exo.playbackState == STATE_BUFFERING)
        return AutoBrowse.skipRedundantLiveSet(
            currentMediaId = current?.mediaId,
            currentUri = current?.localConfiguration?.uri?.toString(),
            incoming = items.map { it.mediaId to it.localConfiguration?.uri?.toString() },
            activelyPlaying = activelyPlaying,
            metadataOnly = metadataOnly,
        )
    }

    private fun ensureLiveItem() {
        val running = exo.playWhenReady &&
            (exo.playbackState == STATE_READY || exo.playbackState == STATE_BUFFERING)
        if (!running || exo.currentMediaItem == null) {
            exo.setMediaItem(liveItem(), /* resetPosition = */ true)
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

    private fun consumeIgnorePlay(): Boolean {
        if (!ignorePlay) {
            return false
        }
        ignorePlay = false
        mainHandler.removeCallbacks(clearIgnorePlay)
        return true
    }

    private fun setWantsPlayback(value: Boolean) {
        if (wantsPlayback == value) {
            return
        }
        wantsPlayback = value
        onWantsPlayback?.invoke(value)
    }

    private fun teardown() {
        if (tearingDown) {
            return
        }
        tearingDown = true
        try {
            setWantsPlayback(false)
            mainHandler.removeCallbacks(reconnect)
            ignorePlay = false
            mainHandler.removeCallbacks(clearIgnorePlay)
            holdAsPaused = true
            exo.playWhenReady = false
            exo.stop()
            exo.setMediaItem(liveItem(), /* resetPosition = */ true)
        } finally {
            tearingDown = false
        }
    }

    private fun sessionState(): LivePlaybackPolicy.SessionPlaybackState =
        LivePlaybackPolicy.sessionPlaybackState(
            playbackState = super.getPlaybackState(),
            playWhenReady = super.getPlayWhenReady(),
            wantsPlayback = wantsPlayback,
            hasLiveItem = exo.currentMediaItem != null,
            holdAsPaused = holdAsPaused,
        )

    private inner class HeldPausedListener(
        private val delegate: Player.Listener,
    ) : Player.Listener by delegate {
        override fun onPlaybackStateChanged(playbackState: Int) {
            delegate.onPlaybackStateChanged(getPlaybackState())
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            delegate.onPlayWhenReadyChanged(getPlayWhenReady(), reason)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            delegate.onIsPlayingChanged(isPlaying())
        }

        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
            delegate.onAvailableCommandsChanged(getAvailableCommands())
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            delegate.onMediaMetadataChanged(SessionMetadata.published(apiMetadata, mediaMetadata))
        }
    }
}
