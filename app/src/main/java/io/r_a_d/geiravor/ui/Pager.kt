package io.r_a_d.geiravor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.r_a_d.geiravor.theme.LocalTokens

sealed class PagerSlot {
    data object Prev : PagerSlot()
    data object Next : PagerSlot()
    data object Ellipsis : PagerSlot()
    data class Page(val n: UInt) : PagerSlot()
}

/** First, nearby window, last. `...` is jump-to-page. Prev/next are the bar edges. */
fun pagerSlots(page: UInt, last: UInt): List<PagerSlot> {
    if (last <= 1u) return emptyList()
    val cur = page.coerceIn(1u, last)
    val nearby = linkedSetOf<UInt>()
    nearby.add(1u)
    nearby.add(last)
    for (d in -1..1) {
        val n = cur.toInt() + d
        if (n in 1..last.toInt()) nearby.add(n.toUInt())
    }
    val sorted = nearby.sorted()
    val mid = mutableListOf<PagerSlot>()
    var prev: UInt? = null
    for (n in sorted) {
        if (prev != null && n > prev + 1u) mid.add(PagerSlot.Ellipsis)
        mid.add(PagerSlot.Page(n))
        prev = n
    }
    return listOf(PagerSlot.Prev) + mid + listOf(PagerSlot.Next)
}

@Composable
fun PagerBar(page: UInt, last: UInt, onPage: (UInt) -> Unit) {
    val t = LocalTokens.current
    if (last <= 1u) return
    var jump by remember { mutableStateOf(false) }
    var jumpText by remember { mutableStateOf("") }
    val slots = pagerSlots(page, last)
    val measurer = rememberTextMeasurer()
    val widest = (0..9).maxOf { measurer.measure(it.toString()).size.width }
    val slotPx = widest * last.toString().length
    val slotDp = with(LocalDensity.current) { slotPx.toDp() }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        slots.forEach { slot ->
            when (slot) {
                PagerSlot.Prev -> TextButton(onClick = { onPage(page - 1u) }, enabled = page > 1u) {
                    Text("<", color = t.highlight)
                }
                PagerSlot.Next -> TextButton(onClick = { onPage(page + 1u) }, enabled = page < last) {
                    Text(">", color = t.highlight)
                }
                PagerSlot.Ellipsis -> Box(
                    Modifier
                        .background(
                            t.surface.copy(alpha = if (t.glass) 0.8f else 1f),
                            RoundedCornerShape(6.dp),
                        )
                        .clickable { jump = true; jumpText = "" }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("…", color = t.muted)
                }
                is PagerSlot.Page -> {
                    val on = slot.n == page
                    Box(
                        Modifier
                            .width(slotDp)
                            .background(
                                if (on) t.highlight.copy(alpha = if (t.glass) 0.92f else 0.35f)
                                else t.surface.copy(alpha = if (t.glass) 0.8f else 1f),
                                RoundedCornerShape(6.dp),
                            )
                            .clickable { onPage(slot.n) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            slot.n.toString(),
                            color = if (on) t.text else t.muted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
    if (jump) {
        AlertDialog(
            containerColor = if (t.glass) Color(0xFF1A1A1A) else t.surface,
            onDismissRequest = { jump = false },
            confirmButton = {
                TextButton(onClick = {
                    jumpText.toUIntOrNull()?.let { onPage(it.coerceIn(1u, last)) }
                    jump = false
                }) { Text("Go") }
            },
            dismissButton = { TextButton(onClick = { jump = false }) { Text("Cancel") } },
            title = { Text("Page") },
            text = {
                TextField(
                    value = jumpText,
                    onValueChange = { if (it.length <= last.toString().length) jumpText = it.filter { c -> c.isDigit() } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            },
        )
    }
}
