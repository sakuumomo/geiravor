package io.r_a_d.geiravor.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.r_a_d.geiravor.R
import kotlin.math.abs
import uniffi.geiravor_core.ThemePack

data class Tokens(
    val background: Color,
    val surface: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val onBackground: Color,
    val accent: Color,
    val highlight: Color,
    val link: Color,
    val red: Color,
    val green: Color,
    val blue: Color,
    val glass: Boolean,
    val wallpaper: Int?,
)

val LocalTokens = staticCompositionLocalOf { tokens(ThemePack.DEFAULT_DARK) }

fun hsl(h: Float, s: Float, l: Float): Color {
    val sat = s / 100f
    val lig = l / 100f
    val c = (1f - abs(2f * lig - 1f)) * sat
    val hp = h / 60f
    val x = c * (1f - abs(hp % 2f - 1f))
    val m = lig - c / 2f
    val (r, g, b) = when {
        hp < 1f -> Triple(c, x, 0f)
        hp < 2f -> Triple(x, c, 0f)
        hp < 3f -> Triple(0f, c, x)
        hp < 4f -> Triple(0f, x, c)
        hp < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}

fun tokens(pack: ThemePack): Tokens {
    val blue = hsl(208f, 27f, 39f)
    val red = hsl(348f, 27f, 50f)
    val green = hsl(153f, 27f, 39f)
    val link = hsl(208f, 27f, 50f)
    return when (pack) {
        ThemePack.DEFAULT_DARK -> Tokens(
            background = hsl(0f, 0f, 11f),
            surface = hsl(0f, 0f, 13f),
            border = hsl(0f, 0f, 24f),
            text = hsl(0f, 0f, 96f),
            muted = hsl(0f, 0f, 50f),
            onBackground = hsl(0f, 0f, 96f),
            accent = blue,
            highlight = blue,
            link = link,
            red = red,
            green = green,
            blue = blue,
            glass = false,
            wallpaper = null,
        )
        ThemePack.DEFAULT_LIGHT -> {
            val info = hsl(198f, 100f, 41f)
            Tokens(
                background = hsl(221f, 14f, 96f),
                surface = Color.White,
                border = hsl(221f, 14f, 86f),
                text = hsl(221f, 14f, 21f),
                muted = hsl(221f, 14f, 48f),
                onBackground = hsl(221f, 14f, 21f),
                accent = info,
                highlight = info,
                link = hsl(233f, 51f, 51f),
                red = red,
                green = green,
                blue = hsl(198f, 100f, 41f),
                glass = false,
                wallpaper = null,
            )
        }
        ThemePack.CHRISTMAS -> {
            val accent = Color(0xFF38A8E4)
            Tokens(
                background = Color.Transparent,
                surface = Color.White,
                border = Color(0xFFE0E0E0),
                text = Color(0xFF1A1A1A),
                muted = Color(0xFF666666),
                onBackground = Color.White,
                accent = accent,
                highlight = accent,
                link = accent,
                red = red,
                green = green,
                blue = blue,
                glass = false,
                wallpaper = R.drawable.wallpaper_christmas,
            )
        }
        ThemePack.HALLOWEEN -> {
            val accent = Color(0xFFF4A246)
            Tokens(
                background = Color.Transparent,
                surface = Color.Black.copy(alpha = 0.5f),
                border = Color.White.copy(alpha = 0.12f),
                text = Color.White,
                muted = Color.White.copy(alpha = 0.7f),
                onBackground = Color.White,
                accent = accent,
                highlight = lerp(accent, Color.White, 0.35f),
                link = accent,
                red = red,
                green = green,
                blue = blue,
                glass = true,
                wallpaper = R.drawable.wallpaper_halloween,
            )
        }
        ThemePack.NEW_YEARS -> {
            val accent = Color(0xFF60709F)
            Tokens(
                background = Color.Transparent,
                surface = Color.Black.copy(alpha = 0.5f),
                border = Color.White.copy(alpha = 0.12f),
                text = Color.White,
                muted = Color.White.copy(alpha = 0.7f),
                onBackground = Color.White,
                accent = accent,
                highlight = lerp(accent, Color.White, 0.4f),
                link = accent,
                red = red,
                green = green,
                blue = blue,
                glass = true,
                wallpaper = R.drawable.wallpaper_newyears,
            )
        }
    }
}
