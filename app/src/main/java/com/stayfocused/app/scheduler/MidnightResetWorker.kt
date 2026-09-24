package com.stayfocused.app.scheduler

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.stayfocused.app.data.local.StayFocusedDatabase

/**
 * WorkManager fallback worker executed when exact alarms cannot be scheduled.
 */
class MidnightResetWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "MidnightResetWorker"
    }

    override suspend fun doWork(): Result {
        return try {
            Log.i(TAG, "Executing midnight reset via WorkManager fallback")
            val db = StayFocusedDatabase.getInstance(applicationContext)
            db.appLimitDao().resetDailyUsage(System.currentTimeMillis())

            // Reschedule next midnight reset
            val scheduler = MidnightResetScheduler(applicationContext)
            scheduler.scheduleNextMidnightReset()

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed executing midnight reset in worker", e)
            Result.retry()
        }
    }
}
