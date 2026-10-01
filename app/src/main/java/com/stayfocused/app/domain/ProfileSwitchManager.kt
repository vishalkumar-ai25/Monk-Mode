package com.stayfocused.app.domain

import androidx.room.withTransaction
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.domain.model.ProfileSwitchDecision

/**
 * Coordinates atomic focus profile switching against database state.
 * Enforces transactional Strict Mode invariants to eliminate TOCTOU race conditions.
 */
class ProfileSwitchManager(
    private val database: StayFocusedDatabase,
    val decisionEngine: ProfileSwitchDecisionEngine = ProfileSwitchDecisionEngine()
) {

    /**
     * Atomically evaluates and performs profile switching inside a Room transaction.
     * Guaranteed to block switching if Strict Mode is active at the moment of execution.
     */
    suspend fun switchProfileAtomically(targetProfile: FocusProfileEntity): ProfileSwitchDecision {
        return database.withTransaction {
            val activeStrict = database.strictSessionDao().getActiveStrictSessionSync()
            val now = System.currentTimeMillis()
            val isStrictActive = activeStrict != null && activeStrict.isActive && now < activeStrict.targetEndTime

            val currentActive = database.focusProfileDao().getActiveProfileSync()

            val decision = decisionEngine.evaluateSwitch(
                currentActiveProfile = currentActive,
                targetProfile = targetProfile,
                isStrictSessionActive = isStrictActive
            )

            if (decision is ProfileSwitchDecision.ImmediateSwitch) {
                database.focusProfileDao().switchToProfile(targetProfile.id)
            }

            decision
        }
    }
}
