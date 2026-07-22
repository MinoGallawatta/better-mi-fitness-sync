package com.bettermifitness.sync.strava

import kotlin.time.Clock

/**
 * Holds the current Strava session in memory; proactively refreshes near expiry.
 * Mirrors [com.bettermifitness.sync.data.MiSessionManager]'s activate/ensureActive shape.
 */
class StravaSessionManager(
    private val credentialsStore: StravaCredentialsStore,
    private val stravaAuth: StravaAuth,
) {
    private var current: StravaCredentials? = null

    val isConnected: Boolean get() = current != null

    fun activate(credentials: StravaCredentials) {
        current = credentials
    }

    /** Loads persisted credentials into memory if not already active. */
    suspend fun ensureActive(): Boolean {
        if (current != null) return true
        val creds = credentialsStore.loadCredentials() ?: return false
        current = creds
        return true
    }

    /**
     * A valid access token, refreshing first if within [REFRESH_MARGIN_SEC] of expiry.
     * @return null if not connected, or if a forced refresh fails.
     */
    suspend fun accessToken(): String? {
        if (!ensureActive()) return null
        val creds = current ?: return null
        val nowSec = Clock.System.now().epochSeconds
        if (creds.expiresAtEpochSec - nowSec > REFRESH_MARGIN_SEC) return creds.accessToken
        return refresh()
    }

    /** Forces a refresh regardless of expiry — used after the API itself rejects a token. */
    suspend fun forceRefresh(): String? {
        if (!ensureActive()) return null
        return refresh()
    }

    private suspend fun refresh(): String? {
        val creds = current ?: return null
        return try {
            val refreshed = stravaAuth.refresh(creds.refreshToken, creds.athleteId)
            credentialsStore.saveCredentials(refreshed)
            current = refreshed
            refreshed.accessToken
        } catch (e: StravaApiException.AuthExpired) {
            // Refresh token itself was rejected — disconnect so we stop retrying every hour.
            credentialsStore.clearCredentials()
            current = null
            null
        } catch (_: Exception) {
            // Transient (network/5xx/429) — leave credentials in place, try again next sync.
            null
        }
    }

    /** User-initiated disconnect: best-effort revoke, then clears local + in-memory state. */
    suspend fun disconnect() {
        val creds = current ?: credentialsStore.loadCredentials()
        if (creds != null) {
            try {
                stravaAuth.deauthorize(creds.accessToken)
            } catch (_: Exception) {
                // Best effort — local state is cleared regardless.
            }
        }
        credentialsStore.clearCredentials()
        current = null
    }

    companion object {
        private const val REFRESH_MARGIN_SEC = 5 * 60L
    }
}
