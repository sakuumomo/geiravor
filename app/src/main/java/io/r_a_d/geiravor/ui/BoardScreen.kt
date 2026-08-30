package io.r_a_d.geiravor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.r_a_d.geiravor.radio.SessionCache
import uniffi.geiravor_core.RadioCore

@Composable
fun BoardScreen(
    radio: RadioCore,
    convertScheduleTimes: Boolean,
    modifier: Modifier = Modifier,
) {
    val sections = SectionLayout.boardSections()
    var selected by remember {
        mutableStateOf(
            sections.firstOrNull { it.label == SessionCache.boardSection() }
                ?: SectionLayout.BoardSection.News,
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        SectionTabs(
            labels = sections.map { it.label },
            selected = sections.indexOf(selected).coerceAtLeast(0),
            onSelect = {
                selected = sections[it]
                SessionCache.putBoardSection(sections[it].label)
            },
        )
        val pane = Modifier
            .weight(1f)
            .fillMaxWidth()
        when (selected) {
            SectionLayout.BoardSection.News -> NewsScreen(radio = radio, modifier = pane)
            SectionLayout.BoardSection.Schedule -> ScheduleScreen(
                radio = radio,
                convertTimes = convertScheduleTimes,
                modifier = pane,
            )
            SectionLayout.BoardSection.Staff -> StaffScreen(radio = radio, modifier = pane)
        }
    }
}
