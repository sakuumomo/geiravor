package io.r_a_d.geiravor.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestPolicyTest {
    @Test
    fun requestDisabledWhenLiveDj() {
        assertFalse(
            RequestPolicy.requestsAllowed(
                isAfkStream = false,
                requesting = true,
                canRequest = true,
            ),
        )
    }

    @Test
    fun requestDisabledWhenSnapshotRequestingFalse() {
        assertFalse(
            RequestPolicy.requestsAllowed(
                isAfkStream = true,
                requesting = false,
                canRequest = true,
            ),
        )
    }

    @Test
    fun requestDisabledWhenCanRequestFalse() {
        assertFalse(
            RequestPolicy.requestsAllowed(
                isAfkStream = true,
                requesting = true,
                canRequest = false,
            ),
        )
    }

    @Test
    fun requestEnabledWhenAfkAndBothFlags() {
        assertTrue(
            RequestPolicy.requestsAllowed(
                isAfkStream = true,
                requesting = true,
                canRequest = true,
            ),
        )
    }

    @Test
    fun songCooldownDisablesRowEvenWhenAllowed() {
        assertFalse(RequestPolicy.rowCanRequest(allowed = true, requestable = false))
        assertTrue(RequestPolicy.rowCanRequest(allowed = true, requestable = true))
    }

    @Test
    fun offBannerDoesNotFlashWhileCanRequestIsUnknown() {
        assertFalse(
            RequestPolicy.showRequestsOff(
                isAfkStream = true,
                requesting = true,
                canRequest = null,
            ),
        )
        assertFalse(
            RequestPolicy.requestsAllowed(
                isAfkStream = true,
                requesting = true,
                canRequest = null,
            ),
        )
    }

    @Test
    fun offBannerOnceCanRequestIsFalse() {
        assertTrue(
            RequestPolicy.showRequestsOff(
                isAfkStream = true,
                requesting = true,
                canRequest = false,
            ),
        )
    }

    @Test
    fun offBannerImmediateForLiveDj() {
        assertTrue(
            RequestPolicy.showRequestsOff(
                isAfkStream = false,
                requesting = true,
                canRequest = null,
            ),
        )
    }
}
