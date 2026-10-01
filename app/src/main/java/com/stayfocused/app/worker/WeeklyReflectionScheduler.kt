package com.stayfocused.app.worker

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.stayfocused.app.domain.WeeklyReflectionEngine
import java.util.concurrent.TimeUnit

/**
 * Helper to schedule the periodic weekly reflection digest with initial delay aligned to
 * Sunday night at 21:00.
 */
object WeeklyReflectionScheduler {

    private const val TAG = "WeeklyReflectionSched"
    const val WORK_NAME_REFLECTION = "work_stayfocused_weekly_reflection"

    fun scheduleWeeklyReflection(
        context: Context,
        engine: WeeklyReflectionEngine = WeeklyReflectionEngine(),
        nowMs: Long = System.currentTimeMillis(),
        workManager: WorkManager? = null
    ) {
        val targetSundayTimestamp = engine.calculateNextSundayNightTimestamp(nowMs)
        val initialDelayMs = (targetSundayTimestamp - nowMs).coerceAtLeast(0L)

        val request = PeriodicWorkRequestBuilder<WeeklyReflectionWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .build()

        val wm = workManager ?: try {
            WorkManager.getInstance(context)
        } catch (e: Exception) {
            Log.w(TAG, "WorkManager could not be retrieved: ${e.message}")
            null
        }

        wm?.enqueueUniquePeriodicWork(
            WORK_NAME_REFLECTION,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        Log.i(TAG, "Weekly reflection worker scheduled with initial delay of ${initialDelayMs / 1000}s")
    }
}
