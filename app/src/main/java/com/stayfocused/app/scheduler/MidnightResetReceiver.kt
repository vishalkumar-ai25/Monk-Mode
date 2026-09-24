package com.stayfocused.app.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.stayfocused.app.data.local.StayFocusedDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver triggered by AlarmManager at 00:00:00 or system on BOOT_COMPLETED.
 */
class MidnightResetReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MidnightResetReceiver"
        const val ACTION_MIDNIGHT_RESET = "com.stayfocused.app.action.MIDNIGHT_RESET"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "Received broadcast action: $action")

        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch {
            try {
                val db = StayFocusedDatabase.getInstance(context)
                val scheduler = MidnightResetScheduler(context)

                if (action == ACTION_MIDNIGHT_RESET) {
                    executeReset(db, scheduler)
                } else if (action == Intent.ACTION_BOOT_COMPLETED) {
                    Log.i(TAG, "Re-scheduling midnight alarm on boot")
                    scheduler.scheduleNextMidnightReset()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing broadcast action: $action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    suspend fun executeReset(db: StayFocusedDatabase, scheduler: MidnightResetScheduler) {
        val now = System.currentTimeMillis()
        Log.i(TAG, "Resetting daily app usage counters at $now")
        db.appLimitDao().resetDailyUsage(now)
        scheduler.scheduleNextMidnightReset()
    }
}
