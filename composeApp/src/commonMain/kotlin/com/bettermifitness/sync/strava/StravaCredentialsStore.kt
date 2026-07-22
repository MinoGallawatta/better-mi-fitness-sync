package com.bettermifitness.sync.strava

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persisted Strava OAuth tokens (SRP: auth storage only — mirrors CredentialsStore for Mi).
 */
class StravaCredentialsStore(
    private val dataStore: DataStore<Preferences>,
) {
    val isConnected: Flow<Boolean> = dataStore.data.map { it[ACCESS_TOKEN_KEY] != null }

    val athleteId: Flow<Long?> = dataStore.data.map { it[ATHLETE_ID_KEY] }

    suspend fun saveCredentials(credentials: StravaCredentials) {
        dataStore.edit { prefs ->
            prefs[ACCESS_TOKEN_KEY] = credentials.accessToken
            prefs[REFRESH_TOKEN_KEY] = credentials.refreshToken
            prefs[EXPIRES_AT_KEY] = credentials.expiresAtEpochSec
            prefs[ATHLETE_ID_KEY] = credentials.athleteId
        }
    }

    suspend fun loadCredentials(): StravaCredentials? {
        val prefs = dataStore.data.first()
        val accessToken = prefs[ACCESS_TOKEN_KEY] ?: return null
        val refreshToken = prefs[REFRESH_TOKEN_KEY] ?: return null
        val expiresAt = prefs[EXPIRES_AT_KEY] ?: return null
        val athleteId = prefs[ATHLETE_ID_KEY] ?: return null
        return StravaCredentials(accessToken, refreshToken, expiresAt, athleteId)
    }

    suspend fun clearCredentials() {
        dataStore.edit { prefs ->
            prefs.remove(ACCESS_TOKEN_KEY)
            prefs.remove(REFRESH_TOKEN_KEY)
            prefs.remove(EXPIRES_AT_KEY)
            prefs.remove(ATHLETE_ID_KEY)
        }
    }

    companion object {
        private val ACCESS_TOKEN_KEY = stringPreferencesKey("strava_access_token")
        private val REFRESH_TOKEN_KEY = stringPreferencesKey("strava_refresh_token")
        private val EXPIRES_AT_KEY = longPreferencesKey("strava_expires_at")
        private val ATHLETE_ID_KEY = longPreferencesKey("strava_athlete_id")
    }
}
