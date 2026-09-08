package io.r_a_d.geiravor.playback

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.metadata.icy.IcyInfo
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
    private var alarmRing = false
    private var fallback: android.media.MediaPlayer? = null
    private var sleepAt = 0L
    private val sleepHandler = Handler(Looper.getMainLooper())
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private val reconnectLive = Runnable {
        if (LivePlaybackPolicy.shouldReconnect(player.playWhenReady)) {
            live.playLive()
        }
    }
    private val sleepTick = object : Runnable {
        override fun run() {
            if (sleepAt == 0L) return
            val left = sleepAt - System.currentTimeMillis()
            if (left <= 0L) {
                cancelSleep(restore = true)
                live.pauseStops()
                return
            }
            if (left <= 15_000L) {
                player.volume = lastGain * (left / 15_000f)
            }
            sleepHandler.postDelayed(this, 250)
        }
    }

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
                if (isPlaying) {
                    alarmRing = false
                    fallback?.release()
                    fallback = null
                    cancelReconnect()
                } else {
                    cancelSleep(restore = true)
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady) cancelReconnect()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                core().setPlayerError()
                if (alarmRing) playFallback()
                scheduleReconnect()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    core().setPlayerError()
                    scheduleReconnect()
                }
            }

            override fun onMetadata(metadata: Metadata) {
                var icy = false
                for (i in 0 until metadata.length()) {
                    if (metadata.get(i) is IcyInfo) icy = true
                }
                if (icy) {
                    Thread({ runCatching { core().fetchStatus() } }, "geiravor-icy").start()
                }
            }
        })
        live = LiveStationPlayer(player)
        ensureLiveItem()
        session = MediaLibrarySession.Builder(this, live, Callbacks())
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
            ACTION_STOP -> {
                cancelReconnect()
                cancelSleep(restore = true)
                live.pauseStops()
            }
            ACTION_ALARM -> {
                alarmRing = true
                live.playLive()
            }
            ACTION_SLEEP -> {
                val mins = intent.getIntExtra(EXTRA_SLEEP_MIN, 30).coerceIn(1, 12 * 60)
                armSleep(mins * 60_000L)
            }
            ACTION_GAIN -> {
                val g = intent.getFloatExtra(EXTRA_GAIN, lastGain).coerceIn(0f, 1f)
                lastGain = g
                if (sleepAt == 0L || sleepAt - System.currentTimeMillis() > 15_000L) {
                    player.volume = g
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        cancelReconnect()
        cancelSleep(restore = false)
        fallback?.release()
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
        val now = System.currentTimeMillis() / 1000
        val fetched = (application as GeiravorApp).ui.fetchedAt
        val progress = uniffi.geiravor_core.songProgressAt(status, now, fetched)
        val duration = if (progress.known) progress.durationSecs * 1000 else C.TIME_UNSET
        val line2 = ShadeLine.fitForShade(this, status.artist, status.dj.name)
        val meta = MediaMetadata.Builder()
            .setTitle(status.title.ifBlank { status.np })
            .setArtist(line2)
            .setAlbumArtist(status.dj.name)
            .setDurationMs(duration)
            .setArtworkUri(
                LivePlaybackPolicy.djImageUrl(status.dj.image)?.let { android.net.Uri.parse(it) },
            )
            .build()
        val built = item.buildUpon().setMediaMetadata(meta)
        if (!progress.known) {
            built.setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
        }
        player.replaceMediaItem(0, built.build())
    }

    private fun playFallback() {
        fallback?.release()
        fallback = android.media.MediaPlayer.create(this, io.r_a_d.geiravor.R.raw.alarm_fallback)
        fallback?.isLooping = true
        fallback?.start()
    }

    private fun armSleep(ms: Long) {
        sleepAt = System.currentTimeMillis() + ms
        sleepHandler.removeCallbacks(sleepTick)
        sleepHandler.post(sleepTick)
    }

    private fun cancelSleep(restore: Boolean) {
        sleepAt = 0L
        sleepHandler.removeCallbacks(sleepTick)
        if (restore) player.volume = lastGain
    }

    private fun scheduleReconnect() {
        if (!LivePlaybackPolicy.shouldReconnect(player.playWhenReady)) return
        reconnectHandler.removeCallbacks(reconnectLive)
        reconnectHandler.postDelayed(reconnectLive, LivePlaybackPolicy.RECONNECT_DELAY_MS)
    }

    private fun cancelReconnect() {
        reconnectHandler.removeCallbacks(reconnectLive)
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
            val app = application as GeiravorApp
            if (app.ui.autoStartVehicle) {
                live.playLive()
            }
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
                    val app = application as GeiravorApp
                    Thread {
                        val snap = core().snapshot()
                        val unfave = snap?.let { s ->
                            runCatching {
                                core().membershipHas(
                                    app.ui.listNickOrConnection(),
                                    if (s.isAfk) s.trackId else 0,
                                    s.np,
                                )
                            }.getOrDefault(false)
                        } ?: false
                        val id = snap?.let { if (it.isAfk) it.trackId else 0L } ?: 0L
                        core().addFave(app.ui.faveConfig(app.secrets), unfave, id)
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
            if ((application as GeiravorApp).ui.autoStartVehicle) {
                live.playLive()
            }
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
        const val ACTION_ALARM = "io.r_a_d.geiravor.ALARM"
        const val ACTION_SLEEP = "io.r_a_d.geiravor.SLEEP"
        const val EXTRA_GAIN = "gain"
        const val EXTRA_SLEEP_MIN = "sleep_min"

        fun playIntent(context: Context): Intent =
            Intent(context, PlaybackService::class.java).setAction(ACTION_PLAY)

        fun stopIntent(context: Context): Intent =
            Intent(context, PlaybackService::class.java).setAction(ACTION_STOP)

        fun alarmIntent(context: Context): Intent =
            Intent(context, PlaybackService::class.java).setAction(ACTION_ALARM)

        fun sleepIntent(context: Context, minutes: Int): Intent =
            Intent(context, PlaybackService::class.java)
                .setAction(ACTION_SLEEP)
                .putExtra(EXTRA_SLEEP_MIN, minutes)

        fun gainIntent(context: Context, gain: Float): Intent =
            Intent(context, PlaybackService::class.java)
                .setAction(ACTION_GAIN)
                .putExtra(EXTRA_GAIN, gain)
    }
}
