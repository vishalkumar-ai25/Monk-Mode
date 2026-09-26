package com.stayfocused.app.domain

import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.domain.model.ProfileSwitchDecision

/**
 * Pure Kotlin decision engine for profile switching from the dashboard.
 * Enforces immediate switching for standard profiles and mandates confirmation
 * if the outgoing profile is running in Strict Mode.
 */
class ProfileSwitchDecisionEngine {

    /**
     * Evaluates a requested profile switch against current focus state.
     */
    fun evaluateSwitch(
        currentActiveProfile: FocusProfileEntity?,
        targetProfile: FocusProfileEntity
    ): ProfileSwitchDecision {
        if (currentActiveProfile != null && currentActiveProfile.isActive && currentActiveProfile.id == targetProfile.id) {
            return ProfileSwitchDecision.AlreadyActive(targetProfile)
        }

        if (currentActiveProfile != null && currentActiveProfile.isActive && currentActiveProfile.isStrictMode) {
            return ProfileSwitchDecision.RequiresConfirmation(
                outgoingProfile = currentActiveProfile,
                targetProfile = targetProfile
            )
        }

        return ProfileSwitchDecision.ImmediateSwitch(targetProfile)
    }
}
