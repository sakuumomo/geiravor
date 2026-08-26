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
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.r_a_d.geiravor.BuildConfig
import io.r_a_d.geiravor.GeiravorApp
import io.r_a_d.geiravor.MainActivity
import io.r_a_d.geiravor.radio.RadioStore
import io.r_a_d.geiravor.settings.SettingsPolicy
import io.r_a_d.geiravor.settings.SettingsStore
import uniffi.geiravor_core.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PlaybackService : MediaLibraryService() {
    private var session: MediaLibraryService.MediaLibrarySession? = null
    private val lastBrowse = mutableMapOf<String, List<BrowseNode>>()
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
        val shadeMaxChars = { SessionMetadata.shadeMaxChars(resources) }
        exo.setMediaItem(SessionMetadata.liveMediaItem(radio.snapshot(), shadeMaxChars = shadeMaxChars()))
        val player = LiveStationPlayer(exo, { radio.progress() }, shadeMaxChars)
        player.onWantsPlayback = { radio.setPlaying(it) }
        player.applyStatus(radio.snapshot())
        player.addListener(
            object : Player.Listener {
                override fun onMetadata(metadata: Metadata) {
                    IcyMetadata.titleFrom(metadata)?.let { radio.onIcyTitle(it) }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        radio.onStreamRecovered()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    radio.onStreamError()
                }
            },
        )
        val activity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val settings = SettingsStore(this)
        var vehicleOn = SettingsPolicy.AUTO_START_VEHICLE_DEFAULT
        var plugOn = SettingsPolicy.AUTO_START_DEFAULT
        fun settingsSnapshot() = AutoSettingsSnapshot(
            vehicleOn = vehicleOn,
            plugOn = plugOn,
            versionName = BuildConfig.VERSION_NAME,
        )
        val callback = LibraryCallback(
            status = { radio.snapshot() },
            settings = { settingsSnapshot() },
            player = player,
            onNudgeVolume = { up ->
                val next = LivePlaybackPolicy.nudgeGain(player.volume, up)
                player.volume = next
                scope.launch { settings.setGain(next) }
                session?.setCustomLayout(nowPlayingButtons(next))
            },
            onToggleSetting = { id ->
                when (id) {
                    AutoBrowse.SETTING_VEHICLE -> {
                        vehicleOn = !vehicleOn
                        scope.launch { settings.setAutoStartInVehicle(vehicleOn) }
                    }
                    AutoBrowse.SETTING_PLUG -> {
                        plugOn = !plugOn
                        scope.launch { settings.setAutoStartOnPlug(plugOn) }
                    }
                }
                publishBrowse(radio.snapshot(), settingsSnapshot(), listOf(AutoBrowse.SETTINGS))
            },
        )
        session = MediaLibraryService.MediaLibrarySession.Builder(this, player, callback)
            .setId("geiravor")
            .setSessionActivity(activity)
            .setBitmapLoader(CacheBitmapLoader(DjArtworkLoader(this)))
            .build()
        setShowNotificationForIdlePlayer(SHOW_NOTIFICATION_FOR_IDLE_PLAYER_AFTER_STOP_OR_ERROR)
        scope.launch {
            settings.gain.collect { gain ->
                exo.volume = gain
                session?.setCustomLayout(nowPlayingButtons(gain))
            }
        }
        scope.launch {
            settings.autoStartInVehicle.collect { enabled ->
                vehicleOn = enabled
                publishBrowse(radio.snapshot(), settingsSnapshot(), listOf(AutoBrowse.SETTINGS))
            }
        }
        scope.launch {
            settings.autoStartOnPlug.collect { enabled ->
                plugOn = enabled
                publishBrowse(radio.snapshot(), settingsSnapshot(), listOf(AutoBrowse.SETTINGS))
            }
        }
        scope.launch {
            RadioStore.state.collect { state ->
                player.applyStatus(state.status)
                publishBrowse(
                    state.status,
                    settingsSnapshot(),
                    listOf(
                        AutoBrowse.ROOT,
                        AutoBrowse.SONGS,
                        AutoBrowse.LAST_PLAYED,
                        AutoBrowse.QUEUE,
                    ),
                )
            }
        }
    }

    private fun publishBrowse(
        status: Status?,
        snapshot: AutoSettingsSnapshot,
        parents: List<String>,
    ) {
        val library = session ?: return
        parents.forEach { parent ->
            val kids = AutoBrowse.children(parent, status, snapshot)
            if (lastBrowse[parent] != kids) {
                lastBrowse[parent] = kids
                library.notifyChildrenChanged(parent, kids.size, null)
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
        private val settings: () -> AutoSettingsSnapshot,
        private val player: LiveStationPlayer,
        private val onNudgeVolume: (Boolean) -> Unit,
        private val onToggleSetting: (String) -> Unit,
    ) : MediaLibraryService.MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                .buildUpon()
                .add(SessionCommand(LivePlaybackPolicy.VOLUME_UP, Bundle.EMPTY))
                .add(SessionCommand(LivePlaybackPolicy.VOLUME_DOWN, Bundle.EMPTY))
                .add(SessionCommand(LivePlaybackPolicy.FAVE, Bundle.EMPTY))
                .remove(SessionCommand.COMMAND_CODE_LIBRARY_SEARCH)
                .build()
            return MediaSession.ConnectionResult.accept(sessionCommands, player.availableCommands)
        }

        override fun onPostConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ) {
            session.setCustomLayout(controller, nowPlayingButtons(player.volume))
            if (
                LivePlaybackPolicy.isAutoPackage(controller.packageName) &&
                settings().vehicleOn &&
                !player.wantsPlayback
            ) {
                player.play()
            }
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                LivePlaybackPolicy.VOLUME_UP -> onNudgeVolume(true)
                LivePlaybackPolicy.VOLUME_DOWN -> onNudgeVolume(false)
                LivePlaybackPolicy.FAVE -> {
                    if (LivePlaybackPolicy.faveIsStub()) {
                        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                    }
                }
                else -> return Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED),
                )
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onAddMediaItems(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            return Futures.immediateFailedFuture(
                UnsupportedOperationException("live stream has no playlist"),
            )
        }
        override fun onPlaybackResumption(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            return Futures.immediateFuture(livePlaylist())
        }

        override fun onSetMediaItems(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val toggle = mediaItems.map { it.mediaId }.firstOrNull { AutoBrowse.isSettingsToggle(it) }
            if (toggle != null) {
                onToggleSetting(toggle)
                if (!player.wantsPlayback) {
                    player.ignoreNextPlay()
                }
                return Futures.immediateFuture(currentOrLive(session))
            }
            if (mediaItems.any { AutoBrowse.isReferenceTap(it.mediaId) }) {
                return Futures.immediateFailedFuture(
                    UnsupportedOperationException("reference only"),
                )
            }
            val playLive = mediaItems.any { item ->
                AutoBrowse.allowsPlayback(
                    item.mediaId,
                    item.localConfiguration?.uri?.toString(),
                )
            }
            if (!playLive) {
                return Futures.immediateFailedFuture(
                    UnsupportedOperationException("not the live stream"),
                )
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
            val items = AutoBrowse.children(parentId, status(), settings()).map { it.toMediaItem() }
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
            val snapshot = settings()
            val current = status()
            val node = listOf(
                AutoBrowse.ROOT,
                AutoBrowse.SONGS,
                AutoBrowse.LAST_PLAYED,
                AutoBrowse.QUEUE,
                AutoBrowse.SETTINGS,
            ).asSequence()
                .map { AutoBrowse.children(it, current, snapshot) }
                .flatten()
                .find { it.id == mediaId }
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
            val metadata = MediaMetadata.Builder()
                .setTitle("r/a/dio")
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setExtras(extras)
                .build()
            return MediaItem.Builder()
                .setMediaId(AutoBrowse.ROOT)
                .setMediaMetadata(metadata)
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
            val item = SessionMetadata.liveMediaItem(status())
            return MediaSession.MediaItemsWithStartPosition(listOf(item), 0, C.TIME_UNSET)
        }
    }
}

