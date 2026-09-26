package com.stayfocused.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.domain.WeeklyReflectionEngine
import com.stayfocused.app.domain.model.WeeklyReflectionDigest
import com.stayfocused.app.tracker.UsageStatsTracker
import com.stayfocused.app.ui.MainActivity

/**
 * Worker that runs once a week (Sunday night at 21:00) to aggregate weekly focus metrics
 * (total blocked distractions, biggest drop in app usage vs last week, longest strict mode streak)
 * and dispatches a local notification digest.
 */
class WeeklyReflectionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "WeeklyReflectionWorker"
        const val WORK_NAME_REFLECTION = "work_stayfocused_weekly_reflection"
        const val NOTIFICATION_CHANNEL_ID = "stayfocused_reflection_channel"
        const val NOTIFICATION_ID = 2003

        fun postDigestNotification(context: Context, digest: WeeklyReflectionDigest) {
            createNotificationChannel(context)

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Weekly Focus Reflection")
                .setContentText(digest.formattedSummary)
                .setStyle(NotificationCompat.BigTextStyle().bigText(digest.formattedSummary))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
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
                val name = "Weekly Reflection Digest"
                val descriptionText = "Sunday night summary of weekly focus achievements and distractions blocked"
                val importance = NotificationManager.IMPORTANCE_DEFAULT
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
            val engine = WeeklyReflectionEngine()

            executeDigest(
                database = db,
                usageTracker = tracker,
                engine = engine,
                nowMs = System.currentTimeMillis(),
                onDigestGenerated = { digest ->
                    postDigestNotification(applicationContext, digest)
                }
            )
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Weekly reflection execution failed", e)
            Result.retry()
        }
    }

    suspend fun executeDigest(
        database: StayFocusedDatabase,
        usageTracker: UsageStatsTracker,
        engine: WeeklyReflectionEngine = WeeklyReflectionEngine(),
        nowMs: Long = System.currentTimeMillis(),
        onDigestGenerated: ((WeeklyReflectionDigest) -> Unit)? = null
    ): WeeklyReflectionDigest {
        val startOfWeek = nowMs - (7 * 24 * 60 * 60 * 1000L)
        val startOfLastWeek = nowMs - (14 * 24 * 60 * 60 * 1000L)

        val suppressedCount = database.suppressedNotificationDao().getSuppressedCountSince(startOfWeek)
        val blockedLaunches = database.appLimitDao().getBlockedAppLaunchesCountSince(startOfWeek)
        val totalBlockedAttempts = suppressedCount + blockedLaunches

        val strictSessions = database.strictSessionDao().getSessionsSince(startOfWeek)

        val usageThisWeek = usageTracker.queryForegroundUsage(startOfWeek, nowMs)
        val usageLastWeek = usageTracker.queryForegroundUsage(startOfLastWeek, startOfWeek)

        val limits = database.appLimitDao().getAllAppLimitsSync()
        val packageToAppName = limits.associate { it.packageName to it.appName }.toMutableMap()

        for (pkg in usageLastWeek.keys) {
            if (!packageToAppName.containsKey(pkg)) {
                try {
                    val appInfo = applicationContext.packageManager.getApplicationInfo(pkg, 0)
                    val label = applicationContext.packageManager.getApplicationLabel(appInfo).toString()
                    packageToAppName[pkg] = label
                } catch (_: Exception) {
                    // Fallback to pkg name if unresolvable
                }
            }
        }

        val digest = engine.calculateWeeklyDigest(
            totalBlockedAttempts = totalBlockedAttempts,
            usageThisWeek = usageThisWeek,
            usageLastWeek = usageLastWeek,
            packageToAppName = packageToAppName,
            strictSessions = strictSessions
        )

        onDigestGenerated?.invoke(digest)
        return digest
    }
}
