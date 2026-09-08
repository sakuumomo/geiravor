package io.r_a_d.geiravor.compat

import android.os.Build
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder

/**
 * Compat: Coil GIF. ImageDecoderDecoder needs API 28. Remove when minSdk >= 28.
 */
fun ImageLoader.Builder.addGifDecoder(): ImageLoader.Builder = components {
    if (Build.VERSION.SDK_INT >= 28) {
        add(ImageDecoderDecoder.Factory())
    } else {
        add(GifDecoder.Factory())
    }
}
