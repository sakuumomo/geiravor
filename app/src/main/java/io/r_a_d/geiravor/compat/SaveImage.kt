package io.r_a_d.geiravor.compat

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.runBlocking

/**
 * Compat: MediaStore RELATIVE_PATH. Remove when minSdk >= 29.
 */
fun threadImageValues(name: String): ContentValues {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
    }
    if (Build.VERSION.SDK_INT >= 29) {
        values.put(
            MediaStore.Images.Media.RELATIVE_PATH,
            Environment.DIRECTORY_PICTURES + "/r-a-dio",
        )
    }
    return values
}

fun canWritePictures(context: Context): Boolean {
    if (Build.VERSION.SDK_INT >= 29) return true
    return context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED
}

fun threadSaveName(url: String): String {
    val raw = url.substringBefore('?').substringAfterLast('/').ifBlank { "thread" }
    val base = raw.substringBeforeLast('.', raw).ifBlank { "thread" }
    return "$base.jpg"
}

/** Flatten Coil still (GIF first frame) into Pictures. Worker-thread only. */
fun saveThreadStill(context: Context, url: String): Boolean {
    if (!canWritePictures(context)) return false
    val result = runBlocking {
        context.imageLoader.execute(
            ImageRequest.Builder(context).data(url).allowHardware(false).build(),
        )
    }
    val bitmap = (result as? SuccessResult)?.drawable?.toBitmap() ?: return false
    val values = threadImageValues(threadSaveName(url))
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return false
    context.contentResolver.openOutputStream(uri)?.use { out ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
    } ?: return false
    return true
}
