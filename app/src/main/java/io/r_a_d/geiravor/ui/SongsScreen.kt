package io.r_a_d.geiravor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.r_a_d.geiravor.theme.LocalTokens
import uniffi.geiravor_core.FaveRow
import uniffi.geiravor_core.ListEntry
import uniffi.geiravor_core.RadioCore
import uniffi.geiravor_core.SearchTrack
import uniffi.geiravor_core.faveRequestable
import uniffi.geiravor_core.relativeLastPlayed
import uniffi.geiravor_core.relativeQueue

@Composable
fun SongsScreen(ui: UiState, core: RadioCore) {
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
            SongsSection.Request -> RequestPane(ui, core)
            SongsSection.Favorites -> FavoritesPane(ui, core)
        }
    }
}

@Composable
private fun RequestPane(ui: UiState, core: RadioCore) {
    val t = LocalTokens.current
    val afk = ui.status?.isAfk == true
    var fit by remember { mutableStateOf(1u) }
    fun go(page: UInt) {
        val q = ui.query
        val n = fit
        ui.offMain {
            runCatching { core.canRequest() }.onSuccess { ok -> ui.onMain { ui.canRequest = ok } }
            val result = runCatching { core.searchWindow(q, page, n) }.getOrNull()
            ui.onMain { ui.search = result }
        }
    }
    Column(Modifier.fillMaxSize()) {
        TextField(
            value = ui.query,
            onValueChange = { ui.query = it },
            singleLine = true,
            label = { Text("Search") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { go(1u) }),
            modifier = Modifier.fillMaxWidth(),
        )
        ui.requestText?.let { Text(it, color = if (it.contains("Thank", true)) t.green else t.red) }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val measured = (maxHeight / 56.dp).toInt().coerceAtLeast(1).toUInt()
            fit = lockPaneFit(measured, ui.searchFit) { ui.searchFit = it }
            LaunchedEffect(fit) {
                if (ui.query.isNotBlank() && ui.search != null) go(ui.search?.currentPage ?: 1u)
            }
            Column {
                val tracks = ui.search?.tracks.orEmpty()
                tracks.forEach { track ->
                    SearchRow(ui, core, track, afk && ui.canRequest && track.requestable)
                }
                if (tracks.isEmpty()) {
                    Text("Search the catalog.", color = t.muted, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        PagerBar(
            page = ui.search?.currentPage ?: 1u,
            last = ui.search?.lastPage ?: 1u,
            onPage = { go(it) },
        )
    }
}

@Composable
private fun SearchRow(ui: UiState, core: RadioCore, track: SearchTrack, can: Boolean) {
    val t = LocalTokens.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text(
                if (track.artist.isBlank()) track.title else "${track.artist} - ${track.title}",
                color = t.text,
                maxLines = 1,
            )
        }
        TextButton(
            onClick = {
                ui.offMain {
                    val r = runCatching { core.requestTrack(track.id) }.getOrNull()
                    ui.onMain { ui.requestText = r?.text ?: "request failed" }
                }
            },
            enabled = can,
            colors = ButtonDefaults.textButtonColors(contentColor = t.accent),
        ) { Text("Request") }
    }
}

@Composable
private fun FavoritesPane(ui: UiState, core: RadioCore) {
    val t = LocalTokens.current
    val afk = ui.status?.isAfk == true
    val now = ui.status?.current ?: 0
    var fit by remember { mutableStateOf(1u) }
    fun load(page: UInt) {
        val nick = ui.listNickOrConnection()
        val n = fit
        ui.offMain {
            if (nick.isEmpty()) {
                ui.onMain {
                    ui.faveRows = emptyList()
                    ui.favePage = 1u
                    ui.faveLast = 1u
                }
                return@offMain
            }
            val cached = runCatching { core.cachedFavesWindow(nick, page, n) }.getOrNull()
            cached?.let {
                ui.onMain {
                    ui.faveRows = it.rows
                    ui.favePage = it.page
                    ui.faveLast = it.lastPage
                }
            }
            val live = runCatching { core.favesWindow(nick, page, n) }.getOrNull()
            live?.let {
                runCatching { core.rememberMembership(nick, it.rows) }
                ui.onMain {
                    ui.faveRows = it.rows
                    ui.favePage = it.page
                    ui.faveLast = it.lastPage
                }
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        TextField(
            value = ui.listNick,
            onValueChange = { ui.listNick = it },
            singleLine = true,
            label = { Text("Favorites nick") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(
                onDone = {
                    ui.setPref(core, Prefs.LIST_NICK, ui.listNick)
                    ui.favePage = 1u
                    load(1u)
                },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        ui.requestText?.let { Text(it, color = t.red) }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val measured = (maxHeight / 56.dp).toInt().coerceAtLeast(1).toUInt()
            fit = lockPaneFit(measured, ui.faveFit) { ui.faveFit = it }
            LaunchedEffect(ui.listNick, ui.nick, fit) { load(ui.favePage) }
            Column {
                val rows = ui.faveRows
                rows.forEach { row ->
                    FaveRowView(
                        ui,
                        core,
                        row,
                        afk && ui.canRequest && faveRequestable(
                            row.lastrequested,
                            row.lastplayed,
                            row.requestcount,
                            now,
                        ),
                    )
                }
                if (rows.isEmpty()) {
                    Text("Empty nick is an empty list.", color = t.muted, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(
                onClick = {
                    val nick = ui.listNickOrConnection()
                    ui.offMain {
                        val all = mutableListOf<FaveRow>()
                        val last = runCatching { core.favesLastPage(nick) }.getOrDefault(1u)
                        var p = 1u
                        while (p <= last) {
                            all += runCatching { core.fetchFaves(nick, p) }.getOrDefault(emptyList())
                            p++
                        }
                        val pick = all.filter {
                            it.tracksId > 0 && faveRequestable(
                                it.lastrequested,
                                it.lastplayed,
                                it.requestcount,
                                now,
                            )
                        }
                            .randomOrNull()
                        val r = if (pick == null) {
                            null
                        } else {
                            runCatching { core.requestTrack(pick.tracksId) }.getOrNull()
                        }
                        ui.onMain {
                            ui.requestText = r?.text ?: "None requestable"
                        }
                    }
                },
                enabled = afk && ui.canRequest && ui.listNickOrConnection().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = t.accent),
            ) { Text("Request random") }
            PagerBar(page = ui.favePage, last = ui.faveLast, onPage = { load(it) })
        }
    }
}

@Composable
private fun FaveRowView(ui: UiState, core: RadioCore, row: FaveRow, can: Boolean) {
    val t = LocalTokens.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            if (row.artist.isBlank()) row.title else "${row.artist} - ${row.title}",
            color = t.text,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = {
                ui.offMain {
                    val r = runCatching { core.requestTrack(row.tracksId) }.getOrNull()
                    ui.onMain { ui.requestText = r?.text ?: "request failed" }
                }
            },
            enabled = can,
            colors = ButtonDefaults.textButtonColors(contentColor = t.accent),
        ) { Text("Request") }
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
    Column(Modifier.fillMaxWidth()) {
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
