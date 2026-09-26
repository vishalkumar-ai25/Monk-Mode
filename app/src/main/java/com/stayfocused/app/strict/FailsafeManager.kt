package com.stayfocused.app.strict

import com.stayfocused.app.data.local.dao.FailsafeLogDao
import com.stayfocused.app.data.local.dao.RecoveryCodeDao
import com.stayfocused.app.data.local.dao.StrictSessionDao
import com.stayfocused.app.data.local.entities.FailsafeEventType
import com.stayfocused.app.data.local.entities.FailsafeLogEntity
import com.stayfocused.app.data.local.entities.RecoveryCodeEntity
import com.stayfocused.app.data.local.entities.StrictSessionEntity
import com.stayfocused.app.util.RecoveryCodeHasher

/**
 * Manages strict mode failsafes:
 * 1. Single-use, high-entropy 16-character emergency recovery codes (PBKDF2/SHA-256 hashed).
 * 2. 24–48 hour time-delayed unlock flow preventing impulsive overrides of strict focus sessions.
 * 3. Append-only tamper-evident audit logging for all failsafe events.
 */
class FailsafeManager(
    private val recoveryCodeDao: RecoveryCodeDao,
    private val strictSessionDao: StrictSessionDao,
    private val failsafeLogDao: FailsafeLogDao? = null,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {

    companion object {
        const val MIN_DELAY_UNLOCK_MS: Long = 24 * 60 * 60 * 1000L // 24 hours
        const val MAX_DELAY_UNLOCK_MS: Long = 48 * 60 * 60 * 1000L // 48 hours
    }

    /**
     * Generates a 16-character emergency recovery code, computes its PBKDF2/SHA-256 hash with random salt,
     * stores the hash in [RecoveryCodeDao], and returns the plain-text code once.
     * The plain code cannot be retrieved again from storage.
     */
    suspend fun generateAndStoreRecoveryCode(): String {
        val plainCode = RecoveryCodeHasher.generate16CharRecoveryCode()
        val salt = RecoveryCodeHasher.generateSalt()
        val hash = RecoveryCodeHasher.hashRecoveryCode(plainCode, salt)

        val entity = RecoveryCodeEntity(
            id = 1,
            passwordHash = hash,
            salt = salt,
            isConsumed = false,
            createdAt = timeProvider()
        )
        recoveryCodeDao.upsertRecoveryCode(entity)
        return plainCode
    }

    /**
     * Verifies the entered recovery code against the stored hash and salt.
     * If valid and unconsumed, consumes the code and deactivates all active strict sessions.
     */
    suspend fun verifyAndConsumeRecoveryCode(enteredCode: String): Boolean {
        val entity = recoveryCodeDao.getRecoveryCodeSync()
        if (entity == null || entity.isConsumed) {
            failsafeLogDao?.insertLog(
                FailsafeLogEntity(
                    timestamp = timeProvider(),
                    eventType = FailsafeEventType.RECOVERY_CODE_ENTERED.name,
                    details = "Emergency recovery code verification rejected (uninitialized or already consumed)",
                    success = false
                )
            )
            return false
        }

        val isValid = RecoveryCodeHasher.verify(enteredCode, entity.salt, entity.passwordHash)
        if (isValid) {
            recoveryCodeDao.markConsumed()
            strictSessionDao.deactivateAllSessions()
            failsafeLogDao?.insertLog(
                FailsafeLogEntity(
                    timestamp = timeProvider(),
                    eventType = FailsafeEventType.RECOVERY_CODE_ENTERED.name,
                    details = "Emergency recovery code verified; strict session deactivated",
                    success = true
                )
            )
            return true
        } else {
            failsafeLogDao?.insertLog(
                FailsafeLogEntity(
                    timestamp = timeProvider(),
                    eventType = FailsafeEventType.RECOVERY_CODE_ENTERED.name,
                    details = "Emergency recovery code verification failed (incorrect code)",
                    success = false
                )
            )
            return false
        }
    }

    /**
     * Initiates a time-delayed unlock request for the specified active strict session.
     */
    suspend fun requestDelayedUnlock(
        sessionId: Long,
        delayDurationMs: Long = MIN_DELAY_UNLOCK_MS
    ): Boolean {
        val session = strictSessionDao.getSessionById(sessionId)
            ?: strictSessionDao.getActiveStrictSessionSync()
            ?: return false

        if (!session.isActive) return false

        val duration = delayDurationMs.coerceIn(MIN_DELAY_UNLOCK_MS, MAX_DELAY_UNLOCK_MS)
        val updated = session.copy(
            delayedUnlockRequestTime = timeProvider(),
            delayedUnlockDurationMs = duration
        )
        strictSessionDao.updateSession(updated)

        val hours = duration / (60 * 60 * 1000L)
        failsafeLogDao?.insertLog(
            FailsafeLogEntity(
                timestamp = timeProvider(),
                eventType = FailsafeEventType.DELAY_REQUESTED.name,
                details = "Requested ${hours}h delayed unlock for Strict Session #${session.id}",
                success = true
            )
        )
        return true
    }

    /**
     * Cancels an existing delayed unlock request.
     */
    suspend fun cancelDelayedUnlock(sessionId: Long): Boolean {
        val session = strictSessionDao.getSessionById(sessionId)
            ?: strictSessionDao.getActiveStrictSessionSync()
            ?: return false

        val updated = session.copy(delayedUnlockRequestTime = null)
        strictSessionDao.updateSession(updated)

        failsafeLogDao?.insertLog(
            FailsafeLogEntity(
                timestamp = timeProvider(),
                eventType = FailsafeEventType.DELAY_CANCELLED.name,
                details = "Cancelled delayed unlock for Strict Session #${session.id}",
                success = true
            )
        )
        return true
    }

    /**
     * Attempts to finalize a time-delayed unlock. Only succeeds if the requested delay duration
     * has fully elapsed.
     */
    suspend fun tryFinalizeDelayedUnlock(
        sessionId: Long,
        currentTimeMs: Long = timeProvider()
    ): Boolean {
        val session = strictSessionDao.getSessionById(sessionId)
            ?: strictSessionDao.getActiveStrictSessionSync()
            ?: return false

        if (!session.isActive) return false
        val reqTime = session.delayedUnlockRequestTime ?: return false
        val duration = session.delayedUnlockDurationMs

        if (currentTimeMs - reqTime >= duration) {
            strictSessionDao.deactivateAllSessions()
            failsafeLogDao?.insertLog(
                FailsafeLogEntity(
                    timestamp = currentTimeMs,
                    eventType = FailsafeEventType.DELAY_FINALIZED.name,
                    details = "Finalized delayed unlock for Strict Session #${session.id}; sessions deactivated",
                    success = true
                )
            )
            return true
        }
        return false
    }

    /**
     * Returns true if there is an active delayed unlock request pending for [session].
     */
    fun isUnlockPending(session: StrictSessionEntity): Boolean {
        return session.isActive && session.delayedUnlockRequestTime != null
    }

    /**
     * Calculates the remaining time in milliseconds before the delayed unlock can be finalized.
     * Returns 0 if the delay has already fully elapsed or if no delay was requested.
     */
    fun getRemainingDelayMs(session: StrictSessionEntity, currentTimeMs: Long = timeProvider()): Long {
        if (!isUnlockPending(session)) return 0L
        val reqTime = session.delayedUnlockRequestTime ?: return 0L
        val unlockTime = reqTime + session.delayedUnlockDurationMs
        val remaining = unlockTime - currentTimeMs
        return if (remaining > 0L) remaining else 0L
    }

    fun getDelayedUnlockRemainingMs(session: StrictSessionEntity, currentTimeMs: Long = timeProvider()): Long {
        return getRemainingDelayMs(session, currentTimeMs)
    }
}
