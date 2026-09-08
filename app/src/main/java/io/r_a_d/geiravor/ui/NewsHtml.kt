package io.r_a_d.geiravor.ui

import android.graphics.Color as AndroidColor
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import io.r_a_d.geiravor.theme.LocalTokens
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Locale

sealed class NewsBlock {
    data class Text(val html: String) : NewsBlock()
    data class Image(val url: String) : NewsBlock()
}

/** Site `radio.js` `localTime`: weekday + short date + 24h clock; `data-dur` is a range. */
fun formatNewsLocalTime(
    unixSeconds: Long,
    durMs: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val start = Instant.ofEpochSecond(unixSeconds).atZone(zone)
    val dateTime = DateTimeFormatterBuilder()
        .appendPattern("EEE, ")
        .appendLocalized(FormatStyle.SHORT, null)
        .appendPattern(", ")
        .appendPattern("H:mm")
        .toFormatter(locale)
    val clock = DateTimeFormatter.ofPattern("H:mm", locale)
    if (durMs <= 0L) {
        return start.format(dateTime)
    }
    val end = Instant.ofEpochMilli(unixSeconds * 1000 + durMs).atZone(zone)
    return if (end.toLocalDate() != start.toLocalDate()) {
        "${start.format(dateTime)} - ${end.format(dateTime)}"
    } else {
        "${start.format(dateTime)} - ${end.format(clock)}"
    }
}

fun rewriteLocalTimes(
    html: String,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val re = Regex(
        """<time([^>]*?)datetime="(\d+)"([^>]*?)>([^<]*)</time>""",
        RegexOption.IGNORE_CASE,
    )
    return re.replace(html) { m ->
        val attrs = m.groupValues[1] + m.groupValues[3]
        if (!attrs.contains("data-type=\"local\"")) return@replace m.value
        val unix = m.groupValues[2].toLongOrNull() ?: return@replace m.value
        val dur = Regex("""data-dur="(\d+)"""").find(attrs)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        formatNewsLocalTime(unix, dur, zone, locale)
    }
}

fun commentListIndex(ids: List<Long>, id: Long, headerItems: Int = 1): Int? {
    val i = ids.indexOf(id)
    if (i < 0) return null
    return headerItems + i
}

fun newsBlocks(html: String): List<NewsBlock> {
    val rewritten = rewriteLocalTimes(html)
    val out = mutableListOf<NewsBlock>()
    val re = Regex("""<img[^>]+src="([^"]+)"[^>]*>""", RegexOption.IGNORE_CASE)
    var last = 0
    re.findAll(rewritten).forEach { m ->
        if (m.range.first > last) {
            out.add(NewsBlock.Text(rewritten.substring(last, m.range.first)))
        }
        out.add(NewsBlock.Image(m.groupValues[1]))
        last = m.range.last + 1
    }
    if (last < rewritten.length) out.add(NewsBlock.Text(rewritten.substring(last)))
    return out.filter {
        when (it) {
            is NewsBlock.Text -> it.html.isNotBlank()
            is NewsBlock.Image -> true
        }
    }
}

fun commentJumpId(url: String): Long? {
    val t = url.trim()
    val m = Regex("""#comment-(\d+)""").find(t) ?: return null
    return m.groupValues[1].toLongOrNull()
}

@Composable
fun NewsHtml(html: String, onJump: (Long) -> Unit) {
    val t = LocalTokens.current
    val blocks = remember(html) { newsBlocks(html) }
    blocks.forEach { block ->
        when (block) {
            is NewsBlock.Image -> StationMedia(
                url = block.url,
                autoplay = false,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .padding(vertical = 8.dp),
                contentScale = ContentScale.FillWidth,
            )
            is NewsBlock.Text -> AndroidView(
                factory = { ctx ->
                    TextView(ctx).apply {
                        setTextColor(t.text.toArgb())
                        setLinkTextColor(t.link.toArgb())
                        setBackgroundColor(AndroidColor.TRANSPARENT)
                        movementMethod = LinkMovementMethod.getInstance()
                    }
                },
                update = { tv ->
                    val spanned = HtmlCompat.fromHtml(block.html, HtmlCompat.FROM_HTML_MODE_COMPACT)
                    tv.text = spanned
                    tv.movementMethod = object : LinkMovementMethod() {
                        override fun onTouchEvent(
                            widget: TextView,
                            buffer: android.text.Spannable,
                            event: android.view.MotionEvent,
                        ): Boolean {
                            if (event.action == android.view.MotionEvent.ACTION_UP) {
                                val x = event.x.toInt() - widget.totalPaddingLeft + widget.scrollX
                                val y = event.y.toInt() - widget.totalPaddingTop + widget.scrollY
                                val line = widget.layout.getLineForVertical(y)
                                val off = widget.layout.getOffsetForHorizontal(line, x.toFloat())
                                val spans = buffer.getSpans(off, off, URLSpan::class.java)
                                val id = spans.firstOrNull()?.url?.let { commentJumpId(it) }
                                if (id != null) {
                                    onJump(id)
                                    return true
                                }
                            }
                            return super.onTouchEvent(widget, buffer, event)
                        }
                    }
                },
            )
        }
    }
}
