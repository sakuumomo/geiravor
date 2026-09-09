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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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
    LaunchedEffect(ui.status?.np, ui.committedListNick, ui.committedNick) {
        val nicks = ui.membershipNicks()
        val s = ui.status
        ui.offMain {
            val hit = if (s == null || nicks.isEmpty()) {
                false
            } else {
                nicks.any { nick ->
                    runCatching {
                        core.membershipHas(nick, if (s.isAfk) s.trackId else 0, s.np)
                    }.getOrDefault(false)
                }
            }
            ui.onMain { ui.paintHeart(hit) }
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
            var wallpaper by remember { mutableStateOf(WallpaperLayout()) }
            CompositionLocalProvider(LocalWallpaperLayout provides wallpaper) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(if (t.wallpaper == null) t.background else Color.Transparent),
            ) {
                t.wallpaper?.let { res ->
                    Image(
                        painterResource(res),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned {
                                wallpaper = WallpaperLayout(it.size, it.positionInRoot())
                            },
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
                            Box(
                                Modifier.weight(1f).fillMaxHeight(),
                                contentAlignment = Alignment.TopStart,
                            ) {
                                NowPlayingScreen(ui, onPlay, onStop, onGain, onFave, showThread = false)
                            }
                            Box(
                                Modifier.weight(1f).fillMaxHeight(),
                                contentAlignment = Alignment.TopStart,
                            ) {
                                TabBody(ui, core, secrets, onPlay, onStop, onGain, onFave)
                            }
                        }
                    } else {
                        Box(body, contentAlignment = Alignment.TopStart) {
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

fun tapFave(
    ui: UiState,
    core: RadioCore,
    secrets: SecretsStore,
    onHeart: () -> Unit = {},
) {
    if (!FaveTap.accept(ui.faveBusy, ui.faveTaps)) return
    val was = ui.heartFilled
    ui.heartFilled = !was
    onHeart()
    val status = ui.status
    val catalog = status?.let { if (it.isAfk) it.trackId else 0L } ?: 0L
    val job = FaveTap.Job(
        unfave = was,
        catalog = catalog,
        np = status?.np.orEmpty(),
        isAfk = status?.isAfk == true,
        trackId = status?.trackId ?: 0L,
    )
    ui.faveTaps = FaveTap.afterAccept(ui.faveBusy, ui.faveTaps)
    if (ui.faveBusy) {
        ui.faveQueue.addLast(job)
        return
    }
    ui.faveBusy = true
    runFave(ui, core, secrets, job, onHeart)
}

private fun runFave(
    ui: UiState,
    core: RadioCore,
    secrets: SecretsStore,
    job: FaveTap.Job,
    onHeart: () -> Unit,
) {
    val cfg = ui.faveConfig(secrets)
    val nicks = ui.membershipNicks()
    ui.offMain {
        val result = core.addFave(cfg, job.unfave, job.catalog, job.np, job.isAfk, job.trackId)
        if (result.kind == FaveKind.SUCCESS) {
            nicks.forEach { nick -> runCatching { core.revalidateMembership(nick) } }
        }
        ui.onMain {
            when (result.kind) {
                FaveKind.SUCCESS -> {
                    ui.faveError = null
                    ui.faveErrorFading = false
                    val next = ui.faveQueue.removeFirstOrNull()
                    if (next != null) {
                        runFave(ui, core, secrets, next, onHeart)
                    } else {
                        ui.heartFilled = result.favorited
                        ui.faveBusy = false
                        ui.faveTaps = 0
                    }
                }
                FaveKind.NOOP -> {
                    ui.heartFilled = job.unfave
                    ui.faveQueue.clear()
                    ui.faveBusy = false
                    ui.faveTaps = 0
                    ui.faveErrorFading = false
                    ui.faveError = if (result.message.isBlank()) "Set a nick in Settings" else result.message
                }
                FaveKind.FAILED -> {
                    ui.heartFilled = job.unfave
                    ui.faveQueue.clear()
                    ui.faveBusy = false
                    ui.faveTaps = 0
                    ui.faveErrorFading = false
                    ui.faveError = result.message.ifBlank { "Fave failed" }
                }
            }
            onHeart()
        }
    }
}
