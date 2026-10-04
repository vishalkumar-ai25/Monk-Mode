package com.stayfocused.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.data.local.entities.FailsafeEventType
import com.stayfocused.app.data.local.entities.FailsafeLogEntity
import com.stayfocused.app.scheduler.MidnightResetScheduler
import com.stayfocused.app.strict.GracePeriodManager
import com.stayfocused.app.strict.SystemClockSnapshotProvider
import com.stayfocused.app.strict.TrustedClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver invoked when the device completes booting or when the app package is updated.
 * Grants a 5-minute scoped boot grace period ONLY if no strict session was active at boot.
 * Package updates (MY_PACKAGE_REPLACED) do not grant grace period.
 * Re-arms scheduled alarms and reconciles active sessions across restarts.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "BootCompletedReceiver triggered with action: $action")

        val isBoot = action == Intent.ACTION_BOOT_COMPLETED
        val isPackageReplaced = action == Intent.ACTION_MY_PACKAGE_REPLACED

        if (!isBoot && !isPackageReplaced) return

        if (isPackageReplaced) {
            GracePeriodManager.clearGracePeriod()
            Log.i(TAG, "Package replaced ($action); boot grace period is not granted.")
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = StayFocusedDatabase.getInstance(context)

                // Reconcile active strict sessions across restart using TrustedClock
                val active = db.strictSessionDao().getActiveStrictSessionSync()
                val snapshot = SystemClockSnapshotProvider.getSnapshot(context)
                val trustedClock = TrustedClock()
                val isStrictActive = active != null && active.isActive && trustedClock.isSessionActive(active, snapshot)

                if (active != null && active.isActive) {
                    val updated = trustedClock.checkpoint(
                        session = active,
                        currentSnapshot = snapshot
                    )
                    db.strictSessionDao().checkpointMonotonicClock(
                        id = updated.id,
                        accumulatedMs = updated.accumulatedMonotonicMs,
                        lastElapsed = updated.lastElapsedRealtime,
                        lastWall = updated.lastWallTime,
                        bootCount = updated.bootCount
                    )
                }

                if (isBoot) {
                    if (isStrictActive) {
                        GracePeriodManager.activateGracePeriod(wasStrictActiveAtBoot = true)
                        Log.i(TAG, "Strict session active across reboot; boot grace period is DENIED.")
                    } else {
                        GracePeriodManager.activateGracePeriod(wasStrictActiveAtBoot = false)
                        Log.i(TAG, "No strict session active at boot; 5m boot grace period armed.")
                        db.failsafeLogDao().insertLog(
                            FailsafeLogEntity(
                                timestamp = System.currentTimeMillis(),
                                eventType = FailsafeEventType.BOOT_GRACE_WINDOW_USED.name,
                                details = "Device reboot detected with no active strict session; 5m grace period armed",
                                success = true
                            )
                        )
                    }
                }

                // Immediately reconcile active strict schedules and arm if inside scheduled window
                StrictScheduleReceiver().reconcileSchedules(context, db)
            } catch (e: Exception) {
                Log.w(TAG, "Failed during boot reconciliation in receiver", e)
            } finally {
                pendingResult.finish()
            }
        }

        // Re-arm exact midnight reset alarm
        try {
            MidnightResetScheduler(context).scheduleNextMidnightReset()
        } catch (e: Exception) {
            Log.w(TAG, "Could not reschedule midnight alarm on boot", e)
        }

        // Re-arm weekly reflection Sunday schedule
        try {
            com.stayfocused.app.worker.WeeklyReflectionScheduler.scheduleWeeklyReflection(context)
        } catch (e: Exception) {
            Log.w(TAG, "Could not reschedule weekly reflection on boot", e)
        }
    }
}
