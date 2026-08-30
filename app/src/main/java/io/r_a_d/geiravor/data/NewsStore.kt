package io.r_a_d.geiravor.data

import android.content.Context
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import coil.memory.MemoryCache
import io.r_a_d.geiravor.ui.NewsPolicy
import uniffi.geiravor_core.NewsArticle
import uniffi.geiravor_core.NewsAuthor
import uniffi.geiravor_core.NewsComment

object NewsStore {
    data class ListPaint(
        val uiPage: Int,
        val visible: Int,
        val lastPage: Int,
        val articles: List<NewsArticle>,
    )

    @Volatile
    var listPaint: ListPaint? = null

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

    suspend fun savePage(db: GeiravorDb, page: Int, lastPage: Int, articles: List<NewsArticle>) {
        db.news().upsertPage(
            NewsPageEntity(
                page = page,
                lastPage = lastPage,
                ids = articles.joinToString(",") { it.id.toString() },
            ),
        )
        articles.forEach { article ->
            val existing = db.news().article(article.id)
            db.news().upsertArticle(mergeListRow(existing, article))
        }
    }

    suspend fun loadArticle(db: GeiravorDb, id: Long): Pair<NewsArticle, List<NewsComment>>? {
        val article = db.news().article(id) ?: return null
        val comments = db.news().comments(id).map(::toComment)
        return toArticle(article) to comments
    }

    suspend fun saveArticle(
        db: GeiravorDb,
        article: NewsArticle,
        comments: List<NewsComment>,
    ) {
        db.news().upsertArticle(fromArticle(article))
        db.news().deleteComments(article.id)
        if (comments.isNotEmpty()) {
            db.news().upsertComments(fromComments(article.id, comments))
        }
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
