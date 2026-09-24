package com.stayfocused.app.strict

import android.os.SystemClock

/**
 * Manages the Scoped Boot Grace Period (3–5 minutes post-reboot).
 * During this window, Settings-blocking and Device Admin lockout are suspended ONLY,
 * ensuring users can recover if an issue occurs upon startup.
 * App and website blocks remain strictly armed at all times.
 */
class GracePeriodManager(
    val gracePeriodDurationMs: Long = DEFAULT_GRACE_PERIOD_MS,
    private val elapsedRealtimeProvider: () -> Long = { SystemClock.elapsedRealtime() }
) {

    companion object {
        const val DEFAULT_GRACE_PERIOD_MS: Long = 5 * 60 * 1000L // 5 minutes

        @Volatile
        private var bootActivationTimeElapsedMs: Long? = null

        /**
         * Called by BootCompletedReceiver upon system restart to record activation.
         */
        fun activateGracePeriod(elapsedMs: Long = SystemClock.elapsedRealtime()) {
            bootActivationTimeElapsedMs = elapsedMs
        }

        /**
         * Checks whether the default system boot grace period is currently active.
         */
        fun isDefaultGracePeriodActive(
            elapsedMs: Long = SystemClock.elapsedRealtime(),
            durationMs: Long = DEFAULT_GRACE_PERIOD_MS
        ): Boolean {
            val activation = bootActivationTimeElapsedMs
            return if (activation != null) {
                (elapsedMs - activation) in 0 until durationMs
            } else {
                // Fallback: elapsedRealtime() directly measures time since boot
                elapsedMs in 0 until durationMs
            }
        }
    }

    /**
     * Checks if the grace period is currently active for this instance.
     */
    fun isGracePeriodActive(elapsedRealtimeMs: Long = elapsedRealtimeProvider()): Boolean {
        return elapsedRealtimeMs in 0 until gracePeriodDurationMs
    }

    /**
     * Returns the remaining milliseconds in the grace period, or 0 if expired.
     */
    fun getRemainingGracePeriodMs(elapsedRealtimeMs: Long = elapsedRealtimeProvider()): Long {
        return (gracePeriodDurationMs - elapsedRealtimeMs).coerceAtLeast(0L)
    }
}
