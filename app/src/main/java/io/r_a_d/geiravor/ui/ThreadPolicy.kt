package io.r_a_d.geiravor.ui

object ThreadPolicy {
    enum class Kind { Hidden, Link, Image }

    fun kind(isAfkStream: Boolean, thread: String?): Kind {
        if (isAfkStream) {
            return Kind.Hidden
        }
        val value = thread?.trim().orEmpty()
        if (value.isEmpty() || value.equals("none", ignoreCase = true)) {
            return Kind.Hidden
        }
        if (imageUrl(value) != null) {
            return Kind.Image
        }
        return if (linkUrl(value) != null) Kind.Link else Kind.Hidden
    }

    fun imageUrl(thread: String): String? {
        val value = thread.trim()
        val prefixed = value.startsWith("image:", ignoreCase = true)
        val raw = if (prefixed) value.substring(6).trim() else value
        if (!isHttpUrl(raw)) {
            return null
        }
        if (prefixed) {
            return raw
        }
        val path = raw.substringBefore('?').substringBefore('#').lowercase()
        val image = path.endsWith(".png") ||
            path.endsWith(".jpg") ||
            path.endsWith(".jpeg") ||
            path.endsWith(".gif") ||
            path.endsWith(".webp") ||
            path.endsWith(".mp4") ||
            path.endsWith(".webm")
        return if (image) raw else null
    }

    fun linkUrl(thread: String): String? {
        if (imageUrl(thread) != null) {
            return null
        }
        val value = thread.trim()
        return if (isHttpUrl(value)) value else null
    }

    private fun isHttpUrl(value: String): Boolean =
        value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true)
}
