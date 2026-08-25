package io.r_a_d.geiravor.playback

import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.icy.IcyInfo

object IcyMetadata {
    fun titleFrom(metadata: Metadata): String? {
        for (i in 0 until metadata.length()) {
            val entry = metadata[i]
            if (entry is IcyInfo) {
                val title = entry.title?.trim().orEmpty()
                if (title.isNotEmpty()) {
                    return title
                }
            }
        }
        return null
    }
}
