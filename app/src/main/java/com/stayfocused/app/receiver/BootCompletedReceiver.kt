package com.stayfocused.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.stayfocused.app.scheduler.MidnightResetScheduler
import com.stayfocused.app.strict.GracePeriodManager

/**
 * BroadcastReceiver invoked when the device completes booting or when the app package is updated.
 * Activates the 5-minute scoped boot grace period and re-arms scheduled midnight reset alarms.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.i(TAG, "BootCompletedReceiver triggered with action: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // Activate scoped boot grace period (5 minutes)
                GracePeriodManager.activateGracePeriod()
                Log.i(TAG, "Scoped boot grace period activated for 5 minutes.")

                // Re-arm exact midnight reset alarm
                try {
                    MidnightResetScheduler(context).scheduleNextMidnightReset()
                } catch (e: Exception) {
                    Log.w(TAG, "Could not reschedule midnight alarm on boot", e)
                }
            }
        }
    }
}
