package com.bettermifitness.sync.strava

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class InMemoryPreferencesDataStore(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data: Flow<Preferences> = state
    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

class StravaUploadLedgerTest {

    @Test
    fun isEligible_unknownKey_isEligible() = runTest {
        val ledger = StravaUploadLedger(InMemoryPreferencesDataStore())
        assertTrue(ledger.isEligible("mifit-workout-1"))
    }

    @Test
    fun isEligible_uploaded_isNeverEligibleAgain() = runTest {
        val ledger = StravaUploadLedger(InMemoryPreferencesDataStore())
        ledger.record(
            "mifit-workout-1",
            StravaLedgerEntry(state = StravaLedgerState.UPLOADED, stravaActivityId = 42, lastAttemptEpochSec = 1000),
        )
        assertFalse(ledger.isEligible("mifit-workout-1", nowEpochSec = 100_000))
    }

    @Test
    fun isEligible_failedPermanent_isNeverEligibleAgain() = runTest {
        val ledger = StravaUploadLedger(InMemoryPreferencesDataStore())
        ledger.record(
            "mifit-workout-1",
            StravaLedgerEntry(state = StravaLedgerState.FAILED_PERMANENT, lastAttemptEpochSec = 1000),
        )
        assertFalse(ledger.isEligible("mifit-workout-1", nowEpochSec = 100_000))
    }

    @Test
    fun isEligible_pendingUpload_isAlwaysEligible() = runTest {
        val ledger = StravaUploadLedger(InMemoryPreferencesDataStore())
        ledger.record(
            "mifit-workout-1",
            StravaLedgerEntry(
                state = StravaLedgerState.PENDING_UPLOAD,
                stravaUploadId = 7,
                lastAttemptEpochSec = 1000,
            ),
        )
        assertTrue(ledger.isEligible("mifit-workout-1", nowEpochSec = 1001))
    }

    @Test
    fun isEligible_failedRetryable_respectsBackoffWindow() = runTest {
        val ledger = StravaUploadLedger(InMemoryPreferencesDataStore())
        ledger.record(
            "mifit-workout-1",
            StravaLedgerEntry(state = StravaLedgerState.FAILED_RETRYABLE, lastAttemptEpochSec = 1000, attemptCount = 1),
        )
        // attemptCount=1 -> backoff = 2h = 7200s
        assertFalse(ledger.isEligible("mifit-workout-1", nowEpochSec = 1000 + 3600))
        assertTrue(ledger.isEligible("mifit-workout-1", nowEpochSec = 1000 + 7200))
    }

    @Test
    fun record_persistsAcrossMultipleKeys() = runTest {
        val ledger = StravaUploadLedger(InMemoryPreferencesDataStore())
        ledger.record("a", StravaLedgerEntry(state = StravaLedgerState.UPLOADED, lastAttemptEpochSec = 1))
        ledger.record("b", StravaLedgerEntry(state = StravaLedgerState.FAILED_PERMANENT, lastAttemptEpochSec = 2))
        assertFalse(ledger.isEligible("a"))
        assertFalse(ledger.isEligible("b"))
        assertTrue(ledger.isEligible("c"))
    }
}
