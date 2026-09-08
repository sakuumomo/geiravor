package io.r_a_d.geiravor.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.r_a_d.geiravor.R
import io.r_a_d.geiravor.compat.saveThreadStill
import io.r_a_d.geiravor.playback.LivePlaybackPolicy
import io.r_a_d.geiravor.theme.LocalTokens
import kotlinx.coroutines.delay
import uniffi.geiravor_core.formatClock
import uniffi.geiravor_core.songProgressAt
import uniffi.geiravor_core.threadEmbedUrlFor
import uniffi.geiravor_core.threadIsVisible
import uniffi.geiravor_core.threadLinkUrlFor

@Composable
fun NowPlayingScreen(
    ui: UiState,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onGain: (Float) -> Unit,
    onFave: () -> Unit,
    showThread: Boolean = true,
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
            val logoShape = RoundedCornerShape(6.dp)
            Box(
                Modifier
                    .padding(bottom = 12.dp)
                    .wrapContentWidth()
                    .clip(logoShape)
                    .then(
                        if (t.wallpaper != null) Modifier
                            .background(t.surface, logoShape)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                        else Modifier,
                    ),
            ) {
                Image(
                    painterResource(R.drawable.logotitle_2),
                    contentDescription = "r/a/dio",
                    modifier = Modifier.height(36.dp),
                    contentScale = ContentScale.Fit,
                )
            }
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
            StationMedia(
                url = djUrl,
                autoplay = true,
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
            if (showThread && status != null && threadIsVisible(status.isAfk, thread)) {
                ThreadLine(ui, thread, status.isAfk)
            }
            ui.faveError?.let { msg ->
                val fade = remember(msg) { Animatable(1f) }
                LaunchedEffect(ui.faveErrorFading, msg) {
                    if (ui.faveErrorFading) {
                        fade.animateTo(0f, animationSpec = tween(800))
                        ui.faveError = null
                        ui.faveErrorFading = false
                    } else {
                        fade.snapTo(1f)
                    }
                }
                Text(
                    msg,
                    color = t.red,
                    modifier = Modifier.padding(top = 8.dp).alpha(fade.value),
                )
            }
            if (ui.streamDown) {
                Text("Stream down", color = t.red, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun ThreadLine(ui: UiState, thread: String, isAfk: Boolean) {
    val t = LocalTokens.current
    val ctx = LocalContext.current
    val embed = threadEmbedUrlFor(isAfk, thread)
    val link = threadLinkUrlFor(isAfk, thread)
    if (embed.isNotEmpty()) {
        var menu by remember { mutableStateOf(false) }
        fun open() {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(embed)))
        }
        Box(Modifier.padding(top = 12.dp)) {
            StationMedia(
                url = embed,
                autoplay = true,
                contentDescription = "Thread",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .combinedClickable(
                        onClick = { open() },
                        onLongClick = { menu = true },
                    ),
                contentScale = ContentScale.Fit,
            )
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Save") },
                    onClick = {
                        menu = false
                        ui.offMain { saveThreadStill(ctx, embed) }
                    },
                )
                DropdownMenuItem(
                    text = { Text("Open") },
                    onClick = {
                        menu = false
                        open()
                    },
                )
            }
        }
    } else if (link.isNotEmpty()) {
        Text(
            link,
            color = t.link,
            modifier = Modifier
                .padding(top = 12.dp)
                .clickable {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                },
        )
    }
}
