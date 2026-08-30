package io.r_a_d.geiravor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uniffi.geiravor_core.RadioCore
import uniffi.geiravor_core.Status

@Composable
fun SongsScreen(
    radio: RadioCore,
    status: Status?,
    streamDown: Boolean,
    canRequest: Boolean?,
    onCanRequest: (Boolean?) -> Unit,
    favesNick: String,
    homeNick: String,
    listFallback: String,
    onFavesNick: (String) -> Unit,
    onFavesNickPersist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val afk = status?.isAfkStream == true
    val sections = SectionLayout.songsSections(afk)
    var selected by remember { mutableStateOf(SectionLayout.SongsSection.LastPlayed) }
    val section = SectionLayout.clampSongsSection(selected, afk)

    Column(modifier = modifier.fillMaxSize()) {
        if (StreamStatus.showBanner(streamDown)) {
            Text(
                StreamStatus.banner,
                color = RadioTheme.red,
                fontSize = 16.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        SectionTabs(
            labels = sections.map { it.label },
            selected = sections.indexOf(section).coerceAtLeast(0),
            onSelect = { selected = sections[it] },
        )
        when (section) {
            SectionLayout.SongsSection.Request -> RequestPane(
                radio = radio,
                status = status,
                canRequest = canRequest,
                onCanRequest = onCanRequest,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            SectionLayout.SongsSection.Favorites -> FavoritesPane(
                radio = radio,
                status = status,
                nick = favesNick,
                homeNick = homeNick,
                listFallback = listFallback,
                onNick = onFavesNick,
                onNickPersist = onFavesNickPersist,
                canRequest = canRequest,
                onCanRequest = onCanRequest,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            else -> Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SongPane(
                    status = status,
                    section = section,
                    showTitle = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SongPane(
    status: Status?,
    section: SectionLayout.SongsSection,
    showTitle: Boolean,
    modifier: Modifier = Modifier,
) {
    val queue = section == SectionLayout.SongsSection.Queue
    SongSection(
        title = section.label,
        entries = if (queue) status?.queue.orEmpty() else status?.lastPlayed.orEmpty(),
        current = status?.current ?: 0,
        queue = queue,
        showTitle = showTitle,
        modifier = modifier,
    )
}
