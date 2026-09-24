package com.stayfocused.app.breaks

import com.stayfocused.app.data.local.entities.BreakSessionEntity

/**
 * Pure Kotlin decision engine for temporary break evaluation.
 * Enforces business rules:
 * 1. Breaks allow temporary access to blocked apps for a defined duration.
 * 2. Breaks are strictly forbidden if a Strict Mode session is active.
 * 3. Break sessions expire automatically once the end timestamp has passed.
 */
class BreakDecisionEngine {

    fun isBreakActive(
        currentTimeMs: Long,
        breakSession: BreakSessionEntity?,
        isStrictModeActive: Boolean
    ): Boolean {
        // Integrity Guard: Strict mode prohibits breaks
        if (isStrictModeActive) {
            return false
        }

        if (breakSession == null || !breakSession.isActive) {
            return false
        }

        return currentTimeMs < breakSession.endTime
    }

    fun calculateRemainingSeconds(
        currentTimeMs: Long,
        breakSession: BreakSessionEntity?
    ): Long {
        if (breakSession == null || !breakSession.isActive) {
            return 0L
        }

        val remainingMs = breakSession.endTime - currentTimeMs
        return if (remainingMs > 0) remainingMs / 1000L else 0L
    }

    fun createBreakSession(
        currentTimeMs: Long,
        durationMinutes: Int
    ): BreakSessionEntity {
        val durationMs = durationMinutes * 60 * 1000L
        return BreakSessionEntity(
            id = 1L,
            startTime = currentTimeMs,
            endTime = currentTimeMs + durationMs,
            durationMinutes = durationMinutes,
            isActive = true
        )
    }
}
