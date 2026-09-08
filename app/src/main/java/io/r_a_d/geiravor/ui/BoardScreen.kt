package io.r_a_d.geiravor.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.r_a_d.geiravor.theme.LocalTokens

@Composable
fun BoardScreen(ui: UiState) {
    val t = LocalTokens.current
    val sections = BoardSection.entries
    val hug = ui.boardSection != BoardSection.News
    Pane(Modifier.fillMaxSize(), hug = hug) {
        SectionTabs(
            labels = sections.map { it.label },
            selected = ui.boardSection.ordinal,
            onSelect = { ui.boardSection = sections[it] },
        )
        Spacer(Modifier.height(12.dp))
        val copy = when (ui.boardSection) {
            BoardSection.News -> "News list and articles come from the site HTML."
            BoardSection.Schedule -> "Seven weekday rows, Monday first."
            BoardSection.Staff -> "Staff, Developers, DJs. No bio."
        }
        Text(copy, color = t.muted)
    }
}
