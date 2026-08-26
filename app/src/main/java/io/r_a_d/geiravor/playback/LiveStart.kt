package io.r_a_d.geiravor.playback

import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

internal fun playLiveStream(context: Context) {
    val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
    val future = MediaController.Builder(context, token).buildAsync()
    val main = Handler(Looper.getMainLooper())
    future.addListener(
        {
            val controller = future.get()
            val release = Runnable { MediaController.releaseFuture(future) }
            controller.addListener(
                object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (
                            playbackState == Player.STATE_BUFFERING ||
                            playbackState == Player.STATE_READY ||
                            playbackState == Player.STATE_ENDED
                        ) {
                            controller.removeListener(this)
                            main.removeCallbacks(release)
                            release.run()
                        }
                    }
                },
            )
            controller.play()
            main.postDelayed(release, 3_000)
        },
        ContextCompat.getMainExecutor(context),
    )
}
