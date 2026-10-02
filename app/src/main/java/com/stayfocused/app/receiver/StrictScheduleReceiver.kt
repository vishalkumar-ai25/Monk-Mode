package com.stayfocused.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.data.local.entities.FailsafeLogEntity
import com.stayfocused.app.data.local.entities.StrictSessionEntity
import com.stayfocused.app.domain.StrictScheduleEngine
import com.stayfocused.app.strict.StrictScheduleScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Handles strict schedule alarms, system time changes, timezone changes, and boot completion
 * to ensure recurring focus sessions transition reliably without clock spoofing bypasses.
 */
class StrictScheduleReceiver(
    private val engine: StrictScheduleEngine = StrictScheduleEngine()
) : BroadcastReceiver() {

    companion object {
        private const val TAG = "StrictScheduleReceiver"
        const val ACTION_STRICT_SCHEDULE_ALARM = "com.stayfocused.app.action.STRICT_SCHEDULE_ALARM"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "Received broadcast intent with action: $action")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = StayFocusedDatabase.getInstance(context)
                reconcileSchedules(context, db)
            } catch (e: Exception) {
                Log.e(TAG, "Failed reconciling strict schedules on broadcast", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    suspend fun reconcileSchedules(
        context: Context,
        database: StayFocusedDatabase,
        nowEpochMs: Long = System.currentTimeMillis(),
        nowZdt: ZonedDateTime = ZonedDateTime.now(ZoneId.systemDefault()),
        nowElapsedRealtime: Long = SystemClock.elapsedRealtime()
    ) {
        val scheduleDao = database.strictScheduleDao()
        val sessionDao = database.strictSessionDao()
        val profileDao = database.focusProfileDao()
        val failsafeLogDao = database.failsafeLogDao()

        val enabledSchedules = scheduleDao.getEnabledSchedulesSync()
        val activeSchedules = engine.getActiveSchedules(enabledSchedules, nowZdt, nowEpochMs)
        val activeSession = sessionDao.getActiveStrictSessionSync()

        if (activeSchedules.isNotEmpty()) {
            val primarySchedule = activeSchedules.first()
            val latestEndTime = activeSchedules.maxOf { engine.calculateCurrentWindowEndTime(it, nowZdt) }
            val challenge = engine.getStrictestChallenge(activeSchedules.map { it.deactivationChallenge })

            if (activeSession == null || !activeSession.isActive) {
                val newSession = StrictSessionEntity(
                    profileId = primarySchedule.profileId,
                    startTime = nowEpochMs,
                    targetEndTime = latestEndTime,
                    startElapsedRealtime = nowElapsedRealtime,
                    deactivationChallenge = challenge,
                    isActive = true
                )
                sessionDao.insertSession(newSession)
                profileDao.switchToProfile(primarySchedule.profileId)

                failsafeLogDao.insertLog(
                    FailsafeLogEntity(
                        timestamp = nowEpochMs,
                        eventType = "SCHEDULE_STRICT_ACTIVATED",
                        details = "Strict mode armed automatically by schedule '${primarySchedule.name}' until $latestEndTime",
                        success = true
                    )
                )
                Log.i(TAG, "Armed strict session from schedule '${primarySchedule.name}'")
            } else {
                val newEndTime = maxOf(activeSession.targetEndTime, latestEndTime)
                val strictestChallenge = engine.getStrictestChallenge(listOf(activeSession.deactivationChallenge, challenge))
                if (newEndTime != activeSession.targetEndTime || strictestChallenge != activeSession.deactivationChallenge) {
                    val updated = activeSession.copy(
                        targetEndTime = newEndTime,
                        deactivationChallenge = strictestChallenge
                    )
                    sessionDao.updateSession(updated)
                    Log.i(TAG, "Updated active strict session: targetEndTime=$newEndTime, challenge=$strictestChallenge")
                }
            }
        } else {
            if (activeSession != null && activeSession.isActive) {
                // If scheduled session reached its end time and no delayed unlock is pending
                if (nowEpochMs >= activeSession.targetEndTime && activeSession.delayedUnlockRequestTime == null) {
                    // Anti-tamper check: if continuously running on the same boot (startElapsedRealtime > 0L),
                    // ensure hardware elapsed monotonic time matches or exceeds the expected wall-clock duration.
                    // Note: If device rebooted, BootCompletedReceiver resets startElapsedRealtime to -1L.
                    val isContinuouslyRunning = activeSession.startElapsedRealtime > 0L && nowElapsedRealtime >= activeSession.startElapsedRealtime
                    val expectedDurationMs = activeSession.targetEndTime - activeSession.startTime
                    val actualElapsedMs = nowElapsedRealtime - activeSession.startElapsedRealtime

                    if (isContinuouslyRunning && actualElapsedMs < expectedDurationMs) {
                        failsafeLogDao.insertLog(
                            FailsafeLogEntity(
                                timestamp = nowEpochMs,
                                eventType = "CLOCK_TAMPER_DETECTED",
                                details = "Wall clock exceeded targetEndTime but hardware elapsed ($actualElapsedMs ms) < expected ($expectedDurationMs ms). Suppression maintained.",
                                success = false
                            )
                        )
                        Log.w(TAG, "Clock roll-forward detected (hardware elapsed $actualElapsedMs < expected $expectedDurationMs); ignoring deactivation request.")
                        return
                    }

                    sessionDao.deactivateAllSessions()
                    failsafeLogDao.insertLog(
                        FailsafeLogEntity(
                            timestamp = nowEpochMs,
                            eventType = "SCHEDULE_STRICT_DEACTIVATED",
                            details = "Strict session concluded gracefully as scheduled window elapsed",
                            success = true
                        )
                    )
                    Log.i(TAG, "Deactivated strict session as scheduled window elapsed")
                }
            }
        }

        // Reschedule next boundary alarm
        val scheduler = StrictScheduleScheduler(context, engine = engine)
        scheduler.scheduleNextBoundary(nowEpochMs, nowZdt)
    }
}
