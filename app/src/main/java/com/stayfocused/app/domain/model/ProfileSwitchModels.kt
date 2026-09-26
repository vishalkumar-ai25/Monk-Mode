package com.stayfocused.app.domain.model

import com.stayfocused.app.data.local.entities.FocusProfileEntity

/**
 * Result of evaluating a requested profile switch from the dashboard.
 */
sealed class ProfileSwitchDecision {
    /**
     * Target profile is already active; no state transition needed.
     */
    data class AlreadyActive(val profile: FocusProfileEntity) : ProfileSwitchDecision()

    /**
     * Outgoing profile is not strict (or null); switch executes immediately without user confirmation.
     */
    data class ImmediateSwitch(val targetProfile: FocusProfileEntity) : ProfileSwitchDecision()

    /**
     * Outgoing profile has Strict Mode enabled; requires explicit confirmation dialog before switching.
     */
    data class RequiresConfirmation(
        val outgoingProfile: FocusProfileEntity,
        val targetProfile: FocusProfileEntity
    ) : ProfileSwitchDecision()
}
