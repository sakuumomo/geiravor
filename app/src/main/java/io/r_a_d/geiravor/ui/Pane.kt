package io.r_a_d.geiravor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.r_a_d.geiravor.compat.canBlur
import io.r_a_d.geiravor.theme.LocalTokens

/** Inner blocks: 1.dp border, pad, no second frost. `docs/spec/ui.md`. */
object InnerSection {
    const val GAP_DP = 8
    const val PAD_DP = 12
    const val ROW_PAD_DP = 8
    const val BORDER_DP = 1
    const val SONG_ROW_DP = 68
    const val NEWS_ROW_DP = 88
    const val ARTICLE_COMMENT_GAP_DP = 20
    const val FIELD_LIST_GAP_DP = 12
}

@Composable
fun InnerCard(
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(6.dp)
    Column(
        modifier
            .fillMaxWidth()
            .border(InnerSection.BORDER_DP.dp, t.border, shape)
            .padding(InnerSection.PAD_DP.dp),
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}

@Composable
fun innerChrome(border: Color? = null): Modifier {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(6.dp)
    return Modifier
        .border(InnerSection.BORDER_DP.dp, border ?: t.border, shape)
        .padding(horizontal = InnerSection.ROW_PAD_DP.dp, vertical = 6.dp)
}

@Composable
fun innerRowModifier(border: Color? = null): Modifier =
    Modifier.fillMaxWidth().then(innerChrome(border))

/**
 * Hug panes wrap height and sit at the top of the tab. `wrapContentHeight()`
 * defaults to [Alignment.CenterVertically] and must not sit inside `fillMaxSize`.
 */
fun paneSheetModifier(hug: Boolean): Modifier =
    Modifier
        .fillMaxWidth()
        .then(
            if (hug) {
                Modifier.wrapContentHeight(align = Alignment.Top)
            } else {
                Modifier.fillMaxSize()
            },
        )

@Composable
fun Pane(
    modifier: Modifier = Modifier,
    hug: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalTokens.current
    val gutter = if (t.wallpaper != null) 16.dp else 0.dp
    val shape = RoundedCornerShape(6.dp)
    // Hug wraps short chrome at the top. Cap to the tab so nested
    // verticalScroll is bounded; otherwise staff/schedule clip.
    BoxWithConstraints(
        modifier.fillMaxWidth().fillMaxHeight(),
        contentAlignment = Alignment.TopStart,
    ) {
        val cap = maxHeight
        Box(
            paneSheetModifier(hug)
                .then(if (hug) Modifier.heightIn(max = cap) else Modifier)
                .padding(gutter)
                .clip(shape)
                .then(
                    if (t.glass && !canBlur()) Modifier
                    else Modifier.border(1.dp, t.border, shape),
                ),
        ) {
            if (t.glass && t.wallpaper != null) {
                FrostBackdrop(t.wallpaper)
            } else {
                Box(Modifier.matchParentSize().background(t.surface))
            }
            Column(
                Modifier
                    .then(if (hug) Modifier.heightIn(max = cap) else Modifier.fillMaxSize())
                    .padding(16.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun BoxScope.FrostBackdrop(res: Int) {
    val laidOut = LocalWallpaperLayout.current
    var paneOrigin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    Box(
        Modifier
            .matchParentSize()
            .onGloballyPositioned { paneOrigin = it.positionInRoot() },
    ) {
        if (Frost.canCopy(laidOut.size)) {
            val w = with(density) { laidOut.size.width.toDp() }
            val h = with(density) { laidOut.size.height.toDp() }
            Image(
                painterResource(res),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .wrapContentSize(unbounded = true, align = Alignment.TopStart)
                    .requiredSize(w, h)
                    .offset { Frost.offsetPx(laidOut.position, paneOrigin) }
                    .then(if (canBlur()) Modifier.blur(15.dp) else Modifier),
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.5f)),
        )
    }
}
