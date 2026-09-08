package io.r_a_d.geiravor.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import coil.compose.AsyncImage
import io.r_a_d.geiravor.R
import io.r_a_d.geiravor.playback.LivePlaybackPolicy
import io.r_a_d.geiravor.theme.LocalTokens
import kotlinx.coroutines.delay
import uniffi.geiravor_core.formatClock
import uniffi.geiravor_core.songProgressAt
import uniffi.geiravor_core.threadIsVisible

@Composable
fun NowPlayingScreen(
    ui: UiState,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onGain: (Float) -> Unit,
    onFave: () -> Unit,
) {
    val t = LocalTokens.current
    val status = ui.status
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(status?.isAfk) {
        while (true) {
            now = System.currentTimeMillis() / 1000
            delay(1_000)
        }
    }
    val progress = status?.let { songProgressAt(it, now, ui.fetchedAt) }
    Pane(Modifier.fillMaxWidth(), hug = true) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painterResource(R.drawable.logotitle_2),
                contentDescription = "r/a/dio",
                modifier = Modifier
                    .height(36.dp)
                    .padding(bottom = 12.dp),
                contentScale = ContentScale.Fit,
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { if (ui.playing) onStop() else onPlay() }) {
                    Icon(
                        if (ui.playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = if (ui.playing) "Stop" else "Play",
                        tint = t.accent,
                        modifier = Modifier.size(40.dp),
                    )
                }
                Slider(
                    value = ui.gain * 100f,
                    onValueChange = { onGain((it / 100f).coerceIn(0f, 1f)) },
                    valueRange = 0f..100f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = t.accent,
                        activeTrackColor = t.accent,
                    ),
                )
                IconButton(onClick = onFave, enabled = !ui.faveBusy) {
                    Icon(
                        painterResource(if (ui.heartFilled) R.drawable.ic_fave_filled else R.drawable.ic_fave),
                        contentDescription = "Fave",
                        tint = t.accent,
                    )
                }
            }
            val title = status?.title?.ifBlank { status.np } ?: "…"
            val artist = status?.artist.orEmpty()
            Text(
                if (artist.isBlank()) title else "$artist - $title",
                color = t.text,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            val tags = status?.tags.orEmpty()
            if (tags.isNotEmpty()) {
                Text(
                    if (ui.tagsOpen) "−" else "+",
                    color = t.link,
                    modifier = Modifier
                        .clickable { ui.tagsOpen = !ui.tagsOpen }
                        .padding(4.dp),
                )
                if (ui.tagsOpen) {
                    Text(tags.joinToString(" "), color = t.muted, textAlign = TextAlign.Center)
                }
            }
            if (progress?.known == true) {
                LinearProgressIndicator(
                    progress = {
                        if (progress.durationSecs == 0L) 0f
                        else progress.elapsedSecs.toFloat() / progress.durationSecs.toFloat()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    color = t.accent,
                    trackColor = t.border,
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                Text(
                    status?.listeners?.toString() ?: "",
                    color = t.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (progress?.known == true) {
                        "${formatClock(progress.elapsedSecs)} / ${formatClock(progress.durationSecs)}"
                    } else {
                        ""
                    },
                    color = t.muted,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                )
            }
            val djUrl = LivePlaybackPolicy.djImageUrl(status?.dj?.image)
            AsyncImage(
                model = djUrl,
                contentDescription = status?.dj?.name,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .size(160.dp),
                placeholder = painterResource(R.drawable.mystery_dj),
                error = painterResource(R.drawable.mystery_dj),
                contentScale = ContentScale.Crop,
            )
            Text(
                status?.dj?.name.orEmpty(),
                color = t.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            val next = when {
                status == null -> ""
                status.isAfk -> status.queue.firstOrNull()?.let { "${it.artist} - ${it.title}" }.orEmpty()
                else -> "???"
            }
            val prev = status?.lp?.firstOrNull()?.let { "${it.artist} - ${it.title}" }.orEmpty()
            Text(
                next,
                color = t.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
            Text(
                prev,
                color = t.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            val thread = status?.thread.orEmpty()
            if (status != null && threadIsVisible(status.isAfk, thread)) {
                ThreadLine(thread)
            }
            ui.faveError?.let {
                Text(it, color = t.red, modifier = Modifier.padding(top = 8.dp))
            }
            if (ui.streamDown) {
                Text("Stream down", color = t.red, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun ThreadLine(thread: String) {
    val t = LocalTokens.current
    val ctx = LocalContext.current
    val trimmed = thread.trim()
    val image = when {
        trimmed.startsWith("image:", ignoreCase = true) ->
            trimmed.removePrefix("image:").removePrefix("IMAGE:").trim()
        trimmed.lowercase().let {
            it.endsWith(".gif") || it.endsWith(".png") || it.endsWith(".jpg") ||
                it.endsWith(".jpeg") || it.endsWith(".webp") || it.endsWith(".mp4") ||
                it.endsWith(".webm")
        } -> trimmed
        else -> null
    }
    if (image != null) {
        AsyncImage(
            model = image,
            contentDescription = "Thread",
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .height(180.dp),
            contentScale = ContentScale.Fit,
        )
    } else {
        Text(
            trimmed,
            color = t.link,
            modifier = Modifier
                .padding(top = 12.dp)
                .clickable {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(trimmed)))
                },
        )
    }
}
