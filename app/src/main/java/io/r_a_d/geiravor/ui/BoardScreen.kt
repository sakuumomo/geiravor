package io.r_a_d.geiravor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.r_a_d.geiravor.theme.LocalTokens
import uniffi.geiravor_core.NewsCard
import uniffi.geiravor_core.RadioCore
import uniffi.geiravor_core.RoleColor
import uniffi.geiravor_core.ScheduleDay
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
fun BoardScreen(ui: UiState, core: RadioCore) {
    val t = LocalTokens.current
    val sections = BoardSection.entries
    LaunchedEffect(ui.boardSection) {
        when (ui.boardSection) {
            BoardSection.News -> ui.offMain {
                val cached = runCatching { core.cachedNewsList(1u) }.getOrNull()
                ui.onMain { if (cached != null) ui.news = cached }
                val live = runCatching { core.fetchNewsList(1u) }.getOrNull()
                ui.onMain { if (live != null) ui.news = live }
            }
            BoardSection.Schedule -> ui.offMain {
                val cached = runCatching { core.cachedSchedule() }.getOrDefault(emptyList())
                ui.onMain { ui.schedule = cached }
                val live = runCatching { core.fetchSchedule() }.getOrDefault(cached)
                ui.onMain { ui.schedule = live }
            }
            BoardSection.Staff -> ui.offMain {
                val cached = runCatching { core.cachedStaff() }.getOrDefault(emptyList())
                ui.onMain { ui.staff = cached }
                val live = runCatching { core.fetchStaff() }.getOrDefault(cached)
                ui.onMain { ui.staff = live }
            }
        }
    }
    val hug = ui.boardSection == BoardSection.Schedule ||
        (ui.boardSection == BoardSection.Staff && ui.article == null)
    Pane(Modifier.fillMaxSize(), hug = hug) {
        SectionTabs(
            labels = sections.map { it.label },
            selected = ui.boardSection.ordinal,
            onSelect = {
                ui.article = null
                ui.boardSection = sections[it]
            },
        )
        Spacer(Modifier.height(12.dp))
        when (ui.boardSection) {
            BoardSection.News -> if (ui.article != null) {
                ArticlePane(ui, core)
            } else {
                NewsListPane(ui, core)
            }
            BoardSection.Schedule -> SchedulePane(ui)
            BoardSection.Staff -> StaffPane(ui)
        }
    }
}

@Composable
private fun NewsListPane(ui: UiState, core: RadioCore) {
    val t = LocalTokens.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val n = (maxHeight / 72.dp).toInt().coerceAtLeast(1)
        val cards = ui.news?.cards.orEmpty().take(n)
        Column {
            cards.forEach { card ->
                NewsCardRow(card, t.text, t.muted) {
                    ui.offMain {
                        val cached = runCatching { core.cachedNewsArticle(card.id) }.getOrNull()
                        cached?.let { ui.onMain { ui.article = it } }
                        val live = runCatching { core.fetchNewsArticle(card.id) }.getOrNull()
                        ui.onMain { if (live != null) ui.article = live }
                    }
                }
            }
            if (cards.isEmpty()) {
                Text("News loads from the site.", color = t.muted)
            }
            val page = ui.news?.page ?: 1u
            val last = ui.news?.lastPage ?: 1u
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(
                    onClick = {
                        val p = page - 1u
                        ui.offMain {
                            val live = runCatching { core.fetchNewsList(p) }.getOrNull()
                            ui.onMain { if (live != null) ui.news = live }
                        }
                    },
                    enabled = page > 1u,
                ) { Text("<") }
                Text("$page / $last", color = t.muted)
                TextButton(
                    onClick = {
                        val p = page + 1u
                        ui.offMain {
                            val live = runCatching { core.fetchNewsList(p) }.getOrNull()
                            ui.onMain { if (live != null) ui.news = live }
                        }
                    },
                    enabled = page < last,
                ) { Text(">") }
            }
        }
    }
}

@Composable
private fun NewsCardRow(card: NewsCard, text: androidx.compose.ui.graphics.Color, muted: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp)) {
        Text(card.title, color = text, maxLines = 1)
        Text("${card.author} · ${card.date}", color = muted, maxLines = 1)
        if (card.header.isNotBlank()) {
            Text(card.header, color = muted, maxLines = 2)
        }
    }
}

