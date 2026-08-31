package io.r_a_d.geiravor.ui

import android.text.SpannableString
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.View
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.r_a_d.geiravor.GeiravorApp
import io.r_a_d.geiravor.data.GeiravorDb
import io.r_a_d.geiravor.data.NewsStore
import io.r_a_d.geiravor.radio.SessionCache
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.geiravor_core.NewsArticle
import uniffi.geiravor_core.NewsComment
import uniffi.geiravor_core.RadioCore

@Composable
fun NewsScreen(
    radio: RadioCore,
    modifier: Modifier = Modifier,
) {
    val start = remember { NewsStore.snapshot() }
    val startPage = start.uiPage.takeIf { it > 0 } ?: SessionCache.newsCurrent()
    val startFit = start.visible.coerceAtLeast(1)
    var page by remember { mutableStateOf(startPage) }
    var lastPage by remember { mutableStateOf(PanePolicy.lastPage(start.rows().size, startFit)) }
    var visible by remember { mutableStateOf(start.visible) }
    var articles by remember {
        mutableStateOf(PanePolicy.slice(start.rows(), startPage, startFit))
    }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(articles.isEmpty() && start.rows().isEmpty()) }
    var selected by remember { mutableStateOf<NewsArticle?>(null) }
    var slotDp by remember { mutableStateOf(0f) }
    val context = LocalContext.current

    fun showRam(uiPage: Int, fit: Int) {
        val vis = fit.coerceAtLeast(1)
        val rows = NewsStore.snapshot().rows()
        val last = PanePolicy.lastPage(rows.size, vis)
        val clamped = PagerPolicy.clampPage(uiPage, last)
        val shown = PanePolicy.slice(rows, clamped, vis)
        articles = shown
        lastPage = last
        if (shown.isNotEmpty() || rows.isNotEmpty()) {
            loading = false
        }
        NewsStore.setUiPage(clamped)
        SessionCache.putNewsCurrent(clamped)
        NewsStore.listPaint = NewsStore.ListPaint(
            uiPage = clamped,
            visible = vis,
            lastPage = last,
            articles = shown,
        )
    }

    LaunchedEffect(Unit) {
        val db = GeiravorDb.get(context)
        withContext(Dispatchers.IO) { NewsStore.hydrate(db) }
        showRam(page, visible.takeIf { it > 0 } ?: startFit)
    }

    LaunchedEffect(radio, page, visible) {
        if (visible <= 0) {
            return@LaunchedEffect
        }
        error = null
        showRam(page, visible)
        val db = GeiravorDb.get(context)
        suspend fun pull(htmlPage: Int) {
            val previous = NewsStore.snapshot().pages[htmlPage]?.map { it.id }
            val fetched = withContext(Dispatchers.IO) { radio.news(htmlPage) }
            val htmlLast = fetched.lastPage.toInt().coerceAtLeast(1)
            withContext(Dispatchers.IO) {
                NewsStore.savePage(db, htmlPage, htmlLast, fetched.data)
            }
            val dropLater = htmlPage == 1 &&
                previous != null &&
                previous != fetched.data.map { it.id }
            NewsStore.putHtmlPage(htmlPage, htmlLast, fetched.data, dropLater)
            (context.applicationContext as? GeiravorApp)?.refreshTheme(processStart = false)
        }
        try {
            if (page == 1 || NewsStore.snapshot().pages[1] == null) {
                pull(1)
                showRam(page, visible)
            }
            val htmlLast = NewsStore.snapshot().htmlLast.coerceAtLeast(1)
            for (htmlPage in 2..htmlLast) {
                if (NewsStore.snapshot().pages[htmlPage] == null) {
                    pull(htmlPage)
                    showRam(page, visible)
                }
            }
            withContext(Dispatchers.IO) {
                NewsStore.pruneCatalog(
                    context,
                    db,
                    extraIds = selected?.id?.let { listOf(it) }.orEmpty(),
                )
            }
            val last = PanePolicy.lastPage(NewsStore.snapshot().rows().size, visible)
            val clamped = PagerPolicy.clampPage(page, last)
            if (clamped != page) {
                page = clamped
            } else {
                showRam(clamped, visible)
            }
        } catch (err: CancellationException) {
            throw err
        } catch (err: Exception) {
            if (NewsStore.snapshot().rows().isEmpty() && articles.isEmpty()) {
                lastPage = 1
                error = err.message?.takeIf { it.isNotBlank() } ?: "Couldn't load news"
            } else {
                showRam(page, visible)
            }
        }
        if (articles.isNotEmpty() || NewsStore.snapshot().rows().isNotEmpty()) {
            loading = false
        }
    }

    val article = selected
    if (article != null) {
        BackHandler { selected = null }
        NewsArticlePane(
            radio = radio,
            article = article,
            onBack = { selected = null },
            modifier = modifier,
        )
        return
    }

    RadioPane(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        error?.let { text ->
            Text(
                text,
                color = RadioTheme.red,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val slot = slotDp.takeIf { it > 0f }
                ?: (PanePolicy.NEWS_CARD_DP + PanePolicy.NEWS_GAP_DP).toFloat()
            val measured = PanePolicy.thatFitSlot(maxHeight.value, slot)
            LaunchedEffect(measured) {
                if (visible != measured) {
                    visible = measured
                }
            }
            when {
                loading && articles.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Loading…", color = RadioTheme.muted, fontSize = 16.sp)
                    }
                }
                articles.isEmpty() && error == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nothing yet", color = RadioTheme.muted, fontSize = 16.sp)
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(NewsPolicy.LIST_GAP_DP.dp),
                    ) {
                        articles.forEach { item ->
                            NewsListCard(
                                article = item,
                                onOpen = { selected = item },
                                onSlotDp = { height ->
                                    val next = height + NewsPolicy.LIST_GAP_DP
                                    if (next > slotDp) {
                                        slotDp = next
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        if (PagerPolicy.visible(lastPage)) {
            PageTabs(
                current = page,
                last = lastPage,
                onPage = { page = it },
            )
        }
    }
    }
}

@Composable
private fun NewsByline(
    name: String,
    role: String,
    suffix: String,
    size: Int = 13,
) {
    Row {
        Text(
            name,
            color = NewsPolicy.nameColor(role),
            fontSize = size.sp,
        )
        if (suffix.isNotEmpty()) {
            Text(
                " · $suffix",
                color = RadioTheme.muted,
                fontSize = size.sp,
            )
        }
    }
}

@Composable
private fun NewsListCard(
    article: NewsArticle,
    onOpen: () -> Unit,
    onSlotDp: (Float) -> Unit = {},
) {
    val density = LocalDensity.current
    val blurb = NewsPolicy.plainText(article.header)
    RadioCard(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { size ->
                onSlotDp(with(density) { size.height.toDp().value })
            }
            .clickable(onClick = onOpen),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                article.title,
                color = RadioTheme.text,
                fontSize = 16.sp,
            )
            NewsByline(
                name = article.author.user,
                role = article.author.role,
                suffix = NewsPolicy.displayDate(article.updatedAt),
            )
            if (blurb.isNotEmpty()) {
                Text(
                    blurb,
                    color = RadioTheme.muted,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NewsArticlePane(
    radio: RadioCore,
    article: NewsArticle,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val seeded = remember(article.id) { NewsStore.seedArticle(article) }
    var displayed by remember(article.id) { mutableStateOf(seeded.first) }
    var comments by remember(article.id) { mutableStateOf(seeded.second) }
    var commentError by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var jumpTo by remember { mutableStateOf<Long?>(null) }
    val blocks = remember(displayed.text) { NewsPolicy.articleBlocks(displayed.text) }
    val composerIndex = 1 + blocks.size

    val context = LocalContext.current
    LaunchedEffect(radio, article.id) {
        if (article.id <= 0) {
            comments = emptyList()
            return@LaunchedEffect
        }
        val db = GeiravorDb.get(context)
        val cached = withContext(Dispatchers.IO) { NewsStore.loadArticle(db, article.id) }
        if (cached != null) {
            val (stored, storedComments) = cached
            if (stored.text.isNotEmpty()) {
                displayed = displayed.copy(
                    text = stored.text,
                    title = displayed.title.ifEmpty { stored.title },
                )
            }
            if (storedComments.isNotEmpty()) {
                comments = storedComments
            }
        }
        try {
            val full = withContext(Dispatchers.IO) { radio.newsArticle(article.id) }
            val fetchedComments = withContext(Dispatchers.IO) { radio.newsComments(article.id) }
            val previousText = displayed.text
            displayed = displayed.copy(text = full.text.ifEmpty { displayed.text })
            comments = fetchedComments
            commentError = null
            withContext(Dispatchers.IO) {
                NewsStore.evictUrls(
                    context,
                    NewsPolicy.droppedImageUrls(previousText, displayed.text),
                )
                NewsStore.saveArticle(db, displayed, fetchedComments)
            }
        } catch (err: CancellationException) {
            throw err
        } catch (err: Exception) {
            if (comments.isEmpty() && displayed.text.isEmpty()) {
                commentError = err.message?.takeIf { it.isNotBlank() } ?: "Couldn't load article"
            }
        }
    }

    LaunchedEffect(jumpTo, comments, blocks.size) {
        val id = jumpTo ?: return@LaunchedEffect
        val idx = comments.indexOfFirst { it.id == id }
        if (idx >= 0) {
            listState.animateScrollToItem(composerIndex + 1 + idx)
            jumpTo = null
        }
    }

    val density = LocalDensity.current
    var backHeightPx by remember { mutableStateOf(0) }
    val backPad = if (backHeightPx > 0) {
        with(density) { backHeightPx.toDp() }
    } else {
        48.dp
    }
    Box(modifier = modifier.fillMaxSize()) {
        RadioPane(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = backPad,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        item(key = "head") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(displayed.title, color = RadioTheme.text, fontSize = 20.sp)
                NewsByline(
                    name = displayed.author.user,
                    role = displayed.author.role,
                    suffix = NewsPolicy.displayDate(displayed.updatedAt),
                    size = 13,
                )
            }
        }
        itemsIndexed(blocks, key = { index, block -> "b$index:${block.kind}:${block.url}" }) { _, block ->
            when (block.kind) {
                NewsBlock.Kind.Html -> NewsHtml(block.html, onCommentLink = { jumpTo = it })
                NewsBlock.Kind.Image -> NewsImage(block.url)
            }
        }
        item(key = "composer") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Comments", color = RadioTheme.text, fontSize = 16.sp)
                if (article.id > 0) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { if (it.length <= NewsPolicy.COMMENT_MAX) draft = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        placeholder = { Text("Enter a comment…") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = RadioTheme.text,
                            unfocusedTextColor = RadioTheme.text,
                            focusedBorderColor = RadioTheme.blue,
                            unfocusedBorderColor = RadioTheme.border,
                            cursorColor = RadioTheme.blue,
                            focusedPlaceholderColor = RadioTheme.muted,
                            unfocusedPlaceholderColor = RadioTheme.muted,
                        ),
                    )
                    Text(
                        "${NewsPolicy.COMMENT_MAX - draft.length} characters remaining. Markdown. Anonymous.",
                        color = RadioTheme.muted,
                        fontSize = 12.sp,
                    )
                    Button(
                        onClick = {
                            busy = true
                            commentError = null
                            scope.launch {
                                try {
                                    comments = withContext(Dispatchers.IO) {
                                        val posted = radio.postNewsComment(article.id, draft)
                                        NewsStore.saveArticle(
                                            GeiravorDb.get(context),
                                            displayed,
                                            posted,
                                        )
                                        posted
                                    }
                                    draft = ""
                                } catch (err: CancellationException) {
                                    throw err
                                } catch (err: Exception) {
                                    commentError =
                                        err.message?.takeIf { it.isNotBlank() } ?: "Comment failed"
                                }
                                busy = false
                            }
                        },
                        enabled = NewsPolicy.commentAllowed(draft) && !busy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = radioButtonColors(),
                    ) {
                        Text("Submit")
                    }
                }
                commentError?.let { text ->
                    Text(text, color = RadioTheme.red, fontSize = 13.sp)
                }
            }
        }
        items(comments, key = { it.id }) { comment ->
            NewsCommentCard(
                comment = comment,
                onQuote = {
                    draft = NewsPolicy.quote(draft, comment.id)
                    scope.launch { listState.animateScrollToItem(composerIndex) }
                },
                onCommentLink = { jumpTo = it },
            )
        }
        }
        }
        Text(
            "← News",
            color = RadioTheme.link,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.TopStart)
                .onSizeChanged { backHeightPx = it.height }
                .clickable(onClick = onBack)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun NewsCommentCard(
    comment: NewsComment,
    onQuote: () -> Unit,
    onCommentLink: (Long) -> Unit,
) {
    RadioCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            NewsByline(
                name = comment.author,
                role = comment.role,
                suffix = comment.postedAt,
                size = 12,
            )
            Text(
                "#${comment.id}",
                color = RadioTheme.link,
                fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = onQuote),
            )
            NewsPolicy.articleBlocks(comment.body).forEach { block ->
                when (block.kind) {
                    NewsBlock.Kind.Html -> NewsHtml(block.html, onCommentLink = onCommentLink)
                    NewsBlock.Kind.Image -> NewsImage(block.url)
                }
            }
        }
    }
}

