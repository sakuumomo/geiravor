package io.r_a_d.geiravor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uniffi.geiravor_core.ListEntry
import uniffi.geiravor_core.relativeLastPlayed
import uniffi.geiravor_core.relativeQueue

@Composable
fun SongSection(
    title: String,
    entries: List<ListEntry>,
    current: Long,
    queue: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            color = RadioTheme.text,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        entries.forEach { entry ->
            val whenText = if (queue) {
                relativeQueue(entry.timestamp, current)
            } else {
                relativeLastPlayed(entry.timestamp, current)
            }
            val color = if (entry.isRequest) RadioTheme.blue else RadioTheme.text
            Text(
                text = entry.meta,
                color = color,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = SongListPolicy.caption(entry.isRequest, whenText),
                color = RadioTheme.muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
