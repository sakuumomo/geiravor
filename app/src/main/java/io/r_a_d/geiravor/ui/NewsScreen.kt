package io.r_a_d.geiravor.ui

import android.text.SpannableString
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.View
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
    var page by remember { mutableStateOf(SessionCache.newsCurrent()) }
    var lastPage by remember { mutableStateOf(1) }
    var visible by remember { mutableStateOf(1) }
    var articles by remember { mutableStateOf(listOf<NewsArticle>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<NewsArticle?>(null) }
    val context = LocalContext.current

    LaunchedEffect(radio, page, visible) {
        error = null
        val start = NewsPolicy.listStartIndex(page, visible)
        val serverPage = start / NewsPolicy.SERVER_PER_PAGE + 1
        val offset = start % NewsPolicy.SERVER_PER_PAGE
        val db = GeiravorDb.get(context)
        val cached = withContext(Dispatchers.IO) { NewsStore.loadPage(db, serverPage) }
        if (cached != null) {
            val (htmlLast, rows) = cached
            var shown = rows.drop(offset).take(visible)
            if (shown.size < visible) {
                val extra = withContext(Dispatchers.IO) { NewsStore.loadPage(db, serverPage + 1) }
                if (extra != null) {
                    shown = shown + extra.second.take(visible - shown.size)
                }
            }
            val lastCount = if (serverPage == htmlLast) rows.size else NewsPolicy.SERVER_PER_PAGE
            lastPage = NewsPolicy.listLastPage(NewsPolicy.listTotal(htmlLast, lastCount), visible)
            articles = shown
            loading = false
        } else {
            loading = true
        }
        try {
            val first = withContext(Dispatchers.IO) { radio.news(serverPage) }
            var shown = first.data.drop(offset).take(visible)
            val htmlLast = first.lastPage.toInt().coerceAtLeast(1)
            withContext(Dispatchers.IO) {
                NewsStore.savePage(db, serverPage, htmlLast, first.data)
            }
            if (shown.size < visible && first.currentPage.toInt() < htmlLast) {
                val next = withContext(Dispatchers.IO) {
                    radio.news(first.currentPage.toInt() + 1)
                }
                shown = shown + next.data.take(visible - shown.size)
                withContext(Dispatchers.IO) {
                    NewsStore.savePage(db, first.currentPage.toInt() + 1, htmlLast, next.data)
                }
            }
            val lastHtml = withContext(Dispatchers.IO) { radio.news(htmlLast) }
            withContext(Dispatchers.IO) {
                NewsStore.savePage(db, htmlLast, htmlLast, lastHtml.data)
                NewsStore.prune(
                    context,
                    db,
                    keepPages = listOf(serverPage, serverPage + 1, htmlLast),
                    keepArticleIds = selected?.id?.let { listOf(it) }.orEmpty(),
                )
            }
            val total = NewsPolicy.listTotal(htmlLast, lastHtml.data.size)
            val uiLast = NewsPolicy.listLastPage(total, visible)
            lastPage = uiLast
            val clamped = PagerPolicy.clampPage(page, uiLast)
            SessionCache.putNewsCurrent(clamped)
            if (clamped != page) {
                page = clamped
            } else {
                articles = shown
            }
        } catch (err: CancellationException) {
            throw err
        } catch (err: Exception) {
            if (articles.isEmpty()) {
                lastPage = 1
                error = err.message?.takeIf { it.isNotBlank() } ?: "Couldn't load news"
            }
        }
        loading = false
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
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
            val fit = NewsPolicy.cardsThatFit(maxHeight.value)
            LaunchedEffect(fit) {
                if (visible != fit) {
                    visible = fit
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
                            NewsListCard(article = item, onOpen = { selected = item })
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
) {
    val blurb = NewsPolicy.plainText(article.header)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = RadioTheme.surface),
        border = BorderStroke(1.dp, RadioTheme.border),
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
    var displayed by remember { mutableStateOf(article) }
    var comments by remember { mutableStateOf(listOf<NewsComment>()) }
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
                displayed = displayed.copy(text = stored.text)
            }
            comments = storedComments
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

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "head") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "← News",
                    color = RadioTheme.link,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable(onClick = onBack),
                )
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = RadioTheme.blue,
                            contentColor = RadioTheme.text,
                            disabledContainerColor = RadioTheme.border,
                            disabledContentColor = RadioTheme.muted,
                        ),
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

@Composable
private fun NewsCommentCard(
    comment: NewsComment,
    onQuote: () -> Unit,
    onCommentLink: (Long) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = RadioTheme.surface),
        border = BorderStroke(1.dp, RadioTheme.border),
    ) {
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
