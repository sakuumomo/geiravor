package io.r_a_d.geiravor.ui

object SongListPolicy {
    fun showQueue(isAfkStream: Boolean): Boolean = isAfkStream

    fun caption(isRequest: Boolean, whenText: String): String =
        if (isRequest) "/r/ · $whenText" else whenText
}
