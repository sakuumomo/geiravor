package io.r_a_d.geiravor.playback

import android.app.PendingIntent
import android.content.Intent
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
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.r_a_d.geiravor.BuildConfig
import io.r_a_d.geiravor.GeiravorApp
import io.r_a_d.geiravor.MainActivity
import io.r_a_d.geiravor.radio.RadioStore
import io.r_a_d.geiravor.settings.SettingsStore
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

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    radio.setPlaying(player.wantsPlayback)
                }
            },
        )
        val activity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaLibraryService.MediaLibrarySession.Builder(this, player, LibraryCallback())
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

    private class LibraryCallback : MediaLibraryService.MediaLibrarySession.Callback {
        override fun onPlaybackResumption(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val item = MediaItem.fromUri(LivePlaybackPolicy.STREAM_URL)
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(listOf(item), 0, C.TIME_UNSET),
            )
        }

        override fun onGetLibraryRoot(
            session: MediaLibraryService.MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = MediaItem.Builder()
                .setMediaId("root")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build(),
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }
    }
}
