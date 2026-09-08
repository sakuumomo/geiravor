package io.r_a_d.geiravor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.r_a_d.geiravor.compat.setApplicationNightMode
import io.r_a_d.geiravor.settings.SecretsStore
import io.r_a_d.geiravor.theme.GeiravorTheme
import io.r_a_d.geiravor.theme.LocalTokens
import uniffi.geiravor_core.FaveKind
import uniffi.geiravor_core.RadioCore
import uniffi.geiravor_core.themePackIsNight

@Composable
fun GeiravorRoot(
    ui: UiState,
    core: RadioCore,
    secrets: SecretsStore,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onGain: (Float) -> Unit,
    onFave: () -> Unit,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(ui.pack) {
        setApplicationNightMode(ctx, themePackIsNight(ui.pack))
    }
    LaunchedEffect(ui.status?.np, ui.listNick, ui.nick) {
        val nick = ui.listNickOrConnection()
        val s = ui.status
        ui.offMain {
            val hit = if (s == null || nick.isEmpty()) {
                false
            } else {
                runCatching {
                    core.membershipHas(nick, if (s.isAfk) s.trackId else 0, s.np)
                }.getOrDefault(false)
            }
            ui.onMain { if (!ui.faveBusy) ui.heartFilled = hit }
        }
    }
    GeiravorTheme(ui.pack) {
        val t = LocalTokens.current
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val twoPane = maxWidth >= 840.dp &&
                LocalConfiguration.current.smallestScreenWidthDp >= 600
            val tabs = if (twoPane) paneTabs() else phoneTabs()
            LaunchedEffect(twoPane) {
                if (twoPane && ui.tab == BottomTab.NowPlaying) {
                    ui.tab = BottomTab.Songs
                }
            }
            var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
            CompositionLocalProvider(
                LocalWallpaperRoot provides WallpaperRoot(rootCoords, rootCoords?.size ?: androidx.compose.ui.unit.IntSize.Zero),
            ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (t.wallpaper == null) t.background else Color.Transparent)
                    .onGloballyPositioned { rootCoords = it },
            ) {
                t.wallpaper?.let { res ->
                    Image(
                        painterResource(res),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Scaffold(
                    containerColor = Color.Transparent,
                    bottomBar = {
                        NavigationBar(containerColor = t.surface.copy(alpha = if (t.glass) 0.85f else 1f)) {
                            tabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = ui.tab == tab,
                                    onClick = { ui.tab = tab },
                                    icon = {
                                        Icon(
                                            when (tab) {
                                                BottomTab.NowPlaying -> Icons.Filled.GraphicEq
                                                BottomTab.Songs -> Icons.AutoMirrored.Filled.QueueMusic
                                                BottomTab.Board -> Icons.AutoMirrored.Filled.Article
                                                BottomTab.Settings -> Icons.Filled.Settings
                                            },
                                            contentDescription = tab.label,
                                        )
                                    },
                                    label = {
                                        Text(
                                            tab.label,
                                            maxLines = 2,
                                            textAlign = TextAlign.Center,
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = t.highlight,
                                        selectedTextColor = t.highlight,
                                        indicatorColor = t.highlight.copy(alpha = 0.25f),
                                        unselectedIconColor = t.muted,
                                        unselectedTextColor = t.muted,
                                    ),
                                )
                            }
                        }
                    },
                ) { padding ->
                    val body = Modifier.padding(padding).fillMaxSize()
                    if (twoPane) {
                        Row(body) {
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                NowPlayingScreen(ui, onPlay, onStop, onGain, onFave, showThread = false)
                            }
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                TabBody(ui, core, secrets, onPlay, onStop, onGain, onFave)
                            }
                        }
                    } else {
                        Box(body) {
                            TabBody(ui, core, secrets, onPlay, onStop, onGain, onFave)
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun TabBody(
    ui: UiState,
    core: RadioCore,
    secrets: SecretsStore,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onGain: (Float) -> Unit,
    onFave: () -> Unit,
) {
    when (ui.tab) {
        BottomTab.NowPlaying -> NowPlayingScreen(ui, onPlay, onStop, onGain, onFave, showThread = true)
        BottomTab.Songs -> SongsScreen(ui, core)
        BottomTab.Board -> BoardScreen(ui, core)
        BottomTab.Settings -> SettingsScreen(ui, core, secrets)
    }
}

fun tapFave(ui: UiState, core: RadioCore, secrets: SecretsStore) {
    if (ui.faveBusy) return
    ui.faveBusy = true
    val was = ui.heartFilled
    ui.heartFilled = !was
    val cfg = ui.faveConfig(secrets)
    val catalog = ui.status?.let { if (it.isAfk) it.trackId else 0L } ?: 0L
    Thread({
        val result = core.addFave(cfg, was, catalog)
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            ui.faveBusy = false
            when (result.kind) {
                FaveKind.SUCCESS -> {
                    ui.heartFilled = result.favorited
                    ui.faveError = null
                    ui.faveErrorFading = false
                }
                FaveKind.NOOP -> {
                    ui.heartFilled = was
                    ui.faveErrorFading = false
                    ui.faveError = if (result.message.isBlank()) "Set a nick in Settings" else result.message
                }
                FaveKind.FAILED -> {
                    ui.heartFilled = was
                    ui.faveErrorFading = false
                    ui.faveError = result.message.ifBlank { "Fave failed" }
                }
            }
        }
    }, "geiravor-fave").start()
}