@Composable
private fun NewsImage(url: String) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .crossfade(false)
            .memoryCacheKey(url)
            .diskCacheKey(url)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NewsHtml(
    html: String,
    modifier: Modifier = Modifier,
    onCommentLink: (Long) -> Unit = {},
) {
    val textColor = RadioTheme.text.toArgb()
    val linkColor = RadioTheme.link.toArgb()
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            TextView(context).apply {
                setTextColor(textColor)
                setLinkTextColor(linkColor)
                textSize = 15f
                setLineSpacing(0f, 1.15f)
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { view ->
            view.setTextColor(textColor)
            view.setLinkTextColor(linkColor)
            val spanned = HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
            val spannable = SpannableString(spanned)
            val urls = spannable.getSpans(0, spannable.length, URLSpan::class.java)
            for (span in urls) {
                val id = NewsPolicy.commentAnchorId(span.url) ?: continue
                val start = spannable.getSpanStart(span)
                val end = spannable.getSpanEnd(span)
                val flags = spannable.getSpanFlags(span)
                spannable.removeSpan(span)
                spannable.setSpan(
                    object : ClickableSpan() {
                        override fun onClick(widget: View) {
                            onCommentLink(id)
                        }

                        override fun updateDrawState(ds: TextPaint) {
                            ds.color = linkColor
                            ds.isUnderlineText = false
                        }
                    },
                    start,
                    end,
                    flags,
                )
            }
            view.text = spannable
        },
    )
}
