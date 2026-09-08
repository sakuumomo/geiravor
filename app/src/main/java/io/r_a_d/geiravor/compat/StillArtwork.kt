package io.r_a_d.geiravor.compat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import io.r_a_d.geiravor.GeiravorApp
import io.r_a_d.geiravor.R
import kotlinx.coroutines.runBlocking

/**
 * Coil still for shade, Auto, and DJ notices.
 * GIF first frame / current drawable frame — not dropped because it is animated.
 * `docs/spec/api.md`, `docs/spec/playback.md`.
 */
fun stillFromDrawable(drawable: Drawable): Bitmap {
    (drawable as? BitmapDrawable)?.bitmap?.let { return it }
    val w = drawable.intrinsicWidth.coerceAtLeast(1)
    val h = drawable.intrinsicHeight.coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    drawable.setBounds(0, 0, w, h)
    drawable.draw(canvas)
    return bmp
}

/** Worker-thread only. Null on miss/error — caller may substitute mystery-DJ. */
fun loadCoilStill(context: Context, url: String?): Bitmap? {
    val src = url?.trim().orEmpty()
    if (src.isEmpty()) return null
    val app = context.applicationContext as? GeiravorApp
    val loader = app?.stillImages ?: context.imageLoader
    val result = runBlocking {
        loader.execute(
            ImageRequest.Builder(context)
                .data(src)
                .allowHardware(false)
                .build(),
        )
    }
    val drawable = (result as? SuccessResult)?.drawable ?: return null
    return stillFromDrawable(drawable)
}

fun mysteryDjBitmap(context: Context): Bitmap =
    BitmapFactory.decodeResource(context.resources, R.drawable.mystery_dj)
