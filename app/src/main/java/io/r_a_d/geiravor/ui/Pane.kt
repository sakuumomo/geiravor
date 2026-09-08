package io.r_a_d.geiravor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.r_a_d.geiravor.compat.canBlur
import io.r_a_d.geiravor.theme.LocalTokens
import kotlin.math.roundToInt

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
    val root = LocalWallpaperRoot.current
    var pane by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(
        modifier.fillMaxWidth().then(if (hug) Modifier else Modifier.fillMaxSize()),
        contentAlignment = Alignment.TopStart,
    ) {
        Box(
            paneSheetModifier(hug)
                .padding(gutter)
                .clip(shape)
                .onGloballyPositioned { pane = it }
                .then(
                    if (t.glass && !canBlur()) Modifier
                    else Modifier.border(1.dp, t.border, shape),
                ),
        ) {
            val res = t.wallpaper
            val coords = pane
            if (t.glass && res != null && root.coords != null && coords != null) {
                val origin = root.coords.localPositionOf(coords, Offset.Zero)
                val density = LocalDensity.current
                Image(
                    painterResource(res),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .requiredSize(
                            with(density) { root.size.width.toDp() },
                            with(density) { root.size.height.toDp() },
                        )
                        .offset { IntOffset(-origin.x.roundToInt(), -origin.y.roundToInt()) }
                        .then(if (canBlur()) Modifier.blur(15.dp) else Modifier),
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                )
            } else {
                Box(Modifier.matchParentSize().background(t.surface))
            }
            Column(
                Modifier
                    .then(if (hug) Modifier else Modifier.fillMaxSize())
                    .padding(16.dp),
                content = content,
            )
        }
    }
}
