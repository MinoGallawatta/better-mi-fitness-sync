package com.bettermifitness.sync.strava

import com.bettermifitness.sync.data.api.WorkoutSession
import com.bettermifitness.sync.health.HealthRecordIds
import kotlinx.coroutines.delay
import kotlin.time.Clock

/**
 * Pushes newly-synced workouts to Strava. Called from `HealthRepository.syncWorkouts` as an
 * isolated side effect — every public entry point here swallows its own failures, so a Strava
 * problem can never affect the Health Connect sync outcome.
 */
class StravaWorkoutSyncer(
    private val sessionManager: StravaSessionManager,
    private val uploadApi: StravaUploadApi,
    private val ledger: StravaUploadLedger,
) {
    private var cooldownUntilEpochSec: Long = 0

    suspend fun pushIfNeeded(sessions: List<WorkoutSession>) {
        if (sessions.isEmpty()) return
        if (!sessionManager.ensureActive()) return
        for (session in sessions) {
            if (isInGlobalCooldown()) break
            try {
                pushOne(session)
            } catch (_: Exception) {
                // Defensive: pushOne already handles StravaApiException per-workout below.
            }
        }
    }

    private suspend fun pushOne(session: WorkoutSession) {
        val key = HealthRecordIds.workout(session.startTime)
        if (!ledger.isEligible(key)) return

        val accessToken = sessionManager.accessToken() ?: return
        val existing = ledger.entryFor(key)

        try {
            val status = if (existing?.state == StravaLedgerState.PENDING_UPLOAD && existing.stravaUploadId != null) {
                pollUntilResolved(accessToken, existing.stravaUploadId, key, existing.attemptCount)
            } else {
                val tcx = TcxExporter.export(session)
                val uploaded = uploadApi.uploadTcx(accessToken, tcx, key, name = null)
                pollUntilResolved(accessToken, uploaded.uploadId, key, existing?.attemptCount ?: 0)
            }
            recordOutcome(key, status, existing?.attemptCount ?: 0)
        } catch (e: StravaApiException.AuthExpired) {
            handleAuthExpired(key, existing)
        } catch (e: StravaApiException.RateLimited) {
            cooldownUntilEpochSec = nowSec() + (e.retryAfterSec?.toLong() ?: DEFAULT_COOLDOWN_SEC)
            ledger.record(key, failedRetryable(existing, e.message))
        } catch (e: StravaApiException.Server) {
            ledger.record(key, failedRetryable(existing, e.message))
        } catch (e: StravaApiException.Network) {
            ledger.record(key, failedRetryable(existing, e.message))
        } catch (e: StravaApiException) {
            // Unexpected / malformed-request style errors: don't retry this workout again.
            ledger.record(key, failedPermanent(existing, e.message))
        }
    }

    private suspend fun handleAuthExpired(key: String, existing: StravaLedgerEntry?) {
        val refreshed = sessionManager.forceRefresh()
        if (refreshed != null) {
            ledger.record(key, failedRetryable(existing, "Access token refreshed, retrying next sync"))
        }
        // If refreshed == null, StravaSessionManager already disconnected locally on a
        // genuine refresh-token rejection; nothing more to do until the user reconnects.
    }

    private suspend fun pollUntilResolved(
        accessToken: String,
        uploadId: Long,
        key: String,
        attemptCount: Int,
    ): StravaUploadStatus {
        var status = uploadApi.pollUpload(accessToken, uploadId)
        var attempts = 0
        while (status.activityId == null && status.error.isNullOrBlank() && attempts < MAX_POLL_ATTEMPTS) {
            delay(POLL_DELAY_MS)
            status = uploadApi.pollUpload(accessToken, uploadId)
            attempts++
        }
        if (status.activityId == null && status.error.isNullOrBlank()) {
            // Still processing server-side after our bounded wait — resume next sync via PENDING_UPLOAD.
            ledger.record(
                key,
                StravaLedgerEntry(
                    state = StravaLedgerState.PENDING_UPLOAD,
                    stravaUploadId = uploadId,
                    lastAttemptEpochSec = nowSec(),
                    attemptCount = attemptCount,
                ),
            )
        }
        return status
    }

    private suspend fun recordOutcome(key: String, status: StravaUploadStatus, attemptCount: Int) {
        if (status.activityId != null) {
            ledger.record(
                key,
                StravaLedgerEntry(
                    state = StravaLedgerState.UPLOADED,
                    stravaUploadId = status.uploadId,
                    stravaActivityId = status.activityId,
                    lastAttemptEpochSec = nowSec(),
                    attemptCount = attemptCount + 1,
                ),
            )
        } else if (!status.error.isNullOrBlank()) {
            ledger.record(
                key,
                StravaLedgerEntry(
                    state = StravaLedgerState.FAILED_PERMANENT,
                    stravaUploadId = status.uploadId,
                    lastAttemptEpochSec = nowSec(),
                    attemptCount = attemptCount + 1,
                    lastErrorMessage = status.error,
                ),
            )
        }
        // else: left as PENDING_UPLOAD by pollUntilResolved above.
    }

    private fun failedRetryable(existing: StravaLedgerEntry?, message: String?) = StravaLedgerEntry(
        state = StravaLedgerState.FAILED_RETRYABLE,
        stravaUploadId = existing?.stravaUploadId,
        lastAttemptEpochSec = nowSec(),
        attemptCount = (existing?.attemptCount ?: 0) + 1,
        lastErrorMessage = message,
    )

    private fun failedPermanent(existing: StravaLedgerEntry?, message: String?) = StravaLedgerEntry(
        state = StravaLedgerState.FAILED_PERMANENT,
        stravaUploadId = existing?.stravaUploadId,
        lastAttemptEpochSec = nowSec(),
        attemptCount = (existing?.attemptCount ?: 0) + 1,
        lastErrorMessage = message,
    )

    private fun isInGlobalCooldown(): Boolean = nowSec() < cooldownUntilEpochSec

    private fun nowSec(): Long = Clock.System.now().epochSeconds

    companion object {
        private const val DEFAULT_COOLDOWN_SEC = 15 * 60L
        private const val MAX_POLL_ATTEMPTS = 6
        private const val POLL_DELAY_MS = 2000L
    }
}
