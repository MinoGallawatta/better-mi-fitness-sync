package com.bettermifitness.sync.strava

/**
 * Transport / API failures from Strava's cloud. Mirrors [com.mifitness.miclient.api.MiApiException]'s
 * retry taxonomy so callers can reuse the same decision-making shape.
 */
sealed class StravaApiException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** Access token invalid/expired — refresh with the stored refresh token. */
    class AuthExpired(
        message: String = "Strava session expired",
        cause: Throwable? = null,
    ) : StravaApiException(message, cause)

    /** Transient network / connectivity. */
    class Network(
        message: String,
        cause: Throwable? = null,
    ) : StravaApiException(message, cause)

    /** HTTP 429 — Strava's 15-minute / daily request caps. */
    class RateLimited(
        val retryAfterSec: Int? = null,
        message: String = "Strava rate limited the request",
    ) : StravaApiException(message)

    /** Non-success HTTP that is not auth or rate-limit (4xx business errors, 5xx). */
    class Server(
        val httpCode: Int,
        message: String,
    ) : StravaApiException(message)

    class Unexpected(
        message: String,
        cause: Throwable? = null,
    ) : StravaApiException(message, cause)

    /** Whether a background sync should retry without user action. */
    val isRetryable: Boolean
        get() = when (this) {
            is AuthExpired -> false
            is Network -> true
            is RateLimited -> true
            is Server -> httpCode >= 500 || httpCode == 0
            is Unexpected -> true
        }
}
