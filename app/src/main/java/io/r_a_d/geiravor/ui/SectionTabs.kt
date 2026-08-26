package io.r_a_d.geiravor.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SectionTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (labels.size < 2) {
        return
    }
    TabRow(
        selectedTabIndex = selected.coerceIn(0, labels.lastIndex),
        modifier = modifier.fillMaxWidth(),
        containerColor = RadioTheme.surface,
        contentColor = RadioTheme.text,
        indicator = { positions ->
            if (selected in positions.indices) {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(positions[selected]),
                    color = RadioTheme.blue,
                )
            }
        },
        divider = {},
    ) {
        labels.forEachIndexed { index, label ->
            Tab(
                selected = index == selected,
                onClick = { onSelect(index) },
                text = { TabLabel(label) },
                selectedContentColor = RadioTheme.text,
                unselectedContentColor = RadioTheme.muted,
            )
        }
    }
}
