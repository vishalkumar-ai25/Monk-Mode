package com.stayfocused.app.domain

/**
 * Result returned by [SessionCooldownTracker.tick].
 */
sealed interface SessionCooldownTickResult {
    data object ActiveSessionContinues : SessionCooldownTickResult
    data object NoLimitConfigured : SessionCooldownTickResult
    data object ScreenOffPaused : SessionCooldownTickResult
    data class TriggerCooldown(
        val packageName: String,
        val cooldownDurationMinutes: Int,
        val startElapsedRealtime: Long,
        val activeUntilWallMs: Long,
        val bootCount: Int
    ) : SessionCooldownTickResult
}

/**
 * State of an active continuous foreground session for a package.
 */
data class ContinuousSessionState(
    val packageName: String,
    val accumulatedActiveMs: Long = 0L,
    val lastTickElapsed: Long = 0L,
    val lastPausedElapsed: Long = 0L
)

/**
 * Pure Kotlin tracker for continuous foreground usage and anti-binge cooldown triggers.
 * Zero Android framework dependencies for 100% JVM unit testability.
 */
class SessionCooldownTracker {

    private val sessionStates = mutableMapOf<String, ContinuousSessionState>()

    /**
     * Called when a package is foregrounded. Reconciles inactivity time.
     * To prevent the "2-minute exit" binge evasion bypass, the accumulated continuous session time
     * only resets if the user remained away from the package for at least the full cooldown duration
     * (or a minimum of 5 minutes).
     */
    @Synchronized
    fun onPackageForegrounded(
        packageName: String,
        nowElapsed: Long,
        cooldownDurationMinutes: Int
    ) {
        val lower = packageName.trim().lowercase()
        val existing = sessionStates[lower] ?: run {
            sessionStates[lower] = ContinuousSessionState(
                packageName = lower,
                accumulatedActiveMs = 0L,
                lastTickElapsed = nowElapsed,
                lastPausedElapsed = 0L
            )
            return
        }

        if (existing.lastPausedElapsed > 0L) {
            val awayDuration = maxOf(0L, nowElapsed - existing.lastPausedElapsed)
            val resetThreshold = maxOf(cooldownDurationMinutes * 60_000L, 5 * 60_000L)

            if (awayDuration >= resetThreshold) {
                // User took a genuine break -> reset continuous session
                sessionStates[lower] = ContinuousSessionState(
                    packageName = lower,
                    accumulatedActiveMs = 0L,
                    lastTickElapsed = nowElapsed,
                    lastPausedElapsed = 0L
                )
            } else {
                // User returned quickly -> resume without resetting, only updating tick timestamp
                sessionStates[lower] = existing.copy(
                    lastTickElapsed = nowElapsed,
                    lastPausedElapsed = 0L
                )
            }
        } else {
            sessionStates[lower] = existing.copy(lastTickElapsed = nowElapsed)
        }
    }

    /**
     * Called when a package is backgrounded or screen turns off.
     */
    @Synchronized
    fun onPackageBackgrounded(packageName: String, nowElapsed: Long) {
        val lower = packageName.trim().lowercase()
        val existing = sessionStates[lower] ?: return
        val elapsedSinceLastTick = maxOf(0L, nowElapsed - existing.lastTickElapsed)
        sessionStates[lower] = existing.copy(
            accumulatedActiveMs = existing.accumulatedActiveMs + elapsedSinceLastTick,
            lastTickElapsed = nowElapsed,
            lastPausedElapsed = nowElapsed
        )
    }

    /**
     * Periodic 1-second tick executed by the foreground monitor loop.
     * Accumulates active time and triggers cooldown when session allowance is reached.
     */
    @Synchronized
    fun tick(
        packageName: String,
        sessionAllowanceMinutes: Int,
        cooldownDurationMinutes: Int,
        nowElapsed: Long,
        nowWallMs: Long,
        currentBootCount: Int,
        isScreenInteractive: Boolean
    ): SessionCooldownTickResult {
        if (sessionAllowanceMinutes <= 0 || cooldownDurationMinutes <= 0) {
            return SessionCooldownTickResult.NoLimitConfigured
        }

        if (!isScreenInteractive) {
            return SessionCooldownTickResult.ScreenOffPaused
        }

        val lower = packageName.trim().lowercase()
        val current = sessionStates[lower] ?: ContinuousSessionState(
            packageName = lower,
            accumulatedActiveMs = 0L,
            lastTickElapsed = nowElapsed
        )

        val delta = if (current.lastTickElapsed > 0L) {
            maxOf(0L, nowElapsed - current.lastTickElapsed)
        } else {
            0L
        }

        val newAccumulated = current.accumulatedActiveMs + delta
        val allowanceMs = sessionAllowanceMinutes * 60_000L

        return if (newAccumulated >= allowanceMs) {
            // Cooldown threshold reached!
            // CRITICAL ADVERSARIAL GUARD: Atomically reset accumulated continuous session to 0L
            // so upon cooldown expiration, it doesn't immediately re-trigger another cooldown.
            sessionStates[lower] = ContinuousSessionState(
                packageName = lower,
                accumulatedActiveMs = 0L,
                lastTickElapsed = nowElapsed,
                lastPausedElapsed = nowElapsed
            )

            val cooldownTotalMs = cooldownDurationMinutes * 60_000L
            SessionCooldownTickResult.TriggerCooldown(
                packageName = lower,
                cooldownDurationMinutes = cooldownDurationMinutes,
                startElapsedRealtime = nowElapsed,
                activeUntilWallMs = nowWallMs + cooldownTotalMs,
                bootCount = currentBootCount
            )
        } else {
            sessionStates[lower] = current.copy(
                accumulatedActiveMs = newAccumulated,
                lastTickElapsed = nowElapsed
            )
            SessionCooldownTickResult.ActiveSessionContinues
        }
    }

    /**
     * Clears tracking state for a package (e.g. when limit is deleted).
     */
    @Synchronized
    fun clearState(packageName: String) {
        sessionStates.remove(packageName.trim().lowercase())
    }

    /**
     * Inspect current accumulated active milliseconds (for testing and UI inspection).
     */
    @Synchronized
    fun getAccumulatedActiveMs(packageName: String): Long {
        return sessionStates[packageName.trim().lowercase()]?.accumulatedActiveMs ?: 0L
    }
}
