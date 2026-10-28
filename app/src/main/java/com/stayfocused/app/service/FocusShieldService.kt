package com.stayfocused.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Persistent Foreground Service that keeps Monk Mode's background enforcement engine alive.
 *
 * Elevation to PROC_STATE_FOREGROUND_SERVICE prevents Android and aggressive OEM task managers
 * (such as ColorOS Athena / MIUI / OneUI) from terminating the process and dropping
 * [FocusAccessibilityService] when the user swipes MainActivity out of Recent Apps.
 */
class FocusShieldService : Service() {

    companion object {
        private const val TAG = "FocusShieldService"
        const val ACTION_START_SHIELD = "com.stayfocused.app.action.START_SHIELD"
        const val ACTION_STOP_SHIELD = "com.stayfocused.app.action.STOP_SHIELD"
        const val NOTIFICATION_CHANNEL_ID = "monk_mode_focus_shield"
        const val NOTIFICATION_ID = 3001

        @Volatile
        var isRunning: Boolean = false

        /**
         * Safely starts or re-arms the [FocusShieldService] in the foreground.
         */
        fun start(context: Context) {
            val intent = Intent(context, FocusShieldService::class.java).apply {
                action = ACTION_START_SHIELD
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not start FocusShieldService", e)
            }
        }

        /**
         * Gracefully stops the [FocusShieldService].
         */
        fun stop(context: Context) {
            val intent = Intent(context, FocusShieldService::class.java).apply {
                action = ACTION_STOP_SHIELD
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Could not stop FocusShieldService", e)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var isStrictModeActive: Boolean = false
    @Volatile private var activeProfileName: String = "Deep Work"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        observeDatabaseState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_SHIELD
        if (action == ACTION_STOP_SHIELD) {
            Log.i(TAG, "Stopping FocusShieldService via ACTION_STOP_SHIELD")
            stopShield()
            return START_NOT_STICKY
        }

        startForegroundNotification()
        isRunning = true
        Log.i(TAG, "FocusShieldService active and guarding Monk Mode process.")
        return START_STICKY
    }

    /**
     * Critical OEM survival hook: invoked when the user swipes MainActivity out of Recents.
     * Keeps the foreground service running and refreshes the sticky notification so
     * the system never drops [FocusAccessibilityService] or drops from enabled services.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.i(TAG, "Activity task removed from Recents. Maintaining FocusShieldService foreground state.")
        isRunning = true
        startForegroundNotification()
    }

    override fun onDestroy() {
        isRunning = false
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun stopShield() {
        isRunning = false
        serviceScope.coroutineContext[Job]?.cancelChildren()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun startForegroundNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isStrictModeActive) "Strict Mode: ARMED" else "Monk Mode Shield Active"
        val contentText = if (isStrictModeActive) {
            "Anti-tamper and focus defenses are locked and active."
        } else {
            "Active Profile: $activeProfileName • Distraction shields armed."
        }

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(title)
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        androidx.core.app.ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Monk Mode Focus Shield",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows continuous focus protection status while Monk Mode is actively guarding your device."
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun observeDatabaseState() {
        serviceScope.launch {
            try {
                val db = StayFocusedDatabase.getInstance(applicationContext)

                // 1. Observe active strict session to update notification dynamically
                launch {
                    db.strictSessionDao().getActiveStrictSession()
                        .catch { e -> Log.w(TAG, "Error observing strict session", e) }
                        .collectLatest { session ->
                            val active = session != null && session.isActive
                            if (active != isStrictModeActive) {
                                isStrictModeActive = active
                                if (isRunning) {
                                    startForegroundNotification()
                                }
                            }
                        }
                }

                // 2. Observe active focus profile
                launch {
                    db.focusProfileDao().getAllProfiles()
                        .catch { e -> Log.w(TAG, "Error observing focus profiles", e) }
                        .collectLatest { profiles ->
                            val current = profiles.firstOrNull { it.isActive }
                            val name = current?.name ?: "Deep Work"
                            if (name != activeProfileName) {
                                activeProfileName = name
                                if (isRunning) {
                                    startForegroundNotification()
                                }
                            }
                        }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Database not available for shield observation", e)
            }
        }
    }
}
