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
                val bootCount = com.stayfocused.app.strict.SystemClockSnapshotProvider.getSnapshot(context).bootCount
                val newSession = StrictSessionEntity(
                    profileId = primarySchedule.profileId,
                    startTime = nowEpochMs,
                    targetEndTime = latestEndTime,
                    startElapsedRealtime = nowElapsedRealtime,
                    deactivationChallenge = challenge,
                    isActive = true,
                    accumulatedMonotonicMs = 0L,
                    lastElapsedRealtime = nowElapsedRealtime,
                    lastWallTime = nowEpochMs,
                    bootCount = bootCount,
                    isScheduled = true
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
                val bootCount = com.stayfocused.app.strict.SystemClockSnapshotProvider.getSnapshot(context).bootCount
                val snapshot = com.stayfocused.app.strict.ClockSnapshot(
                    elapsedRealtimeMs = nowElapsedRealtime,
                    wallTimeMs = nowEpochMs,
                    bootCount = bootCount
                )
                val trustedClock = com.stayfocused.app.strict.TrustedClock()

                // Checkpoint monotonic progress and detect same-boot clock tamper
                var clockTampered = false
                var tamperWallDiff = 0L
                var tamperMonoDiff = 0L
                val checkpointed = trustedClock.checkpoint(
                    session = activeSession,
                    currentSnapshot = snapshot,
                    onClockTamper = { wallDiff, monoDiff ->
                        clockTampered = true
                        tamperWallDiff = wallDiff
                        tamperMonoDiff = monoDiff
                    }
                )
                sessionDao.checkpointMonotonicClock(
                    id = checkpointed.id,
                    accumulatedMs = checkpointed.accumulatedMonotonicMs,
                    lastElapsed = checkpointed.lastElapsedRealtime,
                    lastWall = checkpointed.lastWallTime,
                    bootCount = checkpointed.bootCount
                )

                if (clockTampered) {
                    failsafeLogDao.insertLog(
                        FailsafeLogEntity(
                            timestamp = nowEpochMs,
                            eventType = "CLOCK_TAMPER_DETECTED",
                            details = "Clock tamper detected: wall delta ($tamperWallDiff ms) disagreed with monotonic delta ($tamperMonoDiff ms).",
                            success = false
                        )
                    )
                    Log.w(TAG, "Clock tamper detected (wall delta $tamperWallDiff vs mono $tamperMonoDiff ms)")
                    Log.w(TAG, "Clock roll-forward detected; deactivation suppressed.")
                    return
                }

                // Scheduled calendar window deactivates when target time reached (and not tampered).
                // Manual duration sessions ONLY deactivate when monotonic elapsed duration has fully elapsed.
                val isDeactivationAllowed = if (activeSession.isScheduled) {
                    nowEpochMs >= activeSession.targetEndTime
                } else {
                    !trustedClock.isSessionActive(activeSession, snapshot)
                }

                if (isDeactivationAllowed && activeSession.delayedUnlockRequestTime == null) {
                    sessionDao.deactivateAllSessions()
                    failsafeLogDao.insertLog(
                        FailsafeLogEntity(
                            timestamp = nowEpochMs,
                            eventType = if (activeSession.isScheduled) "SCHEDULE_STRICT_DEACTIVATED" else "STRICT_SESSION_EXPIRED",
                            details = if (activeSession.isScheduled) {
                                "Strict session concluded gracefully as scheduled window elapsed"
                            } else {
                                "Strict session concluded as monotonic duration fully elapsed"
                            },
                            success = true
                        )
                    )
                    Log.i(TAG, "Deactivated strict session (isScheduled=${activeSession.isScheduled})")
                }
            }
        }

        // Reschedule next boundary alarm
        val scheduler = StrictScheduleScheduler(context, engine = engine)
        scheduler.scheduleNextBoundary(nowEpochMs, nowZdt)
    }
}
