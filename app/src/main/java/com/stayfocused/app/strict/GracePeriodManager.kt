package com.stayfocused.app.strict

import android.os.SystemClock

/**
 * Manages the Scoped Boot Grace Period (5 minutes post-reboot).
 * During this window, Settings-blocking and Device Admin lockout are suspended ONLY,
 * ensuring users can recover if an issue occurs upon startup.
 * Grace period applies ONLY if no strict session was active at boot.
 * App and website blocks remain strictly armed at all times.
 */
class GracePeriodManager(
    val gracePeriodDurationMs: Long = DEFAULT_GRACE_PERIOD_MS,
    val wasStrictActiveAtBoot: Boolean = false,
    private val elapsedRealtimeProvider: () -> Long = { currentElapsedRealtime() }
) {

    companion object {
        const val DEFAULT_GRACE_PERIOD_MS: Long = 5 * 60 * 1000L // 5 minutes

        @Volatile
        private var isGraceGranted: Boolean = false

        @Volatile
        private var bootActivationTimeElapsedMs: Long? = null

        @Volatile
        var wasStrictActiveAtBoot: Boolean = false

        /** Injectable provider for monotonic clock in unit tests or production. */
        var currentElapsedRealtimeProvider: () -> Long = {
            try {
                SystemClock.elapsedRealtime()
            } catch (_: Throwable) {
                System.currentTimeMillis()
            }
        }

        fun currentElapsedRealtime(): Long = currentElapsedRealtimeProvider()

        /**
         * Called by BootCompletedReceiver upon system restart to record activation.
         * Grace period is granted ONLY if wasStrictActiveAtBoot is false.
         */
        fun activateGracePeriod(
            wasStrictActiveAtBoot: Boolean = false,
            elapsedMs: Long = currentElapsedRealtime()
        ) {
            Companion.wasStrictActiveAtBoot = wasStrictActiveAtBoot
            if (wasStrictActiveAtBoot) {
                clearGracePeriod()
                return
            }
            isGraceGranted = true
            bootActivationTimeElapsedMs = elapsedMs
        }

        /**
         * Clears any active grace period.
         */
        fun clearGracePeriod() {
            isGraceGranted = false
            bootActivationTimeElapsedMs = null
        }

        /**
         * Checks whether the default system boot grace period is currently active.
         * Grace is active ONLY if it was explicitly granted (no strict session active at boot)
         * and the 5-minute duration has not elapsed.
         */
        fun isDefaultGracePeriodActive(
            elapsedMs: Long = currentElapsedRealtime(),
            durationMs: Long = DEFAULT_GRACE_PERIOD_MS
        ): Boolean {
            if (!isGraceGranted) return false
            val activation = bootActivationTimeElapsedMs ?: return false
            return (elapsedMs - activation) in 0 until durationMs
        }
    }

    /**
     * Checks if the grace period is currently active for this instance.
     */
    fun isGracePeriodActive(
        elapsedRealtimeMs: Long = elapsedRealtimeProvider(),
        wasStrictActiveAtBoot: Boolean = this.wasStrictActiveAtBoot
    ): Boolean {
        if (wasStrictActiveAtBoot) return false
        return elapsedRealtimeMs in 0 until gracePeriodDurationMs
    }

    /**
     * Returns the remaining milliseconds in the grace period, or 0 if expired or not granted.
     */
    fun getRemainingGracePeriodMs(
        elapsedRealtimeMs: Long = elapsedRealtimeProvider(),
        wasStrictActiveAtBoot: Boolean = this.wasStrictActiveAtBoot
    ): Long {
        if (wasStrictActiveAtBoot) return 0L
        return (gracePeriodDurationMs - elapsedRealtimeMs).coerceAtLeast(0L)
    }
}
