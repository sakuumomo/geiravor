package io.r_a_d.geiravor.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.r_a_d.geiravor.BuildConfig
import io.r_a_d.geiravor.GeiravorApp
import io.r_a_d.geiravor.MainActivity
import io.r_a_d.geiravor.radio.RadioStore
import io.r_a_d.geiravor.settings.SettingsStore
import uniffi.geiravor_core.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PlaybackService : MediaLibraryService() {
    private var session: MediaLibraryService.MediaLibrarySession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val app = application as GeiravorApp
        val radio = app.radio
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent("Geiravor/${BuildConfig.VERSION_NAME}")
            .setAllowCrossProtocolRedirects(true)
        val sources = ProgressiveMediaSource.Factory(http)
        val exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(sources)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        exo.volume = LivePlaybackPolicy.DEFAULT_GAIN
        exo.setMediaItem(
            MediaItem.Builder()
                .setUri(LivePlaybackPolicy.STREAM_URL)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("r/a/dio")
                        .setArtist("r/a/dio")
                        .build(),
                )
                .build(),
        )
        val player = LiveStationPlayer(exo) { radio.progress() }
        player.onWantsPlayback = { radio.setPlaying(it) }
        player.applyStatus(radio.snapshot())
        player.addListener(
            object : Player.Listener {
                override fun onMetadata(metadata: Metadata) {
                    IcyMetadata.titleFrom(metadata)?.let { radio.onIcyTitle(it) }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (!LivePlaybackPolicy.shouldReconnect(player.wantsPlayback)) {
                        radio.onStreamError()
                    }
                }
            },
        )
        val activity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaLibraryService.MediaLibrarySession.Builder(
            this,
            player,
            LibraryCallback({ radio.snapshot() }, player),
        )
            .setId("geiravor")
            .setSessionActivity(activity)
            .build()
        setShowNotificationForIdlePlayer(
            if (LivePlaybackPolicy.keepNotificationAfterStop()) {
                SHOW_NOTIFICATION_FOR_IDLE_PLAYER_AFTER_STOP_OR_ERROR
            } else {
                SHOW_NOTIFICATION_FOR_IDLE_PLAYER_NEVER
            },
        )
        val settings = SettingsStore(this)
        scope.launch {
            settings.gain.collect { exo.volume = it }
        }
        scope.launch {
            RadioStore.state.collect { state ->
                player.applyStatus(state.status)
                val library = session ?: return@collect
                AutoBrowse.children(AutoBrowse.ROOT, state.status).let { nodes ->
                    library.notifyChildrenChanged(AutoBrowse.ROOT, nodes.size, null)
                }
                library.notifyChildrenChanged(
                    AutoBrowse.LAST_PLAYED,
                    AutoBrowse.children(AutoBrowse.LAST_PLAYED, state.status).size,
                    null,
                )
                library.notifyChildrenChanged(
                    AutoBrowse.QUEUE,
                    AutoBrowse.children(AutoBrowse.QUEUE, state.status).size,
                    null,
                )
            }
        }
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo,
    ): MediaLibraryService.MediaLibrarySession? {
        return session
    }

    override fun onDestroy() {
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private class LibraryCallback(
        private val status: () -> Status?,
        private val player: LiveStationPlayer,
    ) : MediaLibraryService.MediaLibrarySession.Callback {
        override fun onPlaybackResumption(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            player.ignoreNextPlay()
            return Futures.immediateFuture(livePlaylist())
        }

        override fun onSetMediaItems(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val playLive = mediaItems.any { item ->
                AutoBrowse.allowsPlayback(
                    item.mediaId,
                    item.localConfiguration?.uri?.toString(),
                )
            }
            if (!playLive) {
                player.ignoreNextPlay()
                return Futures.immediateFuture(currentOrLive(session))
            }
            if (!player.wantsPlayback) {
                player.ignoreNextPlay()
            }
            return Futures.immediateFuture(livePlaylist())
        }

        override fun onGetLibraryRoot(
            session: MediaLibraryService.MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(LibraryResult.ofItem(libraryRoot(), params))
        }

        override fun onGetChildren(
            session: MediaLibraryService.MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val items = AutoBrowse.children(parentId, status()).map { it.toMediaItem() }
            return Futures.immediateFuture(LibraryResult.ofItemList(items, params))
        }

        override fun onGetItem(
            session: MediaLibraryService.MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            if (mediaId == AutoBrowse.ROOT) {
                return Futures.immediateFuture(LibraryResult.ofItem(libraryRoot(), null))
            }
            val node = AutoBrowse.children(AutoBrowse.ROOT, status()).find { it.id == mediaId }
                ?: AutoBrowse.children(AutoBrowse.LAST_PLAYED, status()).find { it.id == mediaId }
                ?: AutoBrowse.children(AutoBrowse.QUEUE, status()).find { it.id == mediaId }
            if (node == null) {
                return Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            }
            return Futures.immediateFuture(LibraryResult.ofItem(node.toMediaItem(), null))
        }

        private fun libraryRoot(): MediaItem {
            val extras = Bundle().apply {
                putInt(
                    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                    MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
                )
                putInt(
                    MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                    MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM,
                )
            }
            val card = SessionMetadata.card(status())
            val metadata = MediaMetadata.Builder()
                .setTitle(card?.title ?: "r/a/dio")
                .setArtist(card?.artist)
                .setAlbumArtist(card?.albumArtist)
                .setIsBrowsable(true)
                .setIsPlayable(true)
                .setExtras(extras)
            card?.subtitle?.let { metadata.setSubtitle(it) }
            card?.artworkUrl?.let { metadata.setArtworkUri(android.net.Uri.parse(it)) }
            return MediaItem.Builder()
                .setMediaId(AutoBrowse.ROOT)
                .setUri(LivePlaybackPolicy.STREAM_URL)
                .setMediaMetadata(metadata.build())
                .build()
        }

        private fun currentOrLive(
            session: MediaSession,
        ): MediaSession.MediaItemsWithStartPosition {
            val current = session.player.currentMediaItem
            if (current != null) {
                return MediaSession.MediaItemsWithStartPosition(
                    listOf(current),
                    0,
                    C.TIME_UNSET,
                )
            }
            return livePlaylist()
        }

        private fun livePlaylist(): MediaSession.MediaItemsWithStartPosition {
            val item = MediaItem.Builder()
                .setMediaId(AutoBrowse.NOW_PLAYING)
                .setUri(LivePlaybackPolicy.STREAM_URL)
                .build()
            return MediaSession.MediaItemsWithStartPosition(listOf(item), 0, C.TIME_UNSET)
        }
    }
}

private fun BrowseNode.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setIsPlayable(playable)
        .setIsBrowsable(browsable)
    if (browsable && !playable) {
        metadata.setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
    }
    val built = metadata.build()
    val builder = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(built)
    if (playable) {
        builder.setUri(LivePlaybackPolicy.STREAM_URL)
    }
    return builder.build()
}
