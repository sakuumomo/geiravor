package io.r_a_d.geiravor.data

import android.content.Context
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import coil.memory.MemoryCache
import io.r_a_d.geiravor.ui.NewsPolicy
import io.r_a_d.geiravor.ui.PanePolicy
import uniffi.geiravor_core.NewsArticle
import uniffi.geiravor_core.NewsAuthor
import uniffi.geiravor_core.NewsComment

object NewsStore {
    data class Ram(
        val htmlLast: Int = 1,
        val pages: Map<Int, List<NewsArticle>> = emptyMap(),
        val visible: Int = 0,
        val uiPage: Int = 1,
        val opened: Map<Long, Pair<NewsArticle, List<NewsComment>>> = emptyMap(),
    ) {
        fun rows(): List<NewsArticle> {
            val out = ArrayList<NewsArticle>()
            for (i in 1..htmlLast) {
                val page = pages[i] ?: break
                out.addAll(page)
            }
            return out
        }

        fun ids(): Set<Long> = rows().map { it.id }.toSet()
    }

    data class ListPaint(
        val uiPage: Int,
        val visible: Int,
        val lastPage: Int,
        val articles: List<NewsArticle>,
    )

    private val lock = Any()

    @Volatile
    private var ram: Ram = Ram()

    @Volatile
    var listPaint: ListPaint? = null

    fun snapshot(): Ram = synchronized(lock) { ram }

    fun freezeVisible(measured: Int): Int = synchronized(lock) {
        ram = ram.copy(visible = PanePolicy.freezeVisible(ram.visible, measured))
        ram.visible
    }

    fun setUiPage(page: Int) {
        synchronized(lock) { ram = ram.copy(uiPage = page.coerceAtLeast(1)) }
    }

    fun putHtmlPage(htmlPage: Int, htmlLast: Int, articles: List<NewsArticle>, dropLater: Boolean) {
        synchronized(lock) {
            val last = htmlLast.coerceAtLeast(1)
            val pages = ram.pages.toMutableMap()
            pages[htmlPage] = articles
            if (dropLater) {
                pages.keys.filter { it > 1 }.forEach { key ->
                    if (key != htmlPage) {
                        pages.remove(key)
                    }
                }
            }
            pages.keys.filter { it > last }.forEach { pages.remove(it) }
            ram = ram.copy(htmlLast = last, pages = pages)
        }
    }

    fun rememberOpened(article: NewsArticle, comments: List<NewsComment>) {
        if (article.id <= 0) {
            return
        }
        synchronized(lock) {
            ram = ram.copy(opened = ram.opened + (article.id to (article to comments)))
        }
    }

    fun opened(id: Long): Pair<NewsArticle, List<NewsComment>>? = synchronized(lock) {
        ram.opened[id]
    }

    /** List chrome plus any stored body/comments, for the first paint before GET. */
    fun seedArticle(list: NewsArticle): Pair<NewsArticle, List<NewsComment>> {
        val hit = opened(list.id)
        if (hit == null) {
            return list to emptyList()
        }
        val stored = hit.first
        return list.copy(
            title = list.title.ifEmpty { stored.title },
            header = list.header.ifEmpty { stored.header },
            text = stored.text.ifEmpty { list.text },
            updatedAt = list.updatedAt.ifEmpty { stored.updatedAt },
        ) to hit.second
    }

    fun clearForTests() {
        synchronized(lock) { ram = Ram() }
        listPaint = null
    }

    fun mergeListRow(existing: NewsArticleEntity?, incoming: NewsArticle): NewsArticleEntity {
        val row = fromArticle(incoming)
        val kept = existing?.text.orEmpty()
        return if (row.text.isEmpty() && kept.isNotEmpty()) {
            row.copy(text = kept)
        } else {
            row
        }
    }

    fun toArticle(entity: NewsArticleEntity): NewsArticle = NewsArticle(
        id = entity.id,
        title = entity.title,
        header = entity.header,
        text = entity.text,
        updatedAt = entity.updatedAt,
        author = NewsAuthor(
            id = entity.authorId,
            user = entity.authorUser,
            role = entity.authorRole,
        ),
    )

    fun fromArticle(article: NewsArticle): NewsArticleEntity = NewsArticleEntity(
        id = article.id,
        title = article.title,
        header = article.header,
        text = article.text,
        updatedAt = article.updatedAt,
        authorId = article.author.id,
        authorUser = article.author.user,
        authorRole = article.author.role,
    )

    fun toComment(entity: NewsCommentEntity): NewsComment = NewsComment(
        id = entity.commentId,
        author = entity.author,
        postedAt = entity.postedAt,
        body = entity.body,
        role = entity.role,
    )

    fun fromComments(articleId: Long, comments: List<NewsComment>): List<NewsCommentEntity> =
        comments.map { comment ->
            NewsCommentEntity(
                articleId = articleId,
                commentId = comment.id,
                author = comment.author,
                postedAt = comment.postedAt,
                body = comment.body,
                role = comment.role,
            )
        }

    suspend fun hydrate(db: GeiravorDb): Ram {
        val stored = db.news().allPages()
        if (stored.isEmpty()) {
            return snapshot()
        }
        val htmlLast = stored.maxOf { maxOf(it.lastPage, it.page) }.coerceAtLeast(1)
        val pages = HashMap<Int, List<NewsArticle>>()
        for (row in stored) {
            val ids = parseIds(row.ids)
            if (ids.isEmpty()) {
                pages[row.page] = emptyList()
                continue
            }
            val found = db.news().articles(ids).associateBy { it.id }
            pages[row.page] = ids.mapNotNull { id -> found[id]?.let(::toArticle) }
        }
        synchronized(lock) {
            ram = ram.copy(htmlLast = htmlLast, pages = ram.pages + pages)
        }
        return snapshot()
    }

