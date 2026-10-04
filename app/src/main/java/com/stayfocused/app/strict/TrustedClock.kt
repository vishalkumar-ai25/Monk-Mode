package com.stayfocused.app.strict

import com.stayfocused.app.data.local.entities.StrictSessionEntity

/**
 * Snapshot of system hardware clocks at a point in time.
 * Pure Kotlin data structure with zero Android framework dependencies.
 */
data class ClockSnapshot(
    val elapsedRealtimeMs: Long,
    val wallTimeMs: Long,
    val bootCount: Int = -1
)

/**
 * Pure, framework-free monotonic clock engine for Strict Mode and delayed unlock timing.
 *
 * Replaces wall-clock reliance with monotonic hardware uptime (`SystemClock.elapsedRealtime()`)
 * accumulated across checkpoints and reboots.
 *
 * Core Guarantees:
 * 1. Setting the device wall clock forward (e.g. 48h) does not advance active strict duration.
 * 2. Powering off the device clamps offline duration to 0 ms, preventing shutdown clock-manipulation bypasses.
 * 3. Dual-condition reboot detection protects against unreadable `Settings.Global.BOOT_COUNT` on OEM ROMs.
 * 4. Same-boot clock discrepancies trigger [onClockTamper] callback for security auditing.
 */
class TrustedClock(
    private val tamperToleranceMs: Long = DEFAULT_TAMPER_TOLERANCE_MS
) {

    companion object {
        const val DEFAULT_TAMPER_TOLERANCE_MS: Long = 60_000L // 1 minute
    }

    /**
     * Determines whether [currentSnapshot] represents a device reboot compared to [session].
     *
     * Dual-condition verification:
     * 1. `bootCount` mismatch when both counts are valid (non-negative).
     * 2. Hardware elapsed realtime rollback (`currentElapsed < lastElapsed`), providing
     *    mathematical proof of a reboot even if `Settings.Global.BOOT_COUNT` is unavailable (-1).
     */
    fun isReboot(session: StrictSessionEntity, currentSnapshot: ClockSnapshot): Boolean {
        val bootCountChanged = currentSnapshot.bootCount != -1 &&
            session.bootCount != -1 &&
            currentSnapshot.bootCount != session.bootCount
        val effectiveLastElapsed = if (session.lastElapsedRealtime > 0L) {
            session.lastElapsedRealtime
        } else if (session.startElapsedRealtime > 0L) {
            session.startElapsedRealtime
        } else {
            0L
        }
        val elapsedRolledBack = effectiveLastElapsed > 0L &&
            currentSnapshot.elapsedRealtimeMs < effectiveLastElapsed
        return bootCountChanged || elapsedRolledBack
    }

    /**
     * Computes the total monotonic time (in milliseconds) that has elapsed for [session],
     * including un-checkpointed progress up to [currentSnapshot].
     */
    fun computeAccumulatedMonotonicMs(
        session: StrictSessionEntity,
        currentSnapshot: ClockSnapshot,
        onClockTamper: ((wallDiff: Long, monotonicDiff: Long) -> Unit)? = null
    ): Long {
        if (!session.isActive) return session.accumulatedMonotonicMs

        val effectiveLastElapsed = if (session.lastElapsedRealtime > 0L) {
            session.lastElapsedRealtime
        } else if (session.startElapsedRealtime > 0L) {
            session.startElapsedRealtime
        } else {
            0L
        }

        val effectiveLastWall = if (session.lastWallTime > 0L) {
            session.lastWallTime
        } else if (session.startTime > 0L) {
            session.startTime
        } else {
            0L
        }

        // Uninitialized session baseline
        if (effectiveLastElapsed == 0L && effectiveLastWall == 0L) {
            return session.accumulatedMonotonicMs
        }

        return if (isReboot(session, currentSnapshot)) {
            // Post-reboot: powered-off time clamped to 0 ms.
            // Monotonic time since boot starts at currentSnapshot.elapsedRealtimeMs.
            val uptimeSinceBoot = currentSnapshot.elapsedRealtimeMs.coerceAtLeast(0L)
            session.accumulatedMonotonicMs + uptimeSinceBoot
        } else {
            // Same boot: monotonic delta between current elapsed and last checkpoint
            val monotonicDelta = if (effectiveLastElapsed > 0L) {
                (currentSnapshot.elapsedRealtimeMs - effectiveLastElapsed).coerceAtLeast(0L)
            } else {
                0L
            }
            val wallDelta = if (effectiveLastWall > 0L) {
                currentSnapshot.wallTimeMs - effectiveLastWall
            } else {
                0L
            }

            // Same-boot clock tamper check:
            // Flag if wall time leaped forward or backward beyond tolerance compared to monotonic time
            if (effectiveLastWall > 0L && effectiveLastElapsed > 0L) {
                val isForwardTamper = wallDelta > monotonicDelta + tamperToleranceMs
                val isBackwardTamper = wallDelta < -tamperToleranceMs
                if (isForwardTamper || isBackwardTamper) {
                    onClockTamper?.invoke(wallDelta, monotonicDelta)
                }
            }

            session.accumulatedMonotonicMs + monotonicDelta
        }
    }

    /**
     * Returns the remaining time in milliseconds for [session].
     * Returns [Long.MAX_VALUE] if the session is indefinite (`targetEndTime == 0L`).
     * Returns 0L if the session has fully elapsed.
     */
    fun getRemainingSessionMs(
        session: StrictSessionEntity,
        currentSnapshot: ClockSnapshot,
        onClockTamper: ((wallDiff: Long, monotonicDiff: Long) -> Unit)? = null
    ): Long {
        if (!session.isActive) return 0L
        if (session.targetEndTime == 0L) return Long.MAX_VALUE

        val targetDuration = (session.targetEndTime - session.startTime).coerceAtLeast(0L)
        val accumulated = computeAccumulatedMonotonicMs(session, currentSnapshot, onClockTamper)
        return (targetDuration - accumulated).coerceAtLeast(0L)
    }

    /**
     * Evaluates whether [session] is actively armed based on monotonic elapsed duration.
     */
    fun isSessionActive(
        session: StrictSessionEntity,
        currentSnapshot: ClockSnapshot,
        onClockTamper: ((wallDiff: Long, monotonicDiff: Long) -> Unit)? = null
    ): Boolean {
        if (!session.isActive) return false
        if (session.targetEndTime == 0L) return true
        return getRemainingSessionMs(session, currentSnapshot, onClockTamper) > 0L
    }

    /**
     * Computes the remaining time in milliseconds before a requested delayed unlock can be finalized.
     * Returns 0L if the delayed unlock duration has elapsed or if no delayed unlock is pending.
     */
    fun getRemainingDelayMs(
        session: StrictSessionEntity,
        currentSnapshot: ClockSnapshot,
        onClockTamper: ((wallDiff: Long, monotonicDiff: Long) -> Unit)? = null
    ): Long {
        if (!session.isActive || session.delayedUnlockRequestTime == null) return 0L

        val accumulated = computeAccumulatedMonotonicMs(session, currentSnapshot, onClockTamper)
        val startAccumulated = session.delayedUnlockStartAccumulatedMs

        return if (startAccumulated != null) {
            val elapsedDelay = (accumulated - startAccumulated).coerceAtLeast(0L)
            (session.delayedUnlockDurationMs - elapsedDelay).coerceAtLeast(0L)
        } else {
            // Fallback for legacy v5 entities
            val wallElapsed = (currentSnapshot.wallTimeMs - session.delayedUnlockRequestTime).coerceAtLeast(0L)
            (session.delayedUnlockDurationMs - wallElapsed).coerceAtLeast(0L)
        }
    }

    /**
     * Creates a new [StrictSessionEntity] with updated monotonic checkpoint values.
     */
    fun checkpoint(
        session: StrictSessionEntity,
        currentSnapshot: ClockSnapshot,
        onClockTamper: ((wallDiff: Long, monotonicDiff: Long) -> Unit)? = null
    ): StrictSessionEntity {
        val newAccumulated = computeAccumulatedMonotonicMs(session, currentSnapshot, onClockTamper)
        return session.copy(
            accumulatedMonotonicMs = newAccumulated,
            lastElapsedRealtime = currentSnapshot.elapsedRealtimeMs,
            lastWallTime = currentSnapshot.wallTimeMs,
            bootCount = currentSnapshot.bootCount
        )
    }
}