@Composable
private fun ArticlePane(ui: UiState, core: RadioCore) {
    val t = LocalTokens.current
    val article = ui.article ?: return
    var draft by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        Text(
            "← News",
            color = t.link,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { ui.article = null }
                .padding(8.dp),
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(article.title, color = t.text, modifier = Modifier.padding(top = 8.dp))
            Text(article.author, color = t.muted)
            Text(article.body, color = t.text, modifier = Modifier.padding(vertical = 8.dp))
            article.comments.forEach { c ->
                val color = when (c.role) {
                    RoleColor.STAFF -> t.green
                    RoleColor.DJ -> t.blue
                    RoleColor.DEV -> t.red
                    RoleColor.NONE -> t.text
                }
                Row(Modifier.padding(top = 8.dp)) {
                    Text("${c.author}  ${c.whenUtc}  ", color = color)
                    Text(
                        "#${c.id}",
                        color = t.link,
                        modifier = Modifier.clickable {
                            draft = draft + ">>${c.id}\n"
                        },
                    )
                }
                Text(c.body, color = t.text)
            }
        }
        TextField(
            value = draft,
            onValueChange = { if (it.length <= 500) draft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Comment") },
        )
        TextButton(
            onClick = {
                val body = draft.trim()
                if (body.isEmpty()) return@TextButton
                ui.offMain {
                    val live = runCatching { core.postComment(article.id, body) }.getOrNull()
                    ui.onMain {
                        if (live != null) {
                            ui.article = live
                            draft = ""
                        }
                    }
                }
            },
        ) { Text("Submit", color = t.accent) }
    }
}

@Composable
private fun SchedulePane(ui: UiState) {
    val t = LocalTokens.current
    val today = LocalDate.now().dayOfWeek
    Column(Modifier.verticalScroll(rememberScrollState()).fillMaxWidth()) {
        ui.schedule.forEach { day ->
            val highlight = weekdayToday(day, today)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (day.image.isNotBlank()) {
                    AsyncImage(
                        model = day.image,
                        contentDescription = day.owner,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).padding(end = 8.dp),
                        contentScale = ContentScale.Crop,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        day.weekday + if (day.owner.isNotBlank()) " · ${day.owner}" else "",
                        color = if (highlight) t.highlight else t.text,
                    )
                    val body = if (ui.scheduleLocal) {
                        ScheduleTimes.rewrite(
                            day.body,
                            day.weekday,
                            uniffi.geiravor_core.estZoneId(),
                            java.util.TimeZone.getDefault().id,
                            java.time.Instant.now().epochSecond,
                        )
                    } else {
                        day.body
                    }
                    Text(body, color = t.muted)
                }
            }
        }
        if (ui.schedule.isEmpty()) {
            Text("Schedule loads when you open this tab.", color = t.muted)
        }
    }
}

private fun weekdayToday(day: ScheduleDay, today: DayOfWeek): Boolean =
    when (day.weekday) {
        "Monday" -> today == DayOfWeek.MONDAY
        "Tuesday" -> today == DayOfWeek.TUESDAY
        "Wednesday" -> today == DayOfWeek.WEDNESDAY
        "Thursday" -> today == DayOfWeek.THURSDAY
        "Friday" -> today == DayOfWeek.FRIDAY
        "Saturday" -> today == DayOfWeek.SATURDAY
        "Sunday" -> today == DayOfWeek.SUNDAY
        else -> false
    }

@Composable
private fun StaffPane(ui: UiState) {
    val t = LocalTokens.current
    Column(Modifier.verticalScroll(rememberScrollState()).fillMaxWidth()) {
        ui.staff.forEach { group ->
            Text(
                group.label,
                color = t.text,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
            )
            val color = when (group.role) {
                RoleColor.STAFF -> t.green
                RoleColor.DJ -> t.blue
                RoleColor.DEV -> t.red
                RoleColor.NONE -> t.text
            }
            group.cards.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { card ->
                        Column(Modifier.weight(1f).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (card.image.isNotBlank()) {
                                AsyncImage(
                                    model = card.image,
                                    contentDescription = card.name,
                                    modifier = Modifier.size(96.dp).clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            Text(card.name, color = color)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        if (ui.staff.isEmpty()) {
            Text("Staff loads when you open this tab.", color = t.muted)
        }
    }
}