    suspend fun loadPage(db: GeiravorDb, page: Int): Pair<Int, List<NewsArticle>>? {
        val row = db.news().page(page) ?: return null
        val ids = parseIds(row.ids)
        if (ids.isEmpty()) {
            return row.lastPage to emptyList()
        }
        val found = db.news().articles(ids).associateBy { it.id }
        val articles = ids.mapNotNull { id -> found[id]?.let(::toArticle) }
        return row.lastPage to articles
    }

    fun pageWrite(
        existingPage: NewsPageEntity?,
        existingById: Map<Long, NewsArticleEntity>,
        page: Int,
        lastPage: Int,
        articles: List<NewsArticle>,
    ): Pair<NewsPageEntity, List<NewsArticleEntity>>? {
        val nextPage = NewsPageEntity(
            page = page,
            lastPage = lastPage,
            ids = articles.joinToString(",") { it.id.toString() },
        )
        val nextArticles = articles.map { article ->
            mergeListRow(existingById[article.id], article)
        }
        val pageChanged = DiskPolicy.changed(existingPage, nextPage)
        val articlesChanged = nextArticles.any { row ->
            DiskPolicy.changed(existingById[row.id], row)
        }
        if (!pageChanged && !articlesChanged) {
            return null
        }
        return nextPage to nextArticles
    }

    suspend fun savePage(db: GeiravorDb, page: Int, lastPage: Int, articles: List<NewsArticle>): Boolean {
        val existingPage = db.news().page(page)
        val ids = articles.map { it.id }
        val existingById = if (ids.isEmpty()) {
            emptyMap()
        } else {
            db.news().articles(ids).associateBy { it.id }
        }
        val write = pageWrite(existingPage, existingById, page, lastPage, articles) ?: return false
        db.news().upsertPage(write.first)
        write.second.forEach { row ->
            db.news().upsertArticle(row)
        }
        return true
    }

    suspend fun loadArticle(db: GeiravorDb, id: Long): Pair<NewsArticle, List<NewsComment>>? {
        opened(id)?.let { return it }
        val article = db.news().article(id) ?: return null
        val comments = db.news().comments(id).map(::toComment)
        val pair = toArticle(article) to comments
        if (pair.first.text.isNotEmpty() || comments.isNotEmpty()) {
            rememberOpened(pair.first, pair.second)
        }
        return pair
    }

    fun articleWrite(
        existing: NewsArticleEntity?,
        existingComments: List<NewsCommentEntity>,
        article: NewsArticle,
        comments: List<NewsComment>,
    ): Pair<NewsArticleEntity, List<NewsCommentEntity>>? {
        val next = fromArticle(article)
        val nextComments = fromComments(article.id, comments)
        if (!DiskPolicy.changed(existing, next) && !DiskPolicy.changed(existingComments, nextComments)) {
            return null
        }
        return next to nextComments
    }

    suspend fun saveArticle(
        db: GeiravorDb,
        article: NewsArticle,
        comments: List<NewsComment>,
    ): Boolean {
        val existing = db.news().article(article.id)
        val existingComments = db.news().comments(article.id)
        val write = articleWrite(existing, existingComments, article, comments)
        if (write != null) {
            db.news().upsertArticle(write.first)
            if (DiskPolicy.changed(existingComments, write.second)) {
                db.news().deleteComments(article.id)
                if (write.second.isNotEmpty()) {
                    db.news().upsertComments(write.second)
                }
            }
        }
        val stored = if (article.text.isNotEmpty()) {
            article
        } else {
            existing?.let(::toArticle) ?: article
        }
        rememberOpened(stored, comments)
        return write != null
    }

    suspend fun pruneCatalog(
        context: Context,
        db: GeiravorDb,
        extraIds: Collection<Long> = emptyList(),
    ) {
        val snap = snapshot()
        val keepPages = snap.pages.keys.toList().ifEmpty { listOf(1) }
        prune(context, db, keepPages, snap.ids() + snap.opened.keys + extraIds)
    }

    suspend fun prune(
        context: Context,
        db: GeiravorDb,
        keepPages: Collection<Int>,
        keepArticleIds: Collection<Long>,
    ) {
        val keepPageList = keepPages.distinct()
        val before = db.news().allPages()
        val droppedIds = mutableSetOf<Long>()
        before.forEach { row ->
            if (row.page !in keepPages) {
                droppedIds.addAll(parseIds(row.ids))
            }
        }
        droppedIds.removeAll(keepArticleIds.toSet())
        if (keepPageList.isEmpty()) {
            db.news().deletePagesNotIn(listOf(-1))
        } else {
            db.news().deletePagesNotIn(keepPageList)
        }
        val keepArticles = (keepArticleIds + before.filter { it.page in keepPages }
            .flatMap { parseIds(it.ids) }).distinct()
        if (keepArticles.isEmpty()) {
            db.news().deleteArticlesNotIn(listOf(-1L))
            db.news().deleteCommentsNotIn(listOf(-1L))
        } else {
            droppedIds.forEach { id ->
                db.news().article(id)?.let { evictImages(context, it.text) }
            }
            db.news().deleteArticlesNotIn(keepArticles)
            db.news().deleteCommentsNotIn(keepArticles)
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    fun evictUrls(context: Context, urls: Collection<String>) {
        val loader = context.imageLoader
        urls.forEach { url ->
            loader.memoryCache?.remove(MemoryCache.Key(url))
            loader.diskCache?.remove(url)
        }
    }

    fun evictImages(context: Context, html: String) {
        evictUrls(context, NewsPolicy.imageUrls(html))
    }

    private fun parseIds(raw: String): List<Long> =
        raw.split(',').mapNotNull { it.trim().toLongOrNull() }
}
