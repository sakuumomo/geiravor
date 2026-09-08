package io.r_a_d.geiravor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
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

/** First, nearby, one `...` jump, last. Prev/next are the bar edges. */
fun pagerSlots(page: UInt, last: UInt): List<PagerSlot> {
    if (last <= 1u) return emptyList()
    val cur = page.coerceIn(1u, last)
    val mid = linkedSetOf<UInt>()
    mid.add(1u)
    for (d in -1..1) {
        val n = cur.toInt() + d
        if (n in 1..last.toInt() && n.toUInt() != last) mid.add(n.toUInt())
    }
    val sorted = mid.sorted()
    val out = mutableListOf<PagerSlot>(PagerSlot.Prev)
    sorted.forEach { out += PagerSlot.Page(it) }
    val lastNearby = sorted.lastOrNull() ?: 1u
    if (last > lastNearby + 1u) out += PagerSlot.Ellipsis
    out += PagerSlot.Page(last)
    out += PagerSlot.Next
    return out
}

@Composable
fun PagerBar(page: UInt, last: UInt, onPage: (UInt) -> Unit) {
    val t = LocalTokens.current
    if (last <= 1u) return
    var jump by remember { mutableStateOf(false) }
    var jumpText by remember { mutableStateOf("") }
    val slots = pagerSlots(page, last)
    val measurer = rememberTextMeasurer()
    val digitStyle = TextStyle.Default
    val widest = (0..9).maxOf { measurer.measure(it.toString(), digitStyle).size.width }
    val slotPx = widest * last.toString().length
    val slotDp = with(LocalDensity.current) { slotPx.toDp() } + 8.dp
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = { onPage(page - 1u) }, enabled = page > 1u) {
            Text("<", color = t.highlight)
        }
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        slots.filter { it !is PagerSlot.Prev && it !is PagerSlot.Next }.forEach { slot ->
            when (slot) {
                PagerSlot.Prev, PagerSlot.Next -> {}
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
                    Text("…", color = t.muted, maxLines = 1)
                }
                is PagerSlot.Page -> {
                    val on = slot.n == page
                    Box(
                        Modifier
                            .widthIn(min = slotDp)
                            .background(
                                if (on) t.highlight.copy(alpha = if (t.glass) 0.92f else 0.35f)
                                else t.surface.copy(alpha = if (t.glass) 0.8f else 1f),
                                RoundedCornerShape(6.dp),
                            )
                            .clickable { onPage(slot.n) }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            slot.n.toString(),
                            color = if (on) t.text else t.muted,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible,
                        )
                    }
                }
            }
        }
        }
        TextButton(onClick = { onPage(page + 1u) }, enabled = page < last) {
            Text(">", color = t.highlight)
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
