package io.r_a_d.geiravor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.r_a_d.geiravor.compat.evictNewsImages
import io.r_a_d.geiravor.theme.LocalTokens
import io.r_a_d.geiravor.theme.Tokens
import uniffi.geiravor_core.NewsCard
import uniffi.geiravor_core.RadioCore
import uniffi.geiravor_core.RoleColor
import uniffi.geiravor_core.ScheduleDay
import uniffi.geiravor_core.StaffGroup
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
fun BoardScreen(ui: UiState, core: RadioCore) {
    val t = LocalTokens.current
    val sections = BoardSection.entries
    LaunchedEffect(ui.boardSection) {
        when (ui.boardSection) {
            BoardSection.News -> {}
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
        ui.boardSection == BoardSection.Staff
    Pane(hug = hug) {
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
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val measured = (maxHeight / InnerSection.NEWS_ROW_DP.dp).toInt().coerceAtLeast(1).toUInt()
            val fit = lockPaneFit(measured, ui.newsFit) { ui.newsFit = it }
            LaunchedEffect(fit, ui.newsPage) {
                ui.offMain {
                    val cached = runCatching { core.cachedNewsWindow(ui.newsPage, fit) }.getOrNull()
                    ui.onMain { if (cached != null) ui.news = cached }
                    val live = runCatching { core.newsWindow(ui.newsPage, fit) }.getOrNull()
                    ui.onMain { if (live != null) ui.news = live }
                }
            }
            val cards = ui.news?.cards.orEmpty()
            Column(verticalArrangement = Arrangement.spacedBy(InnerSection.GAP_DP.dp)) {
                cards.forEach { card ->
                    NewsCardRow(card) {
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
            }
        }
        PagerBar(
            page = ui.news?.page ?: 1u,
            last = ui.news?.lastPage ?: 1u,
            onPage = { ui.newsPage = it },
        )
    }
}

@Composable
private fun NewsCardRow(card: NewsCard, onClick: () -> Unit) {
    val t = LocalTokens.current
    Column(innerRowModifier().clickable(onClick = onClick)) {
        Text(card.title, color = t.text, maxLines = 1)
        Row {
            Text(card.author, color = rolePaint(card.role, t), maxLines = 1)
            Text(" · ${card.date}", color = t.muted, maxLines = 1)
        }
        if (card.header.isNotBlank()) {
            Text(card.header, color = t.muted, maxLines = 2)
        }
    }
}

private fun rolePaint(role: RoleColor, t: io.r_a_d.geiravor.theme.Tokens) =
    when (role) {
        RoleColor.STAFF -> t.green
        RoleColor.DJ -> t.blue
        RoleColor.DEV -> t.red
        RoleColor.NONE -> t.muted
    }

@Composable
private fun ArticlePane(ui: UiState, core: RadioCore) {
    val t = LocalTokens.current
    val article = ui.article ?: return
    val ctx = LocalContext.current
    var draft by remember { mutableStateOf("") }
    var commentError by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(article.id, article.body, article.comments) {
        val html = articleImageHtml(article.body, article.comments.map { it.body })
        val urls = uniffi.geiravor_core.newsImageUrlsFor(html)
        val dropped = uniffi.geiravor_core.droppedNewsImagesFor(ui.newsCoilUrls, urls)
        ui.newsCoilUrls = urls
        if (dropped.isNotEmpty()) {
            ui.offMain { evictNewsImages(ctx, dropped) }
        }
    }
    fun jump(id: Long) {
        commentListIndex(article.comments.map { it.id }, id)?.let { idx ->
            scope.launch { listState.animateScrollToItem(idx) }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(top = 40.dp),
            ) {
                item {
                    InnerCard {
                        Text(article.title, color = t.text)
                        Text(article.author, color = rolePaint(article.role, t))
                        NewsHtml(article.body, onJump = { jump(it) })
                    }
                }
                itemsIndexed(article.comments, key = { _, c -> c.id }) { i, c ->
                    val color = when (c.role) {
                        RoleColor.STAFF -> t.green
                        RoleColor.DJ -> t.blue
                        RoleColor.DEV -> t.red
                        RoleColor.NONE -> t.text
                    }
                    InnerCard(
                        Modifier.padding(
                            top = if (i == 0) {
                                InnerSection.ARTICLE_COMMENT_GAP_DP.dp
                            } else {
                                InnerSection.GAP_DP.dp
                            },
                        ),
                    ) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(
                                c.author,
                                color = color,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "#${c.id}",
                                color = t.link,
                                modifier = Modifier.clickable {
                                    draft = quoteComment(draft, c.id)
                                },
                            )
                        }
                        if (c.whenUtc.isNotBlank()) {
                            Text(c.whenUtc, color = t.muted, maxLines = 1)
                        }
                        NewsHtml(c.body, onJump = { jump(it) })
                    }
                }
            }
            val filmHover = remember { MutableInteractionSource() }
            val filmHovered by filmHover.collectIsHoveredAsState()
            Text(
                "← News",
                color = t.link,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .clip(RoundedCornerShape(6.dp))
                    .hoverable(filmHover)
                    .background(
                        newsFilmFill(
                            filmHovered,
                            if (t.glass) Color.Black.copy(alpha = 0.5f) else t.surface,
                        ),
                    )
                    .clickable { ui.article = null }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
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
                if (body.isEmpty()) {
                    commentError = "Comment is empty"
                    return@TextButton
                }
                ui.offMain {
                    val live = runCatching { core.postComment(article.id, body) }.getOrNull()
                    ui.onMain {
                        if (live != null) {
                            ui.article = live
                            draft = ""
                            commentError = null
                        } else {
                            commentError = "Comment failed"
                        }
                    }
                }
            },
        ) { Text("Submit", color = t.accent) }
        commentError?.let { Text(it, color = t.red) }
    }
}

