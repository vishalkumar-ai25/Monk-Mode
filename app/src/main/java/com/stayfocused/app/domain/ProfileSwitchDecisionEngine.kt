package com.stayfocused.app.domain

import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.domain.model.ProfileSwitchDecision

/**
 * Pure Kotlin decision engine for profile switching from the dashboard.
 * Enforces immediate switching for standard profiles and hard-blocks profile switching
 * if the outgoing profile is running in Strict Mode or if a strict session is active.
 */
class ProfileSwitchDecisionEngine {

    /**
     * Evaluates a requested profile switch against current focus state and Strict Mode enforcement.
     */
    fun evaluateSwitch(
        currentActiveProfile: FocusProfileEntity?,
        targetProfile: FocusProfileEntity,
        isStrictModeActive: Boolean = false
    ): ProfileSwitchDecision {
        if (currentActiveProfile != null && currentActiveProfile.isActive && currentActiveProfile.id == targetProfile.id) {
            return ProfileSwitchDecision.AlreadyActive(targetProfile)
        }

        // Hard-block profile switching if outgoing profile is strict OR if a strict session is active
        if (isStrictModeActive || (currentActiveProfile != null && currentActiveProfile.isActive && currentActiveProfile.isStrictMode)) {
            return ProfileSwitchDecision.BlockedByStrictMode(outgoingProfile = currentActiveProfile)
        }

        return ProfileSwitchDecision.ImmediateSwitch(targetProfile)
    }
}
