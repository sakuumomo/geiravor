package io.r_a_d.geiravor.ui

import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.imageLoader
import io.r_a_d.geiravor.GeiravorApp
import uniffi.geiravor_core.MediaKind
import uniffi.geiravor_core.mediaKindFor

/**
 * DJ / schedule / staff / thread: GIF and muted looping video autoplay.
 * News passes [autoplay] false so GIF/mp4/webm stay still.
 */
@Composable
fun StationMedia(
    url: String?,
    autoplay: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
    placeholder: Painter? = null,
    error: Painter? = null,
) {
    val ctx = LocalContext.current
    val stillLoader = remember(ctx) {
        (ctx.applicationContext as? GeiravorApp)?.stillImages
            ?: ImageLoader.Builder(ctx).build()
    }
    val gifLoader = ctx.imageLoader
    if (url.isNullOrBlank()) {
        if (placeholder != null) {
            Image(placeholder, contentDescription, modifier, contentScale = contentScale)
        }
        return
    }
    val kind = remember(url) { mediaKindFor(url) }
    if (autoplay && kind == MediaKind.VIDEO) {
        MutedLoopVideo(url, modifier)
        return
    }
    val loader = if (autoplay && kind == MediaKind.GIF) gifLoader else stillLoader
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        imageLoader = loader,
        modifier = modifier,
        placeholder = placeholder,
        error = error,
        contentScale = contentScale,
    )
}

@Composable
private fun MutedLoopVideo(url: String, modifier: Modifier) {
    val ctx = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(ctx).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { c ->
            TextureView(c).also { tv -> player.setVideoTextureView(tv) }
        },
        update = { tv -> player.setVideoTextureView(tv) },
        modifier = modifier,
    )
}
