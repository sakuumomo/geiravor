package io.r_a_d.geiravor.compat

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp

/** Compat: Compose Modifier.blur (RenderEffect). Remove when minSdk >= 31. */
fun canBlur(): Boolean = Build.VERSION.SDK_INT >= 31

/** Compat: Compose Modifier.blur (RenderEffect). Remove when minSdk >= 31. */
fun Modifier.compatBlur(radius: Dp): Modifier =
    if (canBlur()) blur(radius) else this
