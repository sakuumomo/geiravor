package io.r_a_d.geiravor.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.r_a_d.geiravor.R

data class RadioColors(
    val background: Color,
    val surface: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val onBackground: Color,
    val onBackgroundMuted: Color,
    val blue: Color,
    val red: Color,
    val green: Color,
    val link: Color,
    val wallpaper: Int? = null,
    val glass: Boolean = false,
)

object RadioPacks {
    const val DEFAULT_DARK = "default-dark"
    const val DEFAULT_LIGHT = "default-light"
    const val CHRISTMAS = "christmas"
    const val HALLOWEEN = "halloween"
    const val NEWYEARS = "newyears"

    val HOLIDAYS = setOf(CHRISTMAS, HALLOWEEN, NEWYEARS)

    val PICKS = listOf(
        DEFAULT_DARK to "Default",
        DEFAULT_LIGHT to "Default light",
        CHRISTMAS to "Christmas",
        HALLOWEEN to "Halloween",
        NEWYEARS to "New Years",
    )

    /** `/assets/default-dark/css/bulma.min.css` */
    val defaultDark = RadioColors(
        background = Color.hsl(0f, 0f, 0.11f),
        surface = Color.hsl(0f, 0f, 0.13f),
        border = Color.hsl(0f, 0f, 0.24f),
        text = Color.hsl(0f, 0f, 0.96f),
        muted = Color.hsl(0f, 0f, 0.50f),
        onBackground = Color.hsl(0f, 0f, 0.96f),
        onBackgroundMuted = Color.hsl(0f, 0f, 0.50f),
        blue = Color.hsl(208f, 0.27f, 0.39f),
        red = Color.hsl(348f, 0.27f, 0.50f),
        green = Color.hsl(153f, 0.27f, 0.39f),
        link = Color.hsl(208f, 0.27f, 0.50f),
    )

    /**
     * Live `/assets/default-light/css/bulma.min.css` (stock Bulma light; no `--radio-*`).
     * Scheme 221/14%, text 21%/48%, info 198/100/70, link 233/100/63.
     */
    val defaultLight = RadioColors(
        background = Color.hsl(221f, 0.14f, 0.96f),
        surface = Color.hsl(221f, 0.14f, 1f),
        border = Color.hsl(221f, 0.14f, 0.86f),
        text = Color.hsl(221f, 0.14f, 0.21f),
        muted = Color.hsl(221f, 0.14f, 0.48f),
        onBackground = Color.hsl(221f, 0.14f, 0.21f),
        onBackgroundMuted = Color.hsl(221f, 0.14f, 0.48f),
        blue = Color.hsl(198f, 1f, 0.41f),
        red = Color.hsl(348f, 1f, 0.44f),
        green = Color.hsl(153f, 0.53f, 0.40f),
        link = Color.hsl(233f, 1f, 0.50f),
    )

    private val holidayLink = Color.hsl(208f, 0.47f, 0.59f)
    private val holidayGreen = Color.hsl(153f, 0.47f, 0.59f)
    private val holidayRed = Color.hsl(348f, 0.47f, 0.70f)
    private val holidayBg = Color(0xFF2A7CD3)

    /** White cards on wallpaper. Accent `--edenlight-color: #38A8E4`. */
    val christmas = RadioColors(
        background = holidayBg,
        surface = Color.White,
        border = Color(0x33000000),
        text = Color.hsl(222f, 0.14f, 0.29f),
        muted = Color.hsl(222f, 0.14f, 0.20f),
        onBackground = Color.White,
        onBackgroundMuted = Color.hsl(0f, 0f, 0.75f),
        blue = Color(0xFF38A8E4),
        red = holidayRed,
        green = holidayGreen,
        link = holidayLink,
        wallpaper = R.drawable.wallpaper_christmas,
    )

    /** Dark glass cards on wallpaper. Accent `#F4A246`. */
    val halloween = RadioColors(
        background = holidayBg,
        surface = Color(0x80000000),
        border = Color(0x80FFFFFF),
        text = Color.White,
        muted = Color.hsl(0f, 0f, 0.75f),
        onBackground = Color.White,
        onBackgroundMuted = Color.hsl(0f, 0f, 0.75f),
        blue = Color(0xFFF4A246),
        red = holidayRed,
        green = holidayGreen,
        link = holidayLink,
        wallpaper = R.drawable.wallpaper_halloween,
        glass = true,
    )

    /** Dark glass cards on wallpaper. Accent `#60709f`. */
    val newyears = RadioColors(
        background = holidayBg,
        surface = Color(0x80000000),
        border = Color(0x80FFFFFF),
        text = Color.White,
        muted = Color.hsl(0f, 0f, 0.75f),
        onBackground = Color.White,
        onBackgroundMuted = Color.hsl(0f, 0f, 0.75f),
        blue = Color(0xFF60709F),
        red = holidayRed,
        green = holidayGreen,
        link = holidayLink,
        wallpaper = R.drawable.wallpaper_newyears,
        glass = true,
    )

    fun colors(id: String): RadioColors =
        when (id) {
            DEFAULT_LIGHT -> defaultLight
            CHRISTMAS -> christmas
            HALLOWEEN -> halloween
            NEWYEARS -> newyears
            else -> defaultDark
        }

    /**
     * Selection chrome (theme radios, pager current, tab indicator).
     * Glass packs sit on wallpaper; official `--edenlight-color` can vanish, so lift that
     * same hue until it reads. Play/progress keep [RadioColors.blue].
     */
    fun highlight(colors: RadioColors): Color {
        if (!colors.glass) {
            return colors.blue
        }
        var lifted = colors.blue
        var t = 0f
        while (channelLuma(lifted) < GLASS_HIGHLIGHT_LUMA && t < 0.7f) {
            t += 0.08f
            lifted = lerp(colors.blue, Color.White, t)
        }
        return lifted
    }

    fun onHighlight(colors: RadioColors): Color =
        if (channelLuma(highlight(colors)) > 0.45f) Color(0xDE1A1A1A) else Color.White

    internal fun channelLuma(color: Color): Float =
        0.2126f * color.red + 0.7152f * color.green + 0.0722f * color.blue

    private const val GLASS_HIGHLIGHT_LUMA = 0.58f
}

object RadioTheme {
    var packId: String = RadioPacks.DEFAULT_DARK
        private set
    var current: RadioColors by mutableStateOf(RadioPacks.defaultDark)
        private set

    fun apply(id: String) {
        packId = id
        current = RadioPacks.colors(id)
    }

    val background get() = current.background
    val surface get() = current.surface
    val border get() = current.border
    val text get() = current.text
    val muted get() = current.muted
    val onBackground get() = current.onBackground
    val onBackgroundMuted get() = current.onBackgroundMuted
    val blue get() = current.blue
    val red get() = current.red
    val green get() = current.green
    val link get() = current.link
    val wallpaper get() = current.wallpaper
    val glass get() = current.glass
    val highlight get() = RadioPacks.highlight(current)
    val onHighlight get() = RadioPacks.onHighlight(current)
    val onBlue get(): Color {
        val l = RadioPacks.channelLuma(current.blue)
        return if (l > 0.45f) Color(0xDE1A1A1A) else Color.White
    }
}
