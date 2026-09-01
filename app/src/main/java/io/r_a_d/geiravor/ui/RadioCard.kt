package io.r_a_d.geiravor.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.r_a_d.geiravor.compat.compatBlur

private val cardShape = RoundedCornerShape(6.dp)
private val glassBlur = 15.dp

private val LocalFrostNested = compositionLocalOf { false }

val LocalWallpaperLayout = compositionLocalOf { WallpaperLayout() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeiravorTheme(content: @Composable () -> Unit) {
    val colors = RadioTheme.current
    val hover = RadioPacks.hoverAlpha(colors)
    val press = RadioPacks.pressAlpha(colors)
    val ripple = RippleConfiguration(
        color = RadioPacks.highlight(colors),
        rippleAlpha = RippleAlpha(
            draggedAlpha = hover,
            focusedAlpha = hover,
            hoveredAlpha = hover,
            pressedAlpha = press,
        ),
    )
    MaterialTheme(colorScheme = radioColorScheme()) {
        CompositionLocalProvider(LocalRippleConfiguration provides ripple, content = content)
    }
}

@Composable
fun radioButtonColors() = ButtonDefaults.buttonColors(
    containerColor = RadioTheme.blue,
    contentColor = RadioTheme.onBlue,
    disabledContainerColor = RadioTheme.border,
    disabledContentColor = RadioTheme.muted,
)

@Composable
fun RadioPane(
    modifier: Modifier = Modifier,
    fill: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val gutter = RadioTheme.paneGutterDp.dp
    val pad = RadioTheme.panePadDp.dp
    val inner = if (fill) Modifier.fillMaxSize().padding(pad) else Modifier.padding(pad)
    if (RadioTheme.wallpaper == null) {
        Column(modifier.padding(pad), content = content)
        return
    }
    Box(modifier.padding(gutter).clip(cardShape)) {
        if (RadioTheme.glass) {
            FrostBackdrop(Modifier.matchParentSize())
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .background(RadioTheme.surface.copy(alpha = 0.94f)),
            )
        }
        CompositionLocalProvider(LocalFrostNested provides true) {
            Column(inner, content = content)
        }
    }
}

@Composable
fun RadioBadge(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    if (RadioTheme.wallpaper == null) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        return
    }
    Box(modifier.clip(cardShape)) {
        if (RadioTheme.glass) {
            FrostBackdrop(Modifier.matchParentSize())
        } else {
            Box(
                Modifier
                    .matchParentSize()
                    .background(RadioTheme.surface.copy(alpha = 0.94f)),
            )
        }
        CompositionLocalProvider(LocalFrostNested provides true) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
}

@Composable
fun RadioCard(
    modifier: Modifier = Modifier,
    border: BorderStroke = BorderStroke(1.dp, RadioTheme.border),
    content: @Composable () -> Unit,
) {
    val nested = LocalFrostNested.current
    Box(modifier = modifier.clip(cardShape)) {
        if (RadioPacks.frost(nested)) {
            FrostBackdrop(Modifier.matchParentSize())
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = cardShape,
            colors = CardDefaults.cardColors(
                containerColor = RadioPacks.cardFill(RadioTheme.current, nested),
            ),
            border = border,
            content = { content() },
        )
    }
}

@Composable
fun FrostBackdrop(modifier: Modifier = Modifier) {
    val wallpaper = RadioTheme.wallpaper
    if (!RadioPacks.frost(LocalFrostNested.current) || !RadioTheme.glass || wallpaper == null) {
        return
    }
    val laidOut = LocalWallpaperLayout.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val copy = FrostPolicy.copySize(laidOut.size)
    Box(
        modifier
            .clip(cardShape)
            .onGloballyPositioned { origin = it.positionInRoot() },
    ) {
        if (FrostPolicy.canCopy(copy)) {
            val copyW = with(density) { copy.width.toDp() }
            val copyH = with(density) { copy.height.toDp() }
            Image(
                painter = painterResource(wallpaper),
                contentDescription = null,
                modifier = Modifier
                    .wrapContentSize(unbounded = true, align = Alignment.TopStart)
                    .requiredSize(copyW, copyH)
                    .offset { FrostPolicy.offsetPx(laidOut.position, origin) }
                    .compatBlur(glassBlur),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier
                .matchParentSize()
                .background(RadioTheme.surface),
        )
    }
}

@Composable
private fun radioColorScheme(): ColorScheme {
    val colors = RadioTheme.current
    val lightPage = colors.onBackground.red + colors.onBackground.green + colors.onBackground.blue < 1.5f
    val scheme = if (lightPage) {
        lightColorScheme(
            primary = colors.blue,
            onPrimary = RadioTheme.onBlue,
            background = colors.background,
            onBackground = colors.onBackground,
            surface = colors.surface,
            onSurface = colors.text,
            error = colors.red,
        )
    } else {
        darkColorScheme(
            primary = colors.blue,
            onPrimary = RadioTheme.onBlue,
            background = colors.background,
            onBackground = colors.onBackground,
            surface = colors.surface,
            onSurface = colors.text,
            error = colors.red,
        )
    }
    return scheme
}
