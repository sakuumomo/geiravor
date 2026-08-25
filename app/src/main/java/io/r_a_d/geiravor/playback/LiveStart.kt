package io.r_a_d.geiravor.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

internal fun playLiveStream(context: Context) {
    val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
    val future = MediaController.Builder(context, token).buildAsync()
    future.addListener(
        {
            val controller = future.get()
            controller.play()
            MediaController.releaseFuture(future)
        },
        ContextCompat.getMainExecutor(context),
    )
}
