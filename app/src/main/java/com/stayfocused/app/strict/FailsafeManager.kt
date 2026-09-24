package com.stayfocused.app.strict

import com.stayfocused.app.data.local.dao.RecoveryCodeDao
import com.stayfocused.app.data.local.dao.StrictSessionDao
import com.stayfocused.app.data.local.entities.RecoveryCodeEntity
import com.stayfocused.app.data.local.entities.StrictSessionEntity
import com.stayfocused.app.util.RecoveryCodeHasher

/**
 * Manages strict mode failsafes:
 * 1. Single-use, high-entropy 16-character emergency recovery codes (PBKDF2/SHA-256 hashed).
 * 2. 24–48 hour time-delayed unlock flow preventing impulsive overrides of strict focus sessions.
 */
class FailsafeManager(
    private val recoveryCodeDao: RecoveryCodeDao,
    private val strictSessionDao: StrictSessionDao,
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
        val entity = recoveryCodeDao.getRecoveryCodeSync() ?: return false
        if (entity.isConsumed) return false

        val isValid = RecoveryCodeHasher.verify(enteredCode, entity.salt, entity.passwordHash)
        if (isValid) {
            recoveryCodeDao.markConsumed()
            strictSessionDao.deactivateAllSessions()
            return true
        }
        return false
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
        val requestTime = session.delayedUnlockRequestTime ?: return false

        val elapsed = currentTimeMs - requestTime
        if (elapsed >= session.delayedUnlockDurationMs) {
            val deactivated = session.copy(isActive = false)
            strictSessionDao.updateSession(deactivated)
            return true
        }
        return false
    }

    /**
     * Calculates the remaining milliseconds before the delayed unlock can be finalized.
     * Returns null if no delayed unlock request is active.
     */
    fun getDelayedUnlockRemainingMs(
        session: StrictSessionEntity,
        currentTimeMs: Long = timeProvider()
    ): Long? {
        val requestTime = session.delayedUnlockRequestTime ?: return null
        val targetUnlockTime = requestTime + session.delayedUnlockDurationMs
        return (targetUnlockTime - currentTimeMs).coerceAtLeast(0L)
    }
}
