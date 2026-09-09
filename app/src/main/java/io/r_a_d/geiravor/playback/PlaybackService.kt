package io.r_a_d.geiravor.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import android.os.Handler
import android.os.Looper
import io.r_a_d.geiravor.BuildConfig
import io.r_a_d.geiravor.GeiravorApp
import io.r_a_d.geiravor.MainActivity
import io.r_a_d.geiravor.R
import io.r_a_d.geiravor.compat.loadCoilStill
import io.r_a_d.geiravor.compat.mysteryDjBitmap
import io.r_a_d.geiravor.compat.startMediaPlaybackForeground
import io.r_a_d.geiravor.ui.Prefs
import io.r_a_d.geiravor.ui.tapFave
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
    private var lastSongsSig = ""
    private var playbackForeground = false
    private var shadePosted = false
    private var shadeArt: Bitmap? = null
    private var shadeArtUrl: String? = null
    private val sleepHandler = Handler(Looper.getMainLooper())
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private val reconnectLive = Runnable {
        if (LivePlaybackPolicy.shouldReconnect(player.playWhenReady)) {
            playLiveNow()
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
        PlaybackNotice.ensureChannel(this)
        setShowNotificationForIdlePlayer(
            MediaSessionService.SHOW_NOTIFICATION_FOR_IDLE_PLAYER_NEVER,
        )
        setMediaNotificationProvider(ShadeNotificationProvider(this))
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
                    (application as GeiravorApp).ui.offMain {
                        runCatching { core().fetchStatus() }
                    }
                }
            }
        })
        live = LiveStationPlayer(player)
        ensureLiveItem()
        lastSongsSig = AutoBrowse.songsSignature(core().snapshot())
        session = MediaLibrarySession.Builder(this, live, Callbacks())
            .setId("geiravor")
            .setBitmapLoader(CoilStillBitmapLoader(this))
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        refreshButtons()
        (application as GeiravorApp).heartPaint = { refreshButtons() }
        core().addListener(Listener())
        core().snapshot()?.let { applyStatus(it) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        session

    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        if (shadePosted) return
        super.onUpdateNotification(session, false)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> playLiveNow()
            ACTION_STOP -> {
                cancelReconnect()
                cancelSleep(restore = true)
                live.pauseStops()
                leavePlaybackForeground()
            }
            ACTION_ALARM -> {
                alarmRing = true
                playLiveNow()
            }
            ACTION_SLEEP -> {
                val mins = intent.getIntExtra(EXTRA_SLEEP_MIN, 30).coerceIn(1, 12 * 60)
                armSleep(mins * 60_000L)
            }
            ACTION_FAVE -> {
                val app = application as GeiravorApp
                tapFave(app.ui, app.core, app.secrets) { refreshButtons() }
            }
            ACTION_MUTE -> {
                val cur = player.volume
                if (cur > 0f) lastGain = cur
                applyGain(LivePlaybackPolicy.nextGainAfterMute(cur, lastGain))
            }
            ACTION_VOL_UP ->
                applyGain(LivePlaybackPolicy.stepGain(player.volume, LivePlaybackPolicy.VOL_STEP))
            ACTION_VOL_DOWN ->
                applyGain(LivePlaybackPolicy.stepGain(player.volume, -LivePlaybackPolicy.VOL_STEP))
            ACTION_DISMISS -> {
                shadePosted = false
                playbackForeground = false
                getSystemService(android.app.NotificationManager::class.java)
                    ?.cancel(PlaybackNotice.ID)
            }
            ACTION_GAIN -> {
                val g = intent.getFloatExtra(EXTRA_GAIN, lastGain).coerceIn(0f, 1f)
                if (g > 0f) lastGain = g
                if (sleepAt == 0L || sleepAt - System.currentTimeMillis() > 15_000L) {
                    player.volume = g
                    postShade(playbackForeground)
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        (application as GeiravorApp).heartPaint = null
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

    private fun playLiveNow() {
        if (!playbackForeground) enterPlaybackForeground()
        live.playLive()
    }

    private fun shadeNotice(playing: Boolean): android.app.Notification {
        PlaybackNotice.ensureChannel(this)
        val meta = player.mediaMetadata
        val title = meta.displayTitle ?: meta.title ?: getString(R.string.app_name)
        val text = ShadeLine.fitForShade(
            this,
            NowPlayingMeta.rawArtist(meta),
            NowPlayingMeta.dj(meta),
        ).ifBlank { getString(R.string.playback_connecting) }
        return PlaybackNotice.shade(
            this,
            PlaybackNotice.Shade(
                title = title,
                text = text,
                playing = playing,
                muted = LivePlaybackPolicy.muted(player.volume),
                heartFilled = (application as GeiravorApp).ui.heartFilled,
                art = shadeArt,
            ),
        )
    }

    private fun postShade(playing: Boolean) {
        if (!shadePosted) return
        val nm = getSystemService(android.app.NotificationManager::class.java) ?: return
        if (!playing && nm.activeNotifications.none { it.id == PlaybackNotice.ID }) {
            shadePosted = false
            return
        }
        nm.notify(PlaybackNotice.ID, shadeNotice(playing))
    }

    private fun loadShadeArt(url: String?) {
        val key = url.orEmpty()
        if (key == shadeArtUrl && shadeArt != null) return
        shadeArtUrl = key
        val app = application as GeiravorApp
        app.ui.offMain {
            val bmp = loadCoilStill(this, url) ?: mysteryDjBitmap(this)
            app.ui.onMain {
                if (shadeArtUrl != key) return@onMain
                shadeArt = bmp
                postShade(playbackForeground)
            }
        }
    }

    private fun enterPlaybackForeground() {
        startMediaPlaybackForeground(PlaybackNotice.ID, shadeNotice(playing = true))
        playbackForeground = true
        shadePosted = true
        loadShadeArt(player.mediaMetadata.artworkUri?.toString())
    }

    private fun leavePlaybackForeground() {
        playbackForeground = false
        shadePosted = true
        stopForeground(android.app.Service.STOP_FOREGROUND_DETACH)
        getSystemService(android.app.NotificationManager::class.java)
            ?.notify(PlaybackNotice.ID, shadeNotice(playing = false))
    }

    private fun applyStatus(status: Status) {
        val item = player.currentMediaItem ?: return
        val now = System.currentTimeMillis() / 1000
        val fetched = (application as GeiravorApp).ui.fetchedAt
        val progress = uniffi.geiravor_core.songProgressAt(status, now, fetched)
        val duration = if (progress.known) progress.durationSecs * 1000 else C.TIME_UNSET
        val fields = NowPlayingMeta.fields(status.title, status.artist, status.np, status.dj.name)
        val meta = MediaMetadata.Builder()
            .setDisplayTitle(fields.title)
            .setTitle(fields.title)
            .setArtist(fields.subtitle)
            .setDescription(fields.description)
            .setAlbumArtist(fields.dj)
            .setDurationMs(duration)
            .setArtworkUri(
                LivePlaybackPolicy.djImageUrl(status.dj.image)?.let { android.net.Uri.parse(it) },
            )
            .setExtras(NowPlayingMeta.extras(fields.artist, fields.dj))
            .build()
        val built = item.buildUpon().setMediaMetadata(meta)
        if (progress.known) {
            built.setLiveConfiguration(MediaItem.LiveConfiguration.UNSET)
        } else {
            built.setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
        }
        player.replaceMediaItem(0, built.build())
        if (shadePosted) {
            loadShadeArt(LivePlaybackPolicy.djImageUrl(status.dj.image))
            postShade(playbackForeground)
        }
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
            Handler(Looper.getMainLooper()).post {
                applyStatus(status)
                refreshButtons()
                val sig = AutoBrowse.songsSignature(status)
                if (sig != lastSongsSig) {
                    lastSongsSig = sig
                    val flags = autoFlags()
                    session?.notifyChildrenChanged(
                        AutoBrowse.SONGS,
                        AutoBrowse.children(AutoBrowse.SONGS, status, flags).size,
                        null,
                    )
                    session?.notifyChildrenChanged(
                        AutoBrowse.LAST,
                        AutoBrowse.children(AutoBrowse.LAST, status, flags).size,
                        null,
                    )
                    session?.notifyChildrenChanged(
                        AutoBrowse.QUEUE,
                        AutoBrowse.children(AutoBrowse.QUEUE, status, flags).size,
                        null,
                    )
                }
            }
        }
    }

    private fun autoFlags() = AutoBrowse.Flags(
        vehicle = (application as GeiravorApp).ui.autoStartVehicle,
        plug = (application as GeiravorApp).ui.autoStartPlug,
        version = BuildConfig.VERSION_NAME,
    )

    private fun toggleSetting(id: String) {
        val app = application as GeiravorApp
        when (id) {
            AutoBrowse.VEHICLE -> {
                app.ui.autoStartVehicle = !app.ui.autoStartVehicle
                app.ui.setFlag(app.core, Prefs.AUTOSTART_VEHICLE, app.ui.autoStartVehicle)
            }
            AutoBrowse.PLUG -> {
                app.ui.autoStartPlug = !app.ui.autoStartPlug
                app.ui.setFlag(app.core, Prefs.AUTOSTART_PLUG, app.ui.autoStartPlug)
            }
        }
        session?.notifyChildrenChanged(AutoBrowse.SETTINGS, 3, null)
    }

    private fun refreshButtons() {
        session?.setMediaButtonPreferences(
            LivePlaybackPolicy.mediaButtons(
                (application as GeiravorApp).ui.heartFilled,
                LivePlaybackPolicy.muted(player.volume),
            ),
        )
        postShade(playbackForeground)
    }

    private fun applyGain(g: Float) {
        setPlayerGain(g, persist = true)
        val app = application as GeiravorApp
        app.ui.gain = player.volume
        if (player.volume > 0f) app.ui.lastGain = lastGain
        postShade(playbackForeground)
    }

    private fun setPlayerGain(g: Float, persist: Boolean) {
        val v = g.coerceIn(0f, 1f)
        player.volume = v
        if (v > 0f) lastGain = v
        if (persist) {
            val app = application as GeiravorApp
            app.ui.gain = v
            app.ui.setPref(app.core, Prefs.GAIN, v.toString())
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
                playLiveNow()
            }
            app.ui.offMain {
                app.ui.membershipNicks().forEach { nick ->
                    runCatching { core().revalidateMembership(nick) }
                }
            }
            refreshButtons()
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
                    tapFave(app.ui, app.core, app.secrets) { refreshButtons() }
                }
                LivePlaybackPolicy.MUTE -> {
                    val cur = player.volume
                    if (cur > 0f) lastGain = cur
                    applyGain(LivePlaybackPolicy.nextGainAfterMute(cur, lastGain))
                }
                LivePlaybackPolicy.VOL_UP ->
                    applyGain(LivePlaybackPolicy.stepGain(player.volume, LivePlaybackPolicy.VOL_STEP))
                LivePlaybackPolicy.VOL_DOWN ->
                    applyGain(LivePlaybackPolicy.stepGain(player.volume, -LivePlaybackPolicy.VOL_STEP))
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
            val kids = AutoBrowse.children(parentId, core().snapshot(), autoFlags())
            return Futures.immediateFuture(LibraryResult.ofItemList(kids, params))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            if (AutoBrowse.isLiveId(mediaId) || mediaId == player.currentMediaItem?.mediaId) {
                ensureLiveItem()
                val item = player.currentMediaItem
                    ?: MediaItem.fromUri(LivePlaybackPolicy.STREAM_URL)
                return Futures.immediateFuture(LibraryResult.ofItem(item, null))
            }
            val found = AutoBrowse.item(mediaId, core().snapshot(), autoFlags())
            return if (found != null) {
                Futures.immediateFuture(LibraryResult.ofItem(found, null))
            } else {
                Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            }
        }

        override fun onAddMediaItems(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val id = mediaItems.firstOrNull()?.mediaId.orEmpty()
            if (AutoBrowse.isSettingsToggle(id) || id == AutoBrowse.ABOUT) {
                if (AutoBrowse.isSettingsToggle(id)) toggleSetting(id)
                if (!player.isPlaying) live.skipNextPlay = true
                ensureLiveItem()
                val current = player.currentMediaItem
                    ?: MediaItem.fromUri(LivePlaybackPolicy.STREAM_URL)
                return Futures.immediateFuture(mutableListOf(current))
            }
            return super.onAddMediaItems(session, controller, mediaItems)
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            ensureLiveItem()
            if ((application as GeiravorApp).ui.autoStartVehicle) {
                playLiveNow()
            } else {
                live.skipNextPlay = true
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
        const val ACTION_FAVE = "io.r_a_d.geiravor.FAVE"
        const val ACTION_MUTE = "io.r_a_d.geiravor.MUTE"
        const val ACTION_VOL_UP = "io.r_a_d.geiravor.VOL_UP"
        const val ACTION_VOL_DOWN = "io.r_a_d.geiravor.VOL_DOWN"
        const val ACTION_DISMISS = "io.r_a_d.geiravor.DISMISS"
        const val ACTION_GAIN = "io.r_a_d.geiravor.GAIN"
        const val ACTION_ALARM = "io.r_a_d.geiravor.ALARM"
        const val ACTION_SLEEP = "io.r_a_d.geiravor.SLEEP"
        const val EXTRA_GAIN = "gain"
        const val EXTRA_SLEEP_MIN = "sleep_min"

        fun playIntent(context: Context): Intent =
            Intent(context, PlaybackService::class.java).setAction(ACTION_PLAY)

        fun stopIntent(context: Context): Intent =
            Intent(context, PlaybackService::class.java).setAction(ACTION_STOP)

        fun faveIntent(context: Context): Intent =
            Intent(context, PlaybackService::class.java).setAction(ACTION_FAVE)

        fun muteIntent(context: Context): Intent =
            Intent(context, PlaybackService::class.java).setAction(ACTION_MUTE)

        fun volUpIntent(context: Context): Intent =
            Intent(context, PlaybackService::class.java).setAction(ACTION_VOL_UP)

        fun volDownIntent(context: Context): Intent =
            Intent(context, PlaybackService::class.java).setAction(ACTION_VOL_DOWN)

        fun dismissIntent(context: Context): Intent =
            Intent(context, PlaybackService::class.java).setAction(ACTION_DISMISS)

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
