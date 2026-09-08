package io.r_a_d.geiravor.compat

import android.os.Build

/** Compat: Compose Modifier.blur (RenderEffect). Remove when minSdk >= 31. */
fun canBlur(): Boolean = Build.VERSION.SDK_INT >= 31
