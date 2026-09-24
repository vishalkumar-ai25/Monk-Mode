package com.stayfocused.app.worker

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.service.FocusAccessibilityService
import com.stayfocused.app.tracker.UsageStatsTracker
import java.util.concurrent.TimeUnit

/**
 * 15-minute periodic watchdog worker that:
 * 1. Reconciles daily foreground usage stats in Room DB.
 * 2. Performs failsafe midnight resets if the device was off/asleep at 00:00:00.
 * 3. Monitors whether FocusAccessibilityService is alive, alerting user if killed by OEM skins.
 */
class WatchdogWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "WatchdogWorker"
        const val WORK_NAME_WATCHDOG = "work_stayfocused_watchdog"
        const val NOTIFICATION_CHANNEL_ID = "stayfocused_watchdog_channel"
        const val NOTIFICATION_ID = 2001

        fun enqueuePeriodicWatchdog(context: Context) {
            val request = PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_WATCHDOG,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            Log.i(TAG, "Periodic 15-minute WatchdogWorker enqueued")
        }

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            val expectedId = "${context.packageName}/${FocusAccessibilityService::class.java.name}"
            val shortExpectedId = "${context.packageName}/.service.FocusAccessibilityService"

            val inManagerList = enabledServices.any {
                it.id.equals(expectedId, ignoreCase = true) || it.id.equals(shortExpectedId, ignoreCase = true)
            }
            if (inManagerList) return true

            // Fallback: check Settings.Secure
            return try {
                val settingValue = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: ""
                settingValue.contains(context.packageName, ignoreCase = true)
            } catch (e: Exception) {
                false
            }
        }

        fun postServiceDisabledNotification(context: Context) {
            createNotificationChannel(context)

            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Stay Focused Protection Disabled")
                .setContentText("The accessibility service was stopped by the system. Tap to re-enable protection.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            try {
                NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot post notification (POST_NOTIFICATIONS not granted)", e)
            }
        }

        private fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val name = "Stay Focused Protection Alerts"
                val descriptionText = "Alerts when background focus enforcement services are killed by OEM"
                val importance = NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val db = StayFocusedDatabase.getInstance(applicationContext)
            val tracker = UsageStatsTracker(applicationContext)
            val isAlive = isAccessibilityServiceEnabled(applicationContext)

            executeWatchdog(
                database = db,
                usageTracker = tracker,
                isServiceAlive = isAlive,
                onServiceDead = { postServiceDisabledNotification(applicationContext) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Watchdog execution failed", e)
            Result.retry()
        }
    }

    suspend fun executeWatchdog(
        database: StayFocusedDatabase,
        usageTracker: UsageStatsTracker,
        isServiceAlive: Boolean,
        onServiceDead: () -> Unit
    ): Result {
        val appLimitDao = database.appLimitDao()
        val startOfToday = usageTracker.getStartOfToday()
        val allLimits = appLimitDao.getAllAppLimitsSync()

        // 1. Failsafe midnight reset check if exact alarm was missed due to device power-off
        if (allLimits.any { it.lastResetTimestamp < startOfToday }) {
            Log.i(TAG, "Detected missed midnight reset from previous day; executing failsafe reset")
            appLimitDao.resetDailyUsage(System.currentTimeMillis())
        }

        // 2. Reconcile daily usage stats from midnight to now
        usageTracker.syncUsageWithDatabase(appLimitDao)

        // 3. Monitor Accessibility Service status
        if (!isServiceAlive) {
            Log.w(TAG, "FocusAccessibilityService is disabled or dead. Prompting notification.")
            onServiceDead()
        }

        // 4. Purge suppressed notifications older than 7 days to preserve storage and privacy
        val retentionThreshold = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        database.suppressedNotificationDao().clearOlderThan(retentionThreshold)

        return Result.success()
    }
}
