package io.r_a_d.geiravor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uniffi.geiravor_core.Status

@Composable
fun SongsScreen(
    status: Status?,
    streamDown: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (StreamStatus.showBanner(streamDown)) {
            Text(StreamStatus.banner, color = RadioTheme.red, fontSize = 16.sp)
        }
        SongSection(
            title = "Last Played",
            entries = status?.lastPlayed.orEmpty(),
            current = status?.current ?: 0,
            queue = false,
            modifier = Modifier.fillMaxWidth(),
        )
        if (SongListPolicy.showQueue(status?.isAfkStream == true) && status != null) {
            SongSection(
                title = "Queue",
                entries = status.queue,
                current = status.current,
                queue = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
