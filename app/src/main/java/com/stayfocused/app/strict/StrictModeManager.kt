package com.stayfocused.app.strict

import android.content.Context
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.data.local.entities.FailsafeLogEntity
import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.data.local.entities.StrictSessionEntity
import com.stayfocused.app.util.RecoveryCodeHasher

data class StrictSessionState(
    val isActive: Boolean,
    val session: StrictSessionEntity?,
    val remainingMillis: Long,
    val unlockMethod: StrictUnlockMethod,
    val isDelayedUnlockPending: Boolean,
    val delayedUnlockRemainingMillis: Long
)

/**
 * High-level manager coordinating Strict Mode sessions, anti-tamper constraints,
 * and multi-layered friction deactivation methods.
 */
class StrictModeManager(
    private val database: StayFocusedDatabase,
    private val preferences: StrictPreferences,
    private val failsafeManager: FailsafeManager,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {
    constructor(context: Context) : this(
        database = StayFocusedDatabase.getInstance(context),
        preferences = StrictPreferences(context),
        failsafeManager = FailsafeManager(
            StayFocusedDatabase.getInstance(context).recoveryCodeDao(),
            StayFocusedDatabase.getInstance(context).strictSessionDao(),
            StayFocusedDatabase.getInstance(context).failsafeLogDao()
        )
    )

    /**
     * Activates a new Strict Mode session for the specified duration and unlock method.
     */
    suspend fun startStrictSession(
        durationMinutes: Int,
        unlockMethod: StrictUnlockMethod,
        pin: String? = null,
        typingPhrase: String? = null,
        blockSettings: Boolean = true
    ): Boolean {
        val now = timeProvider()
        val durationMs = durationMinutes * 60 * 1000L
        val targetEndTime = now + durationMs

        // 1. Ensure at least one profile exists in focus_profiles to satisfy foreign key constraint
        val profileDao = database.focusProfileDao()
        val existingProfiles = profileDao.getAllProfilesSync()
        val profileId = if (existingProfiles.isEmpty()) {
            profileDao.upsertProfile(
                FocusProfileEntity(
                    id = 1,
                    name = "Monk Mode",
                    isActive = true,
                    isStrictMode = true,
                    activeDaysMask = 127
                )
            )
        } else {
            val active = existingProfiles.firstOrNull { it.isActive }
            active?.id ?: existingProfiles.first().id
        }

        // 2. Configure preferences
        preferences.unlockMethod = unlockMethod
        preferences.lastDurationMinutes = durationMinutes
        preferences.blockSettings = blockSettings

        if (unlockMethod == StrictUnlockMethod.PIN && !pin.isNullOrBlank()) {
            val salt = RecoveryCodeHasher.generateSalt()
            val hash = RecoveryCodeHasher.hashRecoveryCode(pin.trim(), salt)
            preferences.pinSalt = salt
            preferences.pinHash = hash
        }

        if (unlockMethod == StrictUnlockMethod.TYPING_PHRASE) {
            preferences.typingPhrase = typingPhrase?.trim()?.ifBlank { StrictPreferences.DEFAULT_PHRASE }
                ?: StrictPreferences.DEFAULT_PHRASE
        }

        // 3. Clear any active session and insert new strict session
        val strictDao = database.strictSessionDao()
        strictDao.deactivateAllSessions()

        val session = StrictSessionEntity(
            profileId = profileId,
            startTime = now,
            targetEndTime = targetEndTime,
            delayedUnlockRequestTime = null,
            delayedUnlockDurationMs = 15 * 60 * 1000L, // default 15m delay
            isActive = true
        )
        val sessionId = strictDao.insertSession(session)

        // 4. Record audit log
        database.failsafeLogDao().insertLog(
            FailsafeLogEntity(
                timestamp = now,
                eventType = "STRICT_SESSION_STARTED",
                details = "Started ${durationMinutes}m Strict Mode ($unlockMethod) [Session #$sessionId]",
                success = true
            )
        )

        return true
    }

    /**
     * Checks whether a strict session is currently active and non-expired.
     */
    fun isSessionActive(session: StrictSessionEntity?): Boolean {
        if (session == null || !session.isActive) return false
        val now = timeProvider()
        return now < session.targetEndTime
    }

    /**
     * Inspects active session and evaluates current countdown and unlock states.
     */
    fun evaluateState(session: StrictSessionEntity?): StrictSessionState {
        val now = timeProvider()
        if (session == null || !session.isActive || now >= session.targetEndTime) {
            return StrictSessionState(
                isActive = false,
                session = session,
                remainingMillis = 0L,
                unlockMethod = preferences.unlockMethod,
                isDelayedUnlockPending = false,
                delayedUnlockRemainingMillis = 0L
            )
        }

        val remainingMs = (session.targetEndTime - now).coerceAtLeast(0L)
        val requestTime = session.delayedUnlockRequestTime
        val isDelayedPending = requestTime != null
        val delayedRemaining = if (requestTime != null) {
            val targetUnlock = requestTime + session.delayedUnlockDurationMs
            (targetUnlock - now).coerceAtLeast(0L)
        } else 0L

        return StrictSessionState(
            isActive = true,
            session = session,
            remainingMillis = remainingMs,
            unlockMethod = preferences.unlockMethod,
            isDelayedUnlockPending = isDelayedPending,
            delayedUnlockRemainingMillis = delayedRemaining
        )
    }

    /**
     * Deactivates all active strict sessions and records audit log.
     */
    suspend fun stopStrictSession(reason: String = "Manual deactivation"): Boolean {
        val now = timeProvider()
        database.strictSessionDao().deactivateAllSessions()
        database.failsafeLogDao().insertLog(
            FailsafeLogEntity(
                timestamp = now,
                eventType = "STRICT_SESSION_ENDED",
                details = reason,
                success = true
            )
        )
        return true
    }

    /**
     * Verifies the entered PIN against the stored hash and deactivates strict session if matched.
     */
    suspend fun verifyAndUnlockWithPin(enteredPin: String): Boolean {
        val salt = preferences.pinSalt
        val expectedHash = preferences.pinHash
        val now = timeProvider()

        if (salt.isNullOrEmpty() || expectedHash.isNullOrEmpty()) {
            // No PIN configured; allow fallback deactivation
            stopStrictSession("Deactivated with PIN (unconfigured fallback)")
            return true
        }

        val isMatch = RecoveryCodeHasher.verify(enteredPin.trim(), salt, expectedHash)
        if (isMatch) {
            stopStrictSession("Deactivated with valid PIN")
            database.failsafeLogDao().insertLog(
                FailsafeLogEntity(
                    timestamp = now,
                    eventType = "STRICT_PIN_ENTERED",
                    details = "Correct PIN entered; Strict Mode unlocked",
                    success = true
                )
            )
            return true
        } else {
            database.failsafeLogDao().insertLog(
                FailsafeLogEntity(
                    timestamp = now,
                    eventType = "STRICT_PIN_ENTERED",
                    details = "Incorrect PIN attempt",
                    success = false
                )
            )
            return false
        }
    }

    /**
     * Verifies the entered typing challenge phrase and deactivates strict session if matched.
     */
    suspend fun verifyAndUnlockWithPhrase(enteredText: String): Boolean {
        val targetPhrase = preferences.typingPhrase.trim()
        val normalizedInput = enteredText.trim()
        val now = timeProvider()

        val isMatch = normalizedInput.equals(targetPhrase, ignoreCase = false)
        if (isMatch) {
            stopStrictSession("Deactivated with typing challenge completion")
            database.failsafeLogDao().insertLog(
                FailsafeLogEntity(
                    timestamp = now,
                    eventType = "STRICT_PHRASE_ENTERED",
                    details = "Friction typing challenge completed; Strict Mode unlocked",
                    success = true
                )
            )
            return true
        } else {
            database.failsafeLogDao().insertLog(
                FailsafeLogEntity(
                    timestamp = now,
                    eventType = "STRICT_PHRASE_ENTERED",
                    details = "Friction typing challenge failed (mismatched text)",
                    success = false
                )
            )
            return false
        }
    }

    /**
     * Initiates a time-delayed unlock request (e.g. 15 minutes cooldown).
     */
    suspend fun requestDelayedUnlock(delayMinutes: Int = 15): Boolean {
        val activeSession = database.strictSessionDao().getActiveStrictSessionSync() ?: return false
        val delayMs = delayMinutes * 60 * 1000L
        return failsafeManager.requestDelayedUnlock(activeSession.id, delayMs)
    }

    /**
     * Cancels an existing delayed unlock cooldown.
     */
    suspend fun cancelDelayedUnlock(): Boolean {
        val activeSession = database.strictSessionDao().getActiveStrictSessionSync() ?: return false
        return failsafeManager.cancelDelayedUnlock(activeSession.id)
    }

    /**
     * Finalizes delayed unlock if delay has elapsed.
     */
    suspend fun tryFinalizeDelayedUnlock(): Boolean {
        val activeSession = database.strictSessionDao().getActiveStrictSessionSync() ?: return false
        return failsafeManager.tryFinalizeDelayedUnlock(activeSession.id, timeProvider())
    }

    /**
     * Verifies and consumes single-use emergency recovery code.
     */
    suspend fun verifyAndConsumeRecoveryCode(code: String): Boolean {
        return failsafeManager.verifyAndConsumeRecoveryCode(code)
    }

    /**
     * Generates a new 16-character emergency recovery code.
     */
    suspend fun generateRecoveryCode(): String {
        return failsafeManager.generateAndStoreRecoveryCode()
    }
}
