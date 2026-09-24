package com.stayfocused.app.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Manages daily midnight (00:00:00) counter reset alarms using AlarmManager's exact alarm API
 * with defensive fallback to WorkManager.
 */
class MidnightResetScheduler(
    private val context: Context,
    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager,
    private val exactAlarmPermissionChecker: (Context) -> Boolean = { ctx ->
        canScheduleExactAlarms(ctx)
    }
) {
    companion object {
        private const val TAG = "MidnightResetScheduler"
        const val RESET_REQUEST_CODE = 1001
        const val WORK_NAME_MIDNIGHT_RESET = "work_midnight_reset"

        fun canScheduleExactAlarms(context: Context): Boolean {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.canScheduleExactAlarms()
            } else {
                true
            }
        }
    }

    fun canScheduleExactAlarms(): Boolean = exactAlarmPermissionChecker(context)

    fun getNextMidnightTimestamp(
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zoneId)
        return zdt.toLocalDate().plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    fun scheduleNextMidnightReset() {
        val nextMidnightMs = getNextMidnightTimestamp()

        if (canScheduleExactAlarms() && alarmManager != null) {
            try {
                val intent = Intent(context, MidnightResetReceiver::class.java).apply {
                    action = MidnightResetReceiver.ACTION_MIDNIGHT_RESET
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    RESET_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextMidnightMs,
                    pendingIntent
                )
                Log.i(TAG, "Exact midnight reset alarm scheduled for $nextMidnightMs")
                return
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException while scheduling exact alarm. Falling back to WorkManager.", e)
            }
        }

        // Fallback: WorkManager
        scheduleWorkManagerFallback(nextMidnightMs)
    }

    fun cancelMidnightReset() {
        if (alarmManager != null) {
            val intent = Intent(context, MidnightResetReceiver::class.java).apply {
                action = MidnightResetReceiver.ACTION_MIDNIGHT_RESET
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                RESET_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
            }
        }
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_MIDNIGHT_RESET)
    }

    private fun scheduleWorkManagerFallback(targetTimestampMs: Long) {
        val delayMs = (targetTimestampMs - System.currentTimeMillis()).coerceAtLeast(0L)
        val resetWork = OneTimeWorkRequestBuilder<MidnightResetWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME_MIDNIGHT_RESET,
            ExistingWorkPolicy.REPLACE,
            resetWork
        )
        Log.i(TAG, "WorkManager fallback reset scheduled with delay $delayMs ms")
    }
}
