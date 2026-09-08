package io.r_a_d.geiravor.compat

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore

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
