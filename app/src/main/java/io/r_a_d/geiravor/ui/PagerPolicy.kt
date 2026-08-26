package io.r_a_d.geiravor.ui

object PagerPolicy {
    const val WINDOW = 3

    enum class Kind {
        Prev,
        Page,
        Jump,
        Next,
    }

    data class Item(
        val kind: Kind,
        val page: Int = 0,
        val enabled: Boolean = true,
        val current: Boolean = false,
    )

    fun visible(last: Int): Boolean = last > 1

    fun digitCount(n: Int): Int = n.coerceAtLeast(1).toString().length

    fun jumpInput(raw: String, last: Int): String =
        raw.filter { it.isDigit() }.take(digitCount(last))

    fun pageSlotWidthPx(digits: Int, widestDigitPx: Float): Float =
        digits.coerceAtLeast(1) * widestDigitPx

    fun clampPage(page: Int, last: Int): Int {
        val max = last.coerceAtLeast(1)
        return page.coerceIn(1, max)
    }

    fun clampJump(raw: Int, maxPage: Int): Int = clampPage(raw, maxPage)

    fun items(current: Int, last: Int): List<Item> {
        if (last < 1) {
            return emptyList()
        }
        val cur = clampPage(current, last)
        val out = mutableListOf<Item>()
        out += Item(Kind.Prev, page = cur - 1, enabled = cur > 1)
        out += Item(Kind.Page, page = 1, current = cur == 1)
        for (n in window(cur, last)) {
            out += Item(Kind.Page, page = n, current = n == cur)
        }
        if (last > 1) {
            out += Item(Kind.Jump)
            out += Item(Kind.Page, page = last, current = cur == last)
        }
        out += Item(Kind.Next, page = cur + 1, enabled = cur < last)
        return out
    }

    fun window(current: Int, last: Int): IntRange {
        if (last <= 2) {
            return IntRange.EMPTY
        }
        val lo = 2
        val hi = last - 1
        if (lo > hi) {
            return IntRange.EMPTY
        }
        val half = WINDOW / 2
        var start = current - half
        var end = start + WINDOW - 1
        if (start < lo) {
            end += lo - start
            start = lo
        }
        if (end > hi) {
            start -= end - hi
            end = hi
        }
        start = start.coerceAtLeast(lo)
        end = end.coerceAtMost(hi)
        if (start > end) {
            return IntRange.EMPTY
        }
        return start..end
    }

    fun label(item: Item): String =
        when (item.kind) {
            Kind.Prev -> "<"
            Kind.Next -> ">"
            Kind.Jump -> "..."
            Kind.Page -> item.page.toString()
        }
}
