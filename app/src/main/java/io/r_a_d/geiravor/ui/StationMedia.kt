package io.r_a_d.geiravor.ui

import android.view.TextureView
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import coil.request.ImageRequest

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StationMedia(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    @DrawableRes error: Int? = null,
    @DrawableRes fallback: Int? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val data = url?.trim().orEmpty()
    val clickable = if (onClick != null || onLongClick != null) {
        modifier.combinedClickable(
            onClick = { onClick?.invoke() },
            onLongClick = onLongClick,
        )
    } else {
        modifier
    }
    if (data.isNotEmpty() && MediaPolicy.isVideo(data)) {
        LoopingVideo(
            url = data,
            crop = contentScale == ContentScale.Crop,
            modifier = clickable,
        )
        return
    }
    val context = LocalContext.current
    val request = ImageRequest.Builder(context)
        .data(data.ifEmpty { null })
        .crossfade(false)
        .memoryCacheKey(data.ifEmpty { null })
        .diskCacheKey(data.ifEmpty { null })
        .placeholderMemoryCacheKey(data.ifEmpty { null })
    if (error != null) {
        request.error(error)
    }
    if (fallback != null) {
        request.fallback(fallback)
    }
    AsyncImage(
        model = request.build(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = clickable,
    )
}

@Composable
private fun LoopingVideo(
    url: String,
    crop: Boolean,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            videoScalingMode = if (crop) {
                C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            } else {
                C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            }
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { ctx -> TextureView(ctx) },
        update = { view -> player.setVideoTextureView(view) },
        modifier = modifier,
    )
}
