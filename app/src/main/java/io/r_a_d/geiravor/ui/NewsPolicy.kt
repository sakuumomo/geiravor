package io.r_a_d.geiravor.ui

import androidx.compose.ui.graphics.Color
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class NewsBlock(
    val kind: Kind,
    val html: String = "",
    val url: String = "",
) {
    enum class Kind { Html, Image }
}

object NewsPolicy {
    const val IMAGE_HOST = "static.r-a-d.io"
    const val COMMENT_MAX = 500
    const val SERVER_PER_PAGE = PanePolicy.NEWS_PER_PAGE
    const val LIST_GAP_DP = PanePolicy.NEWS_GAP_DP
    /** Title + author + two-line blurb + padding + gap; sized so a page does not scroll. */
    const val LIST_CARD_DP = PanePolicy.NEWS_CARD_DP

    private val dateOnly = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private val timeTag = Regex("""(?is)<time\b([^>]*)>(.*?)</time>""")
    private val imgTag = Regex("""(?is)<img\b[^>]*\bsrc\s*=\s*["']([^"']+)["'][^>]*>""")
    private val attr = { name: String -> Regex("""(?i)\b$name\s*=\s*["']([^"']*)["']""") }

    fun displayDate(updatedAt: String): String {
        val trimmed = updatedAt.trim()
        if (trimmed.isEmpty()) {
            return ""
        }
        val date = trimmed.take(10)
        return if (dateOnly.matches(date)) date else trimmed
    }

    fun plainText(html: String): String =
        html
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace(Regex("(?i)</p>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\n+"), "\n")
            .trim()

    fun imageUrlAllowed(url: String): Boolean {
        val trimmed = url.trim()
        val schemeSep = trimmed.indexOf("://")
        if (schemeSep <= 0) {
            return false
        }
        val scheme = trimmed.substring(0, schemeSep)
        if (!scheme.equals("https", ignoreCase = true)) {
            return false
        }
        val rest = trimmed.substring(schemeSep + 3)
        val hostPort = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = if (hostPort.startsWith("[")) {
            hostPort.substringAfter('[').substringBefore(']')
        } else {
            hostPort.substringBefore(':')
        }
        return host.equals(IMAGE_HOST, ignoreCase = true)
    }

    fun quote(existing: String, id: Long): String {
        val prefix = existing.trimEnd()
        val line = ">>$id\n"
        return if (prefix.isEmpty()) line else "$prefix\n$line"
    }

    fun commentAllowed(body: String): Boolean {
        val trimmed = body.trim()
        return trimmed.isNotEmpty() && trimmed.length <= COMMENT_MAX
    }

    fun cardsThatFit(availableDp: Float): Int = PanePolicy.newsThatFit(availableDp)

    fun nameColor(role: String, fallback: Color = RadioTheme.muted): Color =
        when (role) {
            "staff" -> RadioTheme.green
            "dj" -> if (RadioTheme.glass) RadioTheme.link else RadioTheme.blue
            "dev" -> RadioTheme.red
            else -> fallback
        }

    fun commentAnchorId(href: String): Long? {
        val trimmed = href.trim()
        val hash = trimmed.substringAfter('#', missingDelimiterValue = "")
        if (!hash.startsWith("comment-")) {
            return null
        }
        val before = trimmed.substringBefore('#', missingDelimiterValue = "")
        if (before.isNotEmpty()) {
            val path = if (before.contains("://")) {
                val rest = before.substringAfter("://")
                val host = rest.substringBefore('/')
                if (!host.equals("r-a-d.io", ignoreCase = true) &&
                    !host.equals("www.r-a-d.io", ignoreCase = true)
                ) {
                    return null
                }
                rest.substringAfter('/', missingDelimiterValue = "")
            } else {
                before.trimStart('/')
            }
            if (path.isNotEmpty() && !path.startsWith("news")) {
                return null
            }
        }
        return hash.removePrefix("comment-").takeWhile { it.isDigit() }.toLongOrNull()?.takeIf { it > 0 }
    }

    fun rewriteTimes(
        html: String,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): String {
        val full = DateTimeFormatter.ofPattern("EEE, M/d/yyyy, HH:mm", locale)
        val timeOnly = DateTimeFormatter.ofPattern("HH:mm", locale)
        return timeTag.replace(html) { match ->
            val attrs = match.groupValues[1]
            val inner = match.groupValues[2]
            val type = attrValue(attrs, "data-type")
            val datetime = attrValue(attrs, "datetime")
            val dur = attrValue(attrs, "data-dur").toLongOrNull() ?: 0L
            if (type.equals("local", ignoreCase = true)) {
                val epoch = datetime.toLongOrNull()
                if (epoch != null) {
                    formatLocal(epoch, dur, zone, full, timeOnly)
                } else {
                    inner
                }
            } else {
                inner
            }
        }
    }

    fun articleBlocks(
        html: String,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): List<NewsBlock> {
        val rewritten = rewriteTimes(html, zone, locale)
        val blocks = mutableListOf<NewsBlock>()
        var last = 0
        for (match in imgTag.findAll(rewritten)) {
            val before = rewritten.substring(last, match.range.first)
            if (before.isNotBlank()) {
                blocks.add(NewsBlock(NewsBlock.Kind.Html, html = before.trim()))
            }
            val url = match.groupValues[1].trim()
            if (imageUrlAllowed(url)) {
                blocks.add(NewsBlock(NewsBlock.Kind.Image, url = url))
            }
            last = match.range.last + 1
        }
        val tail = rewritten.substring(last)
        if (tail.isNotBlank()) {
            blocks.add(NewsBlock(NewsBlock.Kind.Html, html = tail.trim()))
        }
        return blocks
    }

    fun imageUrls(html: String): List<String> =
        articleBlocks(html).mapNotNull { block ->
            block.url.takeIf { it.isNotEmpty() }
        }

    fun droppedImageUrls(previous: String, next: String): List<String> {
        val keep = imageUrls(next).toSet()
        return imageUrls(previous).filter { it !in keep }
    }

    private fun attrValue(attrs: String, name: String): String =
        attr(name).find(attrs)?.groupValues?.getOrNull(1).orEmpty()

    private fun formatLocal(
        epochSecs: Long,
        durationMs: Long,
        zone: ZoneId,
        full: DateTimeFormatter,
        timeOnly: DateTimeFormatter,
    ): String {
        val start = ZonedDateTime.ofInstant(Instant.ofEpochSecond(epochSecs), zone)
        if (durationMs <= 0L) {
            return start.format(full)
        }
        val end = start.plus(Duration.ofMillis(durationMs))
        val startDay = LocalDate.from(start)
        val endDay = LocalDate.from(end)
        return if (startDay == endDay) {
            "${start.format(full)} - ${end.format(timeOnly)}"
        } else {
            "${start.format(full)} - ${end.format(full)}"
        }
    }
}
