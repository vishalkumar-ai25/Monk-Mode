package com.stayfocused.app.strict

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/**
 * Android system provider for [ClockSnapshot].
 * Caches `Settings.Global.BOOT_COUNT` once per process lifetime to eliminate
 * Binder IPC overhead on the hot accessibility/interception evaluation path.
 */
object SystemClockSnapshotProvider {

    @Volatile
    private var cachedBootCount: Int = -1

    fun getSnapshot(context: Context?): ClockSnapshot {
        if (cachedBootCount == -1 && context != null) {
            cachedBootCount = try {
                Settings.Global.getInt(
                    context.contentResolver,
                    Settings.Global.BOOT_COUNT,
                    -1
                )
            } catch (_: Exception) {
                -1
            }
        }

        return ClockSnapshot(
            elapsedRealtimeMs = SystemClock.elapsedRealtime(),
            wallTimeMs = System.currentTimeMillis(),
            bootCount = cachedBootCount
        )
    }

    /**
     * For unit tests only: resets the process-level cached boot count.
     */
    fun resetCachedBootCountForTesting() {
        cachedBootCount = -1
    }
}
