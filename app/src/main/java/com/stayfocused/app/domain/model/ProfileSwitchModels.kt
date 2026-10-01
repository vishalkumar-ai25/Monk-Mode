package com.stayfocused.app.domain.model

import com.stayfocused.app.data.local.entities.FocusProfileEntity

/**
 * Result of evaluating a requested profile switch from the dashboard.
 */
sealed class ProfileSwitchDecision {

    /**
     * The requested profile is already the currently active profile. No state mutation required.
     */
    data class AlreadyActive(val profile: FocusProfileEntity) : ProfileSwitchDecision()

    /**
     * Profile can be immediately activated in Room without restrictions.
     */
    data class ImmediateSwitch(val targetProfile: FocusProfileEntity) : ProfileSwitchDecision()

    /**
     * Profile switch is blocked because a Strict Mode session is currently active.
     * Prevents anti-relapse bypasses.
     */
    data class BlockedByStrictMode(val targetProfile: FocusProfileEntity) : ProfileSwitchDecision()
}
