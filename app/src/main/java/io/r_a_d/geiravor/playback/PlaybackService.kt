package io.r_a_d.geiravor.playback

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import android.os.Handler
import android.os.Looper
import io.r_a_d.geiravor.GeiravorApp
import uniffi.geiravor_core.FaveConfig
import uniffi.geiravor_core.IrcProfile
import uniffi.geiravor_core.Status
import uniffi.geiravor_core.StatusListener

@UnstableApi
class PlaybackService : MediaLibraryService() {
    private lateinit var player: ExoPlayer
    private lateinit var live: LiveStationPlayer
    private var session: MediaLibrarySession? = null
    private var lastGain = LivePlaybackPolicy.DEFAULT_GAIN

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
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
        lastGain = (application as GeiravorApp).ui.gain
        player.volume = lastGain
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                core().setPlaying(isPlaying)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                core().setPlayerError()
            }
        })
        live = LiveStationPlayer(player)
        ensureLiveItem()
        session = MediaLibrarySession.Builder(this, player, Callbacks())
            .setId("geiravor")
            .build()
        core().addListener(Listener())
        core().snapshot()?.let { applyStatus(it) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> live.playLive()
            ACTION_STOP -> live.pauseStops()
            ACTION_GAIN -> {
                val g = intent.getFloatExtra(EXTRA_GAIN, lastGain).coerceIn(0f, 1f)
                lastGain = g
                player.volume = g
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        session?.release()
        player.release()
        super.onDestroy()
    }

    private fun core() = (application as GeiravorApp).core

    private fun ensureLiveItem() {
        if (player.mediaItemCount == 0) {
            player.setMediaItem(MediaItem.fromUri(LivePlaybackPolicy.STREAM_URL))
        }
    }

    private fun applyStatus(status: Status) {
        val item = player.currentMediaItem ?: return
        val meta = MediaMetadata.Builder()
            .setTitle(status.title.ifBlank { status.np })
            .setArtist(status.artist)
            .setAlbumArtist(status.dj.name)
            .setArtworkUri(
                LivePlaybackPolicy.djImageUrl(status.dj.image)?.let { android.net.Uri.parse(it) },
            )
            .build()
        player.replaceMediaItem(
            0,
            item.buildUpon().setMediaMetadata(meta).build(),
        )
    }

    private inner class Listener : StatusListener {
        override fun onStatus(status: Status, streamDown: Boolean, playing: Boolean) {
            Handler(Looper.getMainLooper()).post { applyStatus(status) }
        }
    }

    private inner class Callbacks : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = LivePlaybackPolicy.sessionCommands()
            val playerCommands = LivePlaybackPolicy.playerCommands()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                LivePlaybackPolicy.FAVE -> {
                    Thread {
                        core().addFave(
                            FaveConfig(
                                nick = "",
                                listNick = "",
                                profile = IrcProfile.RIZON,
                                nickservPassword = "",
                                bouncerHost = "",
                                bouncerPort = 6697.toUShort(),
                                bouncerPass = "",
                                allowInsecureTls = false,
                                saslUsername = "",
                                saslPassword = "",
                                clientCertPem = "",
                                clientKeyPem = "",
                                tlsFingerprint = "",
                            ),
                            false,
                            0,
                        )
                    }.start()
                }
                LivePlaybackPolicy.MUTE -> {
                    if (player.volume > 0f) {
                        lastGain = player.volume
                        player.volume = 0f
                    } else {
                        player.volume = lastGain.coerceAtLeast(LivePlaybackPolicy.DEFAULT_GAIN)
                    }
                }
                LivePlaybackPolicy.VOL_UP ->
                    player.volume = (player.volume + 0.05f).coerceAtMost(1f)
                LivePlaybackPolicy.VOL_DOWN ->
                    player.volume = (player.volume - 0.05f).coerceAtLeast(0f)
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
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

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val kids = AutoBrowse.children(parentId, core().snapshot())
            return Futures.immediateFuture(LibraryResult.ofItemList(kids, params))
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            ensureLiveItem()
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    listOf(player.currentMediaItem ?: MediaItem.fromUri(LivePlaybackPolicy.STREAM_URL)),
                    0,
                    C.TIME_UNSET,
                ),
            )
        }
    }

    companion object {
        const val ACTION_PLAY = "io.r_a_d.geiravor.PLAY"
        const val ACTION_STOP = "io.r_a_d.geiravor.STOP"
        const val ACTION_GAIN = "io.r_a_d.geiravor.GAIN"
        const val EXTRA_GAIN = "gain"

        fun playIntent(context: Context): Intent =
            Intent(context, PlaybackService::class.java).setAction(ACTION_PLAY)

        fun stopIntent(context: Context): Intent =
            Intent(context, PlaybackService::class.java).setAction(ACTION_STOP)

        fun gainIntent(context: Context, gain: Float): Intent =
            Intent(context, PlaybackService::class.java)
                .setAction(ACTION_GAIN)
                .putExtra(EXTRA_GAIN, gain)
    }
}
