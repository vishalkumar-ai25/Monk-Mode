package com.stayfocused.app.domain

import com.stayfocused.app.data.local.entities.AppLimitEntity

sealed interface GoalValidationResult {
    data object Valid : GoalValidationResult
    data class Invalid(val reason: String) : GoalValidationResult
}

sealed interface RatchetValidationResult {
    data object Valid : RatchetValidationResult
    data class Violation(val reason: String) : RatchetValidationResult
}

/**
 * Pure Kotlin validator for goal dependencies and strict mode monotonic ratchets.
 * Prevents circular deadlocks, self-referential lockouts, and strict policy tampering.
 */
object GoalDependencyValidator {

    const val SELF_PACKAGE = "com.stayfocused.app"

    /**
     * Validates proposed goal app configuration against deadlocks and circular graphs.
     */
    fun validateGoalConfig(
        targetPackage: String,
        goalPackage: String?,
        goalDurationMinutes: Int,
        existingLimits: List<AppLimitEntity>
    ): GoalValidationResult {
        if (goalPackage.isNullOrBlank() || goalDurationMinutes <= 0) {
            return GoalValidationResult.Valid
        }

        val target = targetPackage.trim().lowercase()
        val goal = goalPackage.trim().lowercase()

        // 1. Self-dependency check
        if (target == goal) {
            return GoalValidationResult.Invalid("An app cannot have itself as a prerequisite goal app.")
        }

        // 2. Monk Mode protection
        if (goal == SELF_PACKAGE) {
            return GoalValidationResult.Invalid("Monk Mode cannot be selected as a prerequisite goal app.")
        }

        // 3. Directed-graph cycle detection (DFS)
        // Construct adjacency graph: Edge X -> Y means App X requires Goal App Y
        val graph = mutableMapOf<String, String>()
        for (limit in existingLimits) {
            val pkg = limit.packageName.trim().lowercase()
            val existingGoal = limit.goalPackageName?.trim()?.lowercase()
            if (!existingGoal.isNullOrEmpty() && limit.goalDurationMinutes > 0 && pkg != target) {
                graph[pkg] = existingGoal
            }
        }
        // Add the proposed edge
        graph[target] = goal

        // Detect if following edges from 'target' cycles back to 'target'
        val visited = mutableSetOf<String>()
        var current: String? = goal
        while (!current.isNullOrEmpty()) {
            if (current == target) {
                return GoalValidationResult.Invalid(
                    "Circular goal dependency detected: '$target' and '$goal' mutually depend on each other."
                )
            }
            if (!visited.add(current)) {
                // Cycle in another part of graph
                break
            }
            current = graph[current]
        }

        return GoalValidationResult.Valid
    }

    /**
     * Validates that new limit configuration does not loosen existing restrictions
     * while Strict Mode is active.
     */
    fun validateStrictModeRatchet(
        existing: AppLimitEntity,
        newConfig: AppLimitEntity
    ): RatchetValidationResult {
        // Daily Screen Time Limit: cannot increase, cannot set to 0 if previously > 0
        if (existing.dailyTimeLimitMinutes > 0) {
            if (newConfig.dailyTimeLimitMinutes == 0 || newConfig.dailyTimeLimitMinutes > existing.dailyTimeLimitMinutes) {
                return RatchetValidationResult.Violation(
                    "Cannot increase or remove daily screen time limit (${existing.dailyTimeLimitMinutes}m) while Strict Mode is active."
                )
            }
        }

        // Daily Launch Limit: cannot increase, cannot set to 0 if previously > 0
        if (existing.dailyLaunchLimit > 0) {
            if (newConfig.dailyLaunchLimit == 0 || newConfig.dailyLaunchLimit > existing.dailyLaunchLimit) {
                return RatchetValidationResult.Violation(
                    "Cannot increase or remove daily launch limit (${existing.dailyLaunchLimit}) while Strict Mode is active."
                )
            }
        }

        // Session Allowance: cannot increase, cannot set to 0 if previously > 0
        if (existing.sessionAllowanceMinutes > 0) {
            if (newConfig.sessionAllowanceMinutes == 0 || newConfig.sessionAllowanceMinutes > existing.sessionAllowanceMinutes) {
                return RatchetValidationResult.Violation(
                    "Cannot increase or remove session allowance (${existing.sessionAllowanceMinutes}m) while Strict Mode is active."
                )
            }
        }

        // Cooldown Duration: cannot decrease
        if (existing.cooldownDurationMinutes > 0) {
            if (newConfig.cooldownDurationMinutes < existing.cooldownDurationMinutes) {
                return RatchetValidationResult.Violation(
                    "Cannot reduce cooldown duration (${existing.cooldownDurationMinutes}m) while Strict Mode is active."
                )
            }
        }

        // Goal App: cannot be nullified or changed to a different package
        if (!existing.goalPackageName.isNullOrBlank()) {
            if (newConfig.goalPackageName.isNullOrBlank() ||
                !newConfig.goalPackageName.equals(existing.goalPackageName, ignoreCase = true)
            ) {
                return RatchetValidationResult.Violation(
                    "Cannot remove or change prerequisite goal app while Strict Mode is active."
                )
            }
            // Goal Duration: cannot decrease
            if (newConfig.goalDurationMinutes < existing.goalDurationMinutes) {
                return RatchetValidationResult.Violation(
                    "Cannot reduce prerequisite goal duration (${existing.goalDurationMinutes}m) while Strict Mode is active."
                )
            }
        }

        return RatchetValidationResult.Valid
    }
}
