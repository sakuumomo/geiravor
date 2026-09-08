package io.r_a_d.geiravor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.r_a_d.geiravor.theme.LocalTokens
import uniffi.geiravor_core.ListEntry
import uniffi.geiravor_core.relativeLastPlayed
import uniffi.geiravor_core.relativeQueue

@Composable
fun SongsScreen(ui: UiState) {
    val t = LocalTokens.current
    val status = ui.status
    val sections = buildList {
        add(SongsSection.LastPlayed)
        if (status?.isAfk != false) add(SongsSection.Queue)
        add(SongsSection.Request)
        add(SongsSection.Favorites)
    }
    LaunchedEffect(status?.isAfk, ui.songsSection) {
        if (ui.songsSection == SongsSection.Queue && status?.isAfk == false) {
            ui.songsSection = SongsSection.LastPlayed
        }
    }
    val selected = sections.indexOf(ui.songsSection).coerceAtLeast(0)
    val hug = ui.songsSection == SongsSection.LastPlayed || ui.songsSection == SongsSection.Queue
    Pane(Modifier.fillMaxSize(), hug = hug) {
        SectionTabs(
            labels = sections.map { it.label },
            selected = selected,
            onSelect = { ui.songsSection = sections[it] },
        )
        Spacer(Modifier.height(12.dp))
        when (ui.songsSection) {
            SongsSection.LastPlayed -> SongList(
                status?.lp.orEmpty(),
                requestBlue = t.blue,
                muted = t.muted,
                text = t.text,
                rel = { e -> relativeLastPlayed(status?.current ?: 0, e.timestamp) },
            )
            SongsSection.Queue -> SongList(
                if (status?.isAfk == true) status.queue else emptyList(),
                requestBlue = t.blue,
                muted = t.muted,
                text = t.text,
                rel = { e -> relativeQueue(status?.current ?: 0, e.timestamp) },
            )
            SongsSection.Request -> Text(
                "Search and request when the AFK stream is on.",
                color = t.muted,
            )
            SongsSection.Favorites -> Text(
                "Public list for a Rizon nick. Empty nick is an empty list.",
                color = t.muted,
            )
        }
    }
}

@Composable
private fun SongList(
    rows: List<ListEntry>,
    requestBlue: androidx.compose.ui.graphics.Color,
    muted: androidx.compose.ui.graphics.Color,
    text: androidx.compose.ui.graphics.Color,
    rel: (ListEntry) -> String,
) {
    Column(Modifier.verticalScroll(rememberScrollState()).fillMaxWidth()) {
        rows.forEach { e ->
            val title = buildString {
                if (e.isRequest) append("/r/ ")
                if (e.artist.isNotBlank()) {
                    append(e.artist)
                    append(" - ")
                }
                append(e.title)
            }
            Text(
                title,
                color = if (e.isRequest) requestBlue else text,
                maxLines = 1,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            Text(rel(e), color = muted, modifier = Modifier.padding(bottom = 8.dp))
        }
        if (rows.isEmpty()) {
            Text("Nothing here yet.", color = muted)
        }
    }
}
