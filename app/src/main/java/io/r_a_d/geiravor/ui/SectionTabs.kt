package io.r_a_d.geiravor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

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
    val wallpaper = RadioTheme.wallpaper != null
    Box(modifier.fillMaxWidth()) {
        if (wallpaper) {
            if (RadioTheme.glass) {
                FrostBackdrop(Modifier.matchParentSize())
            } else {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(RadioTheme.surface.copy(alpha = 0.94f)),
                )
            }
        }
    TabRow(
        selectedTabIndex = selected.coerceIn(0, labels.lastIndex),
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (wallpaper) Color.Transparent else RadioTheme.surface,
        contentColor = RadioTheme.text,
        indicator = { positions ->
            if (selected in positions.indices) {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(positions[selected]),
                    color = RadioTheme.highlight,
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
}
