package io.r_a_d.geiravor.ui

object RequestPolicy {
    fun requestsAllowed(
        isAfkStream: Boolean,
        requesting: Boolean,
        canRequest: Boolean,
    ): Boolean = isAfkStream && requesting && canRequest

    fun rowCanRequest(allowed: Boolean, requestable: Boolean): Boolean =
        allowed && requestable
}
