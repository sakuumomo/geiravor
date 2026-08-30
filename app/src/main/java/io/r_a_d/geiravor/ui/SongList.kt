package io.r_a_d.geiravor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    showTitle: Boolean = true,
) {
    RadioCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (showTitle) {
                Text(
                    title,
                    color = RadioTheme.text,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                HorizontalDivider(color = RadioTheme.border)
            }
            if (entries.isEmpty()) {
                Text(
                    text = "Nothing yet",
                    color = RadioTheme.muted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                entries.forEach { entry ->
                    val whenText = if (queue) {
                        relativeQueue(entry.timestamp, current)
                    } else {
                        relativeLastPlayed(entry.timestamp, current)
                    }
                    val color = if (entry.isRequest) RadioTheme.blue else RadioTheme.text
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = entry.meta,
                            color = color,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
        }
    }
}
