package com.bettermifitness.sync.strava

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class StravaLedgerState {
    UPLOADED,
    FAILED_PERMANENT,
    FAILED_RETRYABLE,
    PENDING_UPLOAD,
}

@Serializable
data class StravaLedgerEntry(
    val state: StravaLedgerState,
    val stravaUploadId: Long? = null,
    val stravaActivityId: Long? = null,
    val lastAttemptEpochSec: Long,
    val attemptCount: Int = 0,
    val lastErrorMessage: String? = null,
)

/**
 * Dedupe ledger for Strava uploads, keyed by [com.bettermifitness.sync.health.HealthRecordIds.workout]
 * — the same id already used for Health Connect upserts. Without this, `MiSyncWorker`'s hourly
 * rolling window would re-upload the same workout to Strava on every run.
 */
class StravaUploadLedger(
    private val dataStore: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun entryFor(key: String): StravaLedgerEntry? = loadAll()[key]

    /** True if [key] should be attempted now (not uploaded, not permanently failed, past any backoff). */
    suspend fun isEligible(key: String, nowEpochSec: Long = Clock.System.now().epochSeconds): Boolean {
        val entry = entryFor(key) ?: return true
        return when (entry.state) {
            StravaLedgerState.UPLOADED -> false
            StravaLedgerState.FAILED_PERMANENT -> false
            StravaLedgerState.PENDING_UPLOAD -> true // resume by polling, not re-uploading
            StravaLedgerState.FAILED_RETRYABLE ->
                nowEpochSec >= entry.lastAttemptEpochSec + backoffSec(entry.attemptCount)
        }
    }

    suspend fun record(key: String, entry: StravaLedgerEntry) {
        dataStore.edit { prefs ->
            val current = decode(prefs[LEDGER_KEY])
            val updated = current + (key to entry)
            prefs[LEDGER_KEY] = json.encodeToString(LedgerData.serializer(), LedgerData(updated))
        }
    }

    private suspend fun loadAll(): Map<String, StravaLedgerEntry> = decode(dataStore.data.first()[LEDGER_KEY])

    private fun decode(raw: String?): Map<String, StravaLedgerEntry> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            json.decodeFromString(LedgerData.serializer(), raw).entries
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /** 1h, 2h, 4h, ... capped at 24h — naturally rides the hourly WorkManager cadence. */
    private fun backoffSec(attemptCount: Int): Long {
        val hours = (1 shl attemptCount.coerceIn(0, 4)).coerceAtMost(24)
        return hours * 3600L
    }

    @Serializable
    private data class LedgerData(val entries: Map<String, StravaLedgerEntry> = emptyMap())

    companion object {
        private val LEDGER_KEY = stringPreferencesKey("strava_upload_ledger")
    }
}
