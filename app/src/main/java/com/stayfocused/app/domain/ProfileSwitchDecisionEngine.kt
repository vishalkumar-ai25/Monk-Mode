package com.stayfocused.app.domain

import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.domain.model.ProfileSwitchDecision

/**
 * Pure Kotlin decision engine for profile switching from the dashboard.
 * Enforces immediate switching for standard profiles and strictly blocks
 * any profile switching when Strict Mode is actively engaged.
 */
class ProfileSwitchDecisionEngine {

    /**
     * Evaluates a requested profile switch against current focus state and strict session liveness.
     *
     * @param currentActiveProfile The currently active profile in Room, if any.
     * @param targetProfile The profile the user clicked to activate.
     * @param isStrictSessionActive True if an active strict session exists in Room.
     */
    fun evaluateSwitch(
        currentActiveProfile: FocusProfileEntity?,
        targetProfile: FocusProfileEntity,
        isStrictSessionActive: Boolean = false
    ): ProfileSwitchDecision {
        if (currentActiveProfile != null && currentActiveProfile.isActive && currentActiveProfile.id == targetProfile.id) {
            return ProfileSwitchDecision.AlreadyActive(targetProfile)
        }

        // Anti-Relapse Rule: Strict Mode actively locks all profile switching
        if (isStrictSessionActive) {
            return ProfileSwitchDecision.BlockedByStrictMode(targetProfile)
        }

        return ProfileSwitchDecision.ImmediateSwitch(targetProfile)
    }
}