@Composable
private fun SchedulePane(ui: UiState) {
    val t = LocalTokens.current
    val today = LocalDate.now().dayOfWeek
    Column(
        Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(InnerSection.GAP_DP.dp),
    ) {
        ui.schedule.forEach { day ->
            val highlight = weekdayToday(day, today)
            Row(
                innerRowModifier(if (highlight) t.highlight else null),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    Modifier
                        .padding(end = 8.dp)
                        .size(56.dp)
                        .clip(RoundedCornerShape(6.dp)),
                ) {
                    if (day.image.isNotBlank()) {
                        StationMedia(
                            url = day.image,
                            autoplay = true,
                            contentDescription = day.owner,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    }
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
    val sw = LocalConfiguration.current.smallestScreenWidthDp
    val wide = StaffLayout.sideBySide(sw)
    Column(Modifier.verticalScroll(rememberScrollState()).fillMaxWidth()) {
        val staff = ui.staff.getOrNull(0)
        val dev = ui.staff.getOrNull(1)
        val rest = ui.staff.drop(2)
        if (wide && staff != null && dev != null) {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(Modifier.weight(1f)) {
                    StaffGroupBlock(staff, StaffLayout.columns(staff.role, sw))
                }
                Box(
                    Modifier
                        .padding(horizontal = 8.dp)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(t.border),
                )
                Box(Modifier.weight(1f)) {
                    StaffGroupBlock(dev, StaffLayout.columns(dev.role, sw))
                }
            }
            rest.forEach { StaffGroupBlock(it, StaffLayout.columns(it.role, sw)) }
        } else {
            ui.staff.forEach { StaffGroupBlock(it, StaffLayout.columns(it.role, sw)) }
        }
        if (ui.staff.isEmpty()) {
            Text("Staff loads when you open this tab.", color = t.muted)
        }
    }
}

@Composable
private fun StaffGroupBlock(group: StaffGroup, columns: Int) {
    val t = LocalTokens.current
    val color = staffRoleColor(group.role, t)
    Column(Modifier.fillMaxWidth()) {
        Text(
            group.label,
            color = t.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        )
        Box(Modifier.fillMaxWidth().height(2.dp).background(t.border))
        Spacer(Modifier.height(8.dp))
        group.cards.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { card ->
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(4.dp)
                            .then(innerChrome()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (card.image.isNotBlank()) {
                            StationMedia(
                                url = card.image,
                                autoplay = true,
                                contentDescription = card.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        Text(card.name, color = color, textAlign = TextAlign.Center)
                    }
                }
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private fun staffRoleColor(role: RoleColor, t: Tokens): Color =
    when (role) {
        RoleColor.STAFF -> t.green
        RoleColor.DJ -> t.blue
        RoleColor.DEV -> t.red
        RoleColor.NONE -> t.text
    }
