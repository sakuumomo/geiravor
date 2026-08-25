package io.r_a_d.geiravor.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.r_a_d.geiravor.BuildConfig
import io.r_a_d.geiravor.MainActivity

class PlaybackService : MediaLibraryService() {
    private var session: MediaLibraryService.MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
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
        val player = LiveStationPlayer(exo)
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
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo,
    ): MediaLibraryService.MediaLibrarySession? {
        return session
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    private class LibraryCallback : MediaLibraryService.MediaLibrarySession.Callback {
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
