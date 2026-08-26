package io.r_a_d.geiravor.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.r_a_d.geiravor.R
import io.r_a_d.geiravor.playback.LivePlaybackPolicy
import kotlinx.coroutines.delay
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
    onPlayToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var progress by remember { mutableStateOf<SongProgress?>(null) }
    var tagsOpen by remember { mutableStateOf(false) }
    var sliding by remember { mutableStateOf(false) }
    var percent by remember { mutableStateOf(LivePlaybackPolicy.toPercent(gain)) }

    LaunchedEffect(radio) {
        while (true) {
            progress = radio.progress()
            delay(1000)
        }
    }

    LaunchedEffect(gain) {
        if (!sliding) {
            percent = LivePlaybackPolicy.toPercent(gain)
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
            colors = ButtonDefaults.buttonColors(containerColor = RadioTheme.blue),
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
                    percent = value
                    onGain(LivePlaybackPolicy.fromPercent(value))
                },
                onValueChangeFinished = {
                    sliding = false
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
                color = RadioTheme.muted,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = StreamStatus.headline(status?.np, streamDown),
                color = RadioTheme.text,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (!status?.tags.isNullOrEmpty()) {
                Text(
                    text = if (tagsOpen) "−" else "+",
                    color = RadioTheme.muted,
                    fontSize = 22.sp,
                    modifier = Modifier
                        .clickable { tagsOpen = !tagsOpen }
                        .padding(8.dp),
                )
            }
        }
        if (tagsOpen && !status?.tags.isNullOrEmpty()) {
            Text(
                text = status!!.tags.joinToString(" "),
                color = RadioTheme.muted,
                textAlign = TextAlign.Center,
            )
        }
        val duration = progress?.durationSecs
        val elapsed = progress?.elapsedSecs ?: 0
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
        val clock = formatProgressClock(elapsed, duration)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Listeners: ${status?.listeners ?: "—"}",
                color = RadioTheme.muted,
            )
            if (clock != null) {
                Text(text = clock, color = RadioTheme.text)
            }
        }
        val context = LocalContext.current
        status?.thread?.let { url ->
            Text(
                text = url,
                color = RadioTheme.link,
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                },
            )
        }
        val djUrl = status?.let { djImageUrl(it.dj.image) }
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(djUrl)
                .crossfade(false)
                .memoryCacheKey(djUrl)
                .diskCacheKey(djUrl)
                .placeholderMemoryCacheKey(djUrl)
                .error(R.drawable.mystery_dj)
                .fallback(R.drawable.mystery_dj)
                .build(),
            contentDescription = status?.dj?.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(160.dp),
        )
        Text(
            text = status?.dj?.name ?: "",
            color = RadioTheme.text,
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
        Text("Next", color = RadioTheme.muted, fontSize = 12.sp)
        Text(
            text = neighbors.next,
            color = RadioTheme.text,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Previous", color = RadioTheme.muted, fontSize = 12.sp)
        Text(
            text = neighbors.previous,
            color = RadioTheme.text,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
    }
}
