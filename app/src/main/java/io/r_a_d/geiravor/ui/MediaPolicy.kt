package io.r_a_d.geiravor.ui

object MediaPolicy {
    enum class Kind { Image, Video }

    fun path(url: String): String =
        url.trim().substringBefore('?').substringBefore('#').lowercase()

    fun kind(url: String): Kind {
        val path = path(url)
        return if (
            path.endsWith(".mp4") ||
            path.endsWith(".webm") ||
            path.endsWith(".mov")
        ) {
            Kind.Video
        } else {
            Kind.Image
        }
    }

    fun isVideo(url: String): Boolean = kind(url) == Kind.Video
}
