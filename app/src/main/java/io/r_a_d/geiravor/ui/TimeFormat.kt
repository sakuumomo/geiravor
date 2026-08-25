package io.r_a_d.geiravor.ui

fun formatMmSs(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

fun formatProgressClock(elapsedSecs: Long, durationSecs: Long?): String? {
    if (durationSecs != null) {
        return "${formatMmSs(elapsedSecs)} / ${formatMmSs(durationSecs)}"
    }
    if (elapsedSecs > 0) {
        return formatMmSs(elapsedSecs)
    }
    return null
}
