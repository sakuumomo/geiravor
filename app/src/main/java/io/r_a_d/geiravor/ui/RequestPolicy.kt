package io.r_a_d.geiravor.ui

import kotlin.coroutines.cancellation.CancellationException

object RequestPolicy {
    fun requestsAllowed(
        isAfkStream: Boolean,
        requesting: Boolean,
        canRequest: Boolean?,
    ): Boolean = isAfkStream && requesting && canRequest == true

    /** Unknown can-request (null) must not show the off banner. */
    fun showRequestsOff(
        isAfkStream: Boolean?,
        requesting: Boolean?,
        canRequest: Boolean?,
    ): Boolean {
        if (isAfkStream == null || requesting == null) {
            return false
        }
        if (!isAfkStream || !requesting) {
            return true
        }
        return canRequest == false
    }

    fun rowCanRequest(allowed: Boolean, requestable: Boolean): Boolean =
        allowed && requestable

    /** A successful POST spends the IP cooldown. Do not wait on GET /api/can-request. */
    fun canRequestAfterRequest(success: Boolean, previous: Boolean?): Boolean? =
        if (success) false else previous

    /** Cancellation from debounce / leaving the pane is not a user-facing error. */
    fun userFacingError(err: Throwable): String? {
        if (err is CancellationException) {
            return null
        }
        return err.message?.takeIf { it.isNotBlank() } ?: "Request failed"
    }
}