private fun nowPlayingButtons(gain: Float): List<CommandButton> {
    val label = LivePlaybackPolicy.volumeLabel(gain)
    return listOf(
        CommandButton.Builder(CommandButton.ICON_VOLUME_DOWN)
            .setSessionCommand(SessionCommand(LivePlaybackPolicy.VOLUME_DOWN, Bundle.EMPTY))
            .setDisplayName("Vol $label −")
            .setSlots(CommandButton.SLOT_BACK_SECONDARY, CommandButton.SLOT_OVERFLOW)
            .build(),
        CommandButton.Builder(CommandButton.ICON_VOLUME_UP)
            .setSessionCommand(SessionCommand(LivePlaybackPolicy.VOLUME_UP, Bundle.EMPTY))
            .setDisplayName("Vol $label +")
            .setSlots(CommandButton.SLOT_FORWARD_SECONDARY, CommandButton.SLOT_OVERFLOW)
            .build(),
        CommandButton.Builder(CommandButton.ICON_HEART_UNFILLED)
            .setSessionCommand(SessionCommand(LivePlaybackPolicy.FAVE, Bundle.EMPTY))
            .setDisplayName("Fave")
            .setSlots(CommandButton.SLOT_OVERFLOW)
            .build(),
    )
}

private fun BrowseNode.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setTitle(title)
        .setIsPlayable(playable)
        .setIsBrowsable(browsable)
    subtitle?.let { metadata.setSubtitle(it) }
    if (browsable && !playable) {
        metadata.setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
    }
    val built = metadata.build()
    val builder = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(built)
    if (playable) {
        if (AutoBrowse.isSettingsToggle(id)) {
            builder.setUri(android.net.Uri.parse("content://io.r_a_d.geiravor/settings/$id"))
        } else {
            builder.setUri(LivePlaybackPolicy.STREAM_URL)
        }
    }
    return builder.build()
}
