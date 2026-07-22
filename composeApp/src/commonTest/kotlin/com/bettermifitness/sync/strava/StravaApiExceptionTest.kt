package com.bettermifitness.sync.strava

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StravaApiExceptionTest {

    @Test
    fun authExpired_isNotRetryable() {
        assertFalse(StravaApiException.AuthExpired().isRetryable)
    }

    @Test
    fun network_isRetryable() {
        assertTrue(StravaApiException.Network("boom").isRetryable)
    }

    @Test
    fun rateLimited_isRetryable_andCarriesRetryAfter() {
        val e = StravaApiException.RateLimited(retryAfterSec = 120)
        assertTrue(e.isRetryable)
        assertEquals(120, e.retryAfterSec)
    }

    @Test
    fun server_5xxIsRetryable_4xxIsNot() {
        assertTrue(StravaApiException.Server(500, "server error").isRetryable)
        assertTrue(StravaApiException.Server(0, "unknown").isRetryable)
        assertFalse(StravaApiException.Server(400, "bad request").isRetryable)
    }

    @Test
    fun unexpected_isRetryable() {
        assertTrue(StravaApiException.Unexpected("weird").isRetryable)
    }
}
