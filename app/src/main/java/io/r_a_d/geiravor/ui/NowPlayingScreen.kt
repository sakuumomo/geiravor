package io.r_a_d.geiravor.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import io.r_a_d.geiravor.R
import io.r_a_d.geiravor.compat.SaveImage
import io.r_a_d.geiravor.playback.LivePlaybackPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.graphics.drawable.BitmapDrawable
import uniffi.geiravor_core.RadioCore
import uniffi.geiravor_core.SongProgress
import uniffi.geiravor_core.Status
import uniffi.geiravor_core.djImageUrl

@Composable
fun NowPlayingScreen(
    radio: RadioCore,
    status: Status?,
    streamDown: Boolean,
    playing: Boolean,
    gain: Float,
    onGain: (Float) -> Unit,
    onGainFinished: (Float) -> Unit,
    onSliding: (Boolean) -> Unit = {},
    onPlayToggle: () -> Unit,
    onFave: () -> Unit = {},
    faveBusy: Boolean = false,
    faveFilled: Boolean = false,
    faveMessage: String? = null,
    showThread: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var progress by remember { mutableStateOf<SongProgress?>(null) }
    var tagsOpen by remember { mutableStateOf(false) }
    var sliding by remember { mutableStateOf(false) }
    var shownFaveError by remember { mutableStateOf<String?>(null) }
    var percent by remember { mutableStateOf(LivePlaybackPolicy.toPercent(gain)) }

    LaunchedEffect(radio) {
        while (true) {
            progress = radio.progress()
            delay(1000)
        }
    }

    LaunchedEffect(status?.tags) {
        if (!StreamStatus.hasTagContent(status?.tags)) {
            tagsOpen = false
        }
    }

    LaunchedEffect(gain) {
        if (!sliding) {
            percent = LivePlaybackPolicy.toPercent(gain)
        }
    }

    LaunchedEffect(faveMessage) {
        if (faveMessage != null) {
            shownFaveError = faveMessage
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState(), enabled = !sliding)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Image(
                painter = painterResource(R.drawable.logo_image_small),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
            Image(
                painter = painterResource(R.drawable.logotitle_2),
                contentDescription = "r/a/dio",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(28.dp)
                    .padding(start = 8.dp),
            )
        }
        Button(
            onClick = onPlayToggle,
            colors = radioButtonColors(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (playing) "Stop" else "Play Stream")
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Slider(
                value = percent,
                onValueChange = { value ->
                    sliding = true
                    onSliding(true)
                    percent = value
                    onGain(LivePlaybackPolicy.fromPercent(value))
                },
                onValueChangeFinished = {
                    sliding = false
                    onSliding(false)
                    onGainFinished(LivePlaybackPolicy.fromPercent(percent))
                },
                valueRange = 0f..100f,
                steps = 99,
                colors = SliderDefaults.colors(
                    thumbColor = RadioTheme.blue,
                    activeTrackColor = RadioTheme.blue,
                ),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = percent.toInt().toString(),
                color = RadioTheme.onBackgroundMuted,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                painter = painterResource(
                    if (faveFilled) R.drawable.ic_fave_filled else R.drawable.ic_fave,
                ),
                contentDescription = "Fave",
                tint = if (faveFilled) RadioTheme.blue else RadioTheme.onBackgroundMuted,
                modifier = Modifier
                    .clickable(enabled = !faveBusy, onClick = onFave)
                    .padding(8.dp)
                    .size(22.dp),
            )
            Text(
                text = StreamStatus.headline(status?.np, streamDown),
                color = RadioTheme.onBackground,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (StreamStatus.hasTagContent(status?.tags)) {
                Text(
                    text = if (tagsOpen) "−" else "+",
                    color = RadioTheme.onBackgroundMuted,
                    fontSize = 22.sp,
                    modifier = Modifier
                        .clickable { tagsOpen = !tagsOpen }
                        .padding(8.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = faveMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Text(
                text = shownFaveError.orEmpty(),
                color = RadioTheme.red,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (tagsOpen && StreamStatus.hasTagContent(status?.tags)) {
            Text(
                text = status!!.tags.filter { it.isNotBlank() }.joinToString(" "),
                color = RadioTheme.onBackgroundMuted,
                textAlign = TextAlign.Center,
            )
        }
        val duration = progress?.durationSecs
        val elapsed = progress?.elapsedSecs ?: 0
        val showProgress = SongListPolicy.showTrackProgress(status?.isAfkStream == true)
        if (showProgress) {
            if (duration == null) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = RadioTheme.blue,
                    trackColor = RadioTheme.border,
                )
            } else {
                val d = duration.coerceAtLeast(1)
                LinearProgressIndicator(
                    progress = { (elapsed.toFloat() / d.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = RadioTheme.blue,
                    trackColor = RadioTheme.border,
                )
            }
        }
        val clock = if (showProgress) formatProgressClock(elapsed, duration) else null
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Listeners: ${status?.listeners ?: "—"}",
                color = RadioTheme.onBackgroundMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
            )
            if (clock != null) {
                Text(
                    text = clock,
                    color = RadioTheme.onBackground,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
        val djUrl = status?.let { djImageUrl(it.dj.image) }
        StationMedia(
            url = djUrl,
            contentDescription = status?.dj?.name,
            contentScale = ContentScale.Crop,
            error = R.drawable.mystery_dj,
            fallback = R.drawable.mystery_dj,
            modifier = Modifier.size(160.dp),
        )
        Text(
            text = status?.dj?.name ?: "",
            color = RadioTheme.onBackground,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        if (StreamStatus.showBanner(streamDown)) {
            Text(StreamStatus.banner, color = RadioTheme.red)
        }
        val neighbors = SongListPolicy.neighbors(
            lastPlayedMeta = status?.lastPlayed?.firstOrNull()?.meta,
            nextInQueueMeta = status?.queue?.firstOrNull()?.meta,
            isAfkStream = status?.isAfkStream,
        )
        Text("Next", color = RadioTheme.onBackgroundMuted, fontSize = 12.sp)
        Text(
            text = neighbors.next,
            color = RadioTheme.onBackground,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Previous", color = RadioTheme.onBackgroundMuted, fontSize = 12.sp)
        Text(
            text = neighbors.previous,
            color = RadioTheme.onBackground,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        if (showThread) {
            ThreadBlock(
                isAfkStream = status?.isAfkStream == true,
                thread = status?.thread,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ThreadBlock(
    isAfkStream: Boolean,
    thread: String?,
) {
    val kind = ThreadPolicy.kind(isAfkStream, thread)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    fun open(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
    when (kind) {
        ThreadPolicy.Kind.Hidden -> {}
        ThreadPolicy.Kind.Link -> {
            val url = ThreadPolicy.linkUrl(thread.orEmpty()) ?: return
            Text(
                text = url,
                color = RadioTheme.link,
                modifier = Modifier.clickable { open(url) },
            )
        }
        ThreadPolicy.Kind.Image -> {
            val url = ThreadPolicy.imageUrl(thread.orEmpty()) ?: return
            Box {
                StationMedia(
                    url = url,
                    contentDescription = "Thread",
                    contentScale = ContentScale.Fit,
                    onClick = { open(url) },
                    onLongClick = { menu = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Save") },
                        onClick = {
                            menu = false
                            scope.launch {
                                val result = context.imageLoader.execute(
                                    ImageRequest.Builder(context).data(url).build(),
                                )
                                val bitmap = (result as? SuccessResult)?.drawable as? BitmapDrawable
                                val name = Uri.parse(url).lastPathSegment?.ifBlank { null } ?: "thread.jpg"
                                bitmap?.bitmap?.let { SaveImage.saveJpeg(context, it, name) }
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Open") },
                        onClick = {
                            menu = false
                            open(url)
                        },
                    )
                }
            }
        }
    }
}
