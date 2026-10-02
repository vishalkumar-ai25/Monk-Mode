package com.stayfocused.app.strict

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.domain.StrictScheduleEngine
import com.stayfocused.app.receiver.StrictScheduleReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Schedules exact transitions for recurring strict mode focus schedules using AlarmManager,
 * with defensive fallback to windowed alarms on Android 12+ (API 31+) if exact alarm capability is restricted.
 */
class StrictScheduleScheduler(
    private val context: Context,
    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager,
    private val engine: StrictScheduleEngine = StrictScheduleEngine(),
    private val exactAlarmPermissionChecker: (Context) -> Boolean = { ctx ->
        canScheduleExactAlarms(ctx)
    }
) {
    companion object {
        private const val TAG = "StrictScheduleScheduler"
        const val SCHEDULE_REQUEST_CODE = 2005

        fun canScheduleExactAlarms(context: Context): Boolean {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                am.canScheduleExactAlarms()
            } else {
                true
            }
        }

        fun scheduleNextBoundaryAsync(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                val scheduler = StrictScheduleScheduler(context)
                scheduler.scheduleNextBoundary()
            }
        }
    }

    suspend fun scheduleNextBoundary(
        nowEpochMs: Long = System.currentTimeMillis(),
        nowZdt: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault())
    ) {
        val db = StayFocusedDatabase.getInstance(context)
        val enabledSchedules = db.strictScheduleDao().getEnabledSchedulesSync()

        val nextBoundaryMs = engine.calculateNextBoundaryEpochMs(enabledSchedules, nowZdt, nowEpochMs)

        val intent = Intent(context, StrictScheduleReceiver::class.java).apply {
            action = StrictScheduleReceiver.ACTION_STRICT_SCHEDULE_ALARM
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(context, SCHEDULE_REQUEST_CODE, intent, flags)

        if (nextBoundaryMs == null || alarmManager == null) {
            Log.d(TAG, "No upcoming strict schedule boundaries. Cancelling alarm.")
            alarmManager?.cancel(pendingIntent)
            return
        }

        Log.i(TAG, "Scheduling next strict schedule boundary at $nextBoundaryMs (in ${nextBoundaryMs - nowEpochMs}ms)")

        if (exactAlarmPermissionChecker(context)) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextBoundaryMs,
                    pendingIntent
                )
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException on setExactAndAllowWhileIdle, falling back to setWindow", e)
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    nextBoundaryMs,
                    60_000L,
                    pendingIntent
                )
            }
        } else {
            Log.i(TAG, "Exact alarms restricted, using setWindow fallback")
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                nextBoundaryMs,
                60_000L,
                pendingIntent
            )
        }
    }
}
