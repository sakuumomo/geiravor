package io.r_a_d.geiravor.playback

import android.content.Context
import android.graphics.Paint
import android.text.TextPaint
import android.widget.TextView
import io.r_a_d.geiravor.compat.displayWidthPx

/** Shade line 2: ellipsize artist so `| {dj}` stays. No character cap. */
object ShadeLine {
    fun fit(artist: String, dj: String, maxWidthPx: Float, measure: (String) -> Float): String {
        val a = artist.trim()
        val d = dj.trim()
        if (a.isEmpty()) return d
        if (d.isEmpty()) return a
        val suffix = " | $d"
        val sw = measure(suffix)
        if (sw >= maxWidthPx) return d
        val budget = maxWidthPx - sw
        if (measure(a) <= budget) return a + suffix
        var lo = 0
        var hi = a.length
        var best = "…"
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val cand = a.take(mid).trimEnd() + "…"
            if (measure(cand) <= budget) {
                best = cand
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return best + suffix
    }

    fun fitForShade(context: Context, artist: String, dj: String): String {
        val density = context.resources.displayMetrics.density
        val max = displayWidthPx(context) - (64f + 48f + 16f) * density
        val paint = line2Paint(context)
        return fit(artist, dj, max.coerceAtLeast(1f)) { s -> paint.measureText(s) }
    }

    private fun line2Paint(context: Context): TextPaint {
        val tv = TextView(context)
        tv.setTextAppearance(android.R.style.TextAppearance_Material_Notification)
        return TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = tv.textSize
            typeface = tv.typeface
            letterSpacing = tv.letterSpacing
        }
    }
}
