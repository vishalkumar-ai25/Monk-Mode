package com.stayfocused.app.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.data.local.entities.SuppressedNotificationEntity
import com.stayfocused.app.notification.NotificationDecisionEngine
import com.stayfocused.app.notification.NotificationInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Android NotificationListenerService adapter that silences notifications
 * from apps that are currently blocked, while preserving critical ongoing,
 * call, alarm, and system-level alerts.
 */
open class FocusNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "FocusNotification"
    }

    var decisionEngine: NotificationDecisionEngine = NotificationDecisionEngine()
    var database: StayFocusedDatabase? = null
    var autoObserveDatabase: Boolean = true

    // In-memory zero-latency cache (< 1ms evaluation budget)
    @Volatile var cachedBlockedPackages: Set<String> = emptySet()
    @Volatile var cachedBreakEndTimeMs: Long = 0L

    var isBreakActive: Boolean
        get() = System.currentTimeMillis() < cachedBreakEndTimeMs
        set(value) {
            cachedBreakEndTimeMs = if (value) Long.MAX_VALUE else 0L
        }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        if (database == null) {
            database = StayFocusedDatabase.getInstance(this)
        }
        if (autoObserveDatabase) {
            observeAppLimits()
            observeBreakSessions()
        }
    }

    private fun observeBreakSessions() {
        val db = database ?: return
        serviceScope.launch {
            db.breakSessionDao().getActiveBreak()
                .catch { e -> Log.e(TAG, "Error observing break sessions for notifications", e) }
                .collectLatest { session ->
                    cachedBreakEndTimeMs = if (session != null && session.isActive) session.endTime else 0L
                }
        }
    }

    private fun observeAppLimits() {
        val db = database ?: return
        serviceScope.launch {
            db.appLimitDao().getAllAppLimits()
                .catch { e -> Log.e(TAG, "Error observing app limits for notifications", e) }
                .collectLatest { limits ->
                    cachedBlockedPackages = limits.filter { limit ->
                        limit.isBlocked ||
                            (limit.dailyTimeLimitMinutes > 0 && limit.currentDayUsageMs >= limit.dailyTimeLimitMinutes * 60_000L) ||
                            (limit.dailyLaunchLimit > 0 && limit.currentDayLaunches >= limit.dailyLaunchLimit)
                    }.map { it.packageName }.toSet()
                }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val notification = sbn.notification ?: return
        val packageName = sbn.packageName ?: return

        val info = NotificationInfo(
            packageName = packageName,
            isOngoing = sbn.isOngoing || (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0,
            category = notification.category,
            channelId = notification.channelId
        )

        val isBlocked = packageName in cachedBlockedPackages
        val result = decisionEngine.shouldSuppressNotification(
            notification = info,
            isPackageBlocked = isBlocked,
            isBreakActive = isBreakActive
        )

        if (result.shouldCancel) {
            Log.d(TAG, "Canceling distraction notification from $packageName [Key: ${sbn.key}]")
            cancelNotificationByKey(sbn.key)

            // Asynchronously record into the suppressed notification vault on Dispatchers.IO
            val extras = notification.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            val postTime = sbn.postTime

            serviceScope.launch(Dispatchers.IO) {
                try {
                    val resolvedAppName = getAppName(packageName)
                    val entity = SuppressedNotificationEntity(
                        packageName = packageName,
                        appName = resolvedAppName,
                        title = title,
                        contentSnippet = text,
                        postTimestamp = postTime
                    )
                    database?.suppressedNotificationDao()?.insert(entity)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to persist suppressed notification", e)
                }
            }
        }
    }

    open fun cancelNotificationByKey(key: String) {
        cancelNotification(key)
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
