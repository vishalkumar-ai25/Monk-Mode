package com.stayfocused.app.service

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.stayfocused.app.BuildConfig
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.data.registry.PackageRegistry
import com.stayfocused.app.domain.InterceptionDecisionEngine
import com.stayfocused.app.domain.model.AppLimitSnapshot
import com.stayfocused.app.domain.model.BlockReason
import com.stayfocused.app.domain.model.FocusProfileRule
import com.stayfocused.app.domain.model.InterceptionContext
import com.stayfocused.app.domain.model.InterceptionResult
import com.stayfocused.app.tracker.UsageStatsTracker
import com.stayfocused.app.ui.overlay.BlockOverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * High-performance, thin OS Accessibility Service adapter for foreground app interception.
 * Runs on-demand event evaluation against an in-memory cached state budget (< 10ms).
 */
class FocusAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "FocusAccessibility"
        private const val BOOT_GRACE_PERIOD_MS = 5 * 60 * 1000L // 5 minutes
    }

    var engine: InterceptionDecisionEngine = InterceptionDecisionEngine()
    var overlayManager: BlockOverlayManager? = null
    var packageRegistry: PackageRegistry? = null
    var usageStatsTracker: UsageStatsTracker? = null
    var database: StayFocusedDatabase? = null

    private var lastForegroundPackage: String? = null

    // Boot grace period un-spoofable hardware check
    var isBootGracePeriodProvider: () -> Boolean = {
        com.stayfocused.app.strict.GracePeriodManager.isDefaultGracePeriodActive()
    }

    // In-memory hot cache for zero-disk-latency interception (< 10ms budget)
    @Volatile var cachedActiveProfiles: List<FocusProfileRule> = emptyList()
    @Volatile var cachedAppLimits: Map<String, AppLimitSnapshot> = emptyMap()
    @Volatile var isStrictModeActive: Boolean = false
    @Volatile var cachedBreakEndTimeMs: Long = 0L

    var isBreakActive: Boolean
        get() = System.currentTimeMillis() < cachedBreakEndTimeMs && !isStrictModeActive
        set(value) {
            cachedBreakEndTimeMs = if (value) Long.MAX_VALUE else 0L
        }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        if (overlayManager == null) {
            overlayManager = BlockOverlayManager(this)
        }
        if (packageRegistry == null) {
            packageRegistry = PackageRegistry.fromAsset(applicationContext)
        }
        if (usageStatsTracker == null) {
            usageStatsTracker = UsageStatsTracker(applicationContext)
        }
        if (database == null) {
            try {
                database = StayFocusedDatabase.getInstance(applicationContext)
            } catch (e: Exception) {
                Log.w(TAG, "Database not available in this context", e)
            }
        }
        observeDatabaseState()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        overlayManager?.hideOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val packageName = event.packageName?.toString() ?: return
        handlePackageChanged(packageName)
    }

    fun handlePackageChanged(packageName: String) {
        val target = packageName.trim()
        if (target.isEmpty() || target.equals(applicationContext.packageName, ignoreCase = true)) {
            return
        }

        val isSettingsOrInstaller = packageRegistry?.isSettingsOrInstaller(target) ?: false
        val isGracePeriodActive = isBootGracePeriodProvider()
        val appLimit = cachedAppLimits[target.lowercase()]

        val context = InterceptionContext(
            targetPackageName = target,
            currentTimeMillis = System.currentTimeMillis(),
            appLimit = appLimit,
            activeProfiles = cachedActiveProfiles,
            isStrictModeActive = isStrictModeActive,
            isBreakActive = isBreakActive,
            antiTamperEnabled = BuildConfig.ANTI_TAMPER_ENABLED,
            isSettingsOrInstaller = isSettingsOrInstaller,
            isGracePeriodActive = isGracePeriodActive
        )

        val decision = engine.evaluate(context)

        // Increment launch counts on package transition
        if (target != lastForegroundPackage) {
            lastForegroundPackage = target
            val db = database
            if (db != null) {
                serviceScope.launch {
                    try {
                        usageStatsTracker?.recordAppLaunch(target, db.appLimitDao())
                    } catch (e: Exception) {
                        Log.e(TAG, "Error recording launch for $target", e)
                    }
                }
            }
        }

        when (decision) {
            is InterceptionResult.Block -> {
                val manager = overlayManager
                val canDraw = manager?.canDrawOverlays() ?: false
                Log.i(TAG, "Blocking package $target: ${decision.reason} | canDrawOverlays=$canDraw")
                if (decision.reason is BlockReason.SettingsTamper) {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
                if (manager != null && canDraw) {
                    manager.showOverlay(
                        reason = decision.reason,
                        onReturnHome = {
                            performGlobalAction(GLOBAL_ACTION_HOME)
                        }
                    )
                } else {
                    // Fallback if overlay permission is not yet granted
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
            is InterceptionResult.Allow -> {
                // If user switched away from a blocked app to an allowed app or home launcher, dismiss overlay
                overlayManager?.hideOverlay()
            }
        }
    }

    override fun onInterrupt() {
        overlayManager?.hideOverlay()
    }

    private fun observeDatabaseState() {
        try {
            val db = StayFocusedDatabase.getInstance(applicationContext)

            // 1. Observe App Limits
            serviceScope.launch {
                db.appLimitDao().getAllAppLimits()
                    .catch { e -> Log.e(TAG, "Error observing app limits", e) }
                    .collectLatest { entities ->
                        cachedAppLimits = entities.associate { entity ->
                            entity.packageName.lowercase() to AppLimitSnapshot(
                                packageName = entity.packageName,
                                appName = entity.appName,
                                dailyTimeLimitMinutes = entity.dailyTimeLimitMinutes,
                                dailyLaunchLimit = entity.dailyLaunchLimit,
                                currentDayUsageMs = entity.currentDayUsageMs,
                                currentDayLaunches = entity.currentDayLaunches,
                                isBlocked = entity.isBlocked
                            )
                        }
                    }
            }

            // 2. Observe Focus Profiles with rules
            serviceScope.launch {
                db.focusProfileDao().getAllProfilesWithRules()
                    .catch { e -> Log.e(TAG, "Error observing focus profiles", e) }
                    .collectLatest { profilesWithRules ->
                        cachedActiveProfiles = profilesWithRules
                            .filter { it.profile.isActive }
                            .map { profileWithRules ->
                                FocusProfileRule(
                                    profileId = profileWithRules.profile.id,
                                    name = profileWithRules.profile.name,
                                    isActive = profileWithRules.profile.isActive,
                                    isStrictMode = profileWithRules.profile.isStrictMode,
                                    scheduleStartTime = profileWithRules.profile.scheduleStartTime,
                                    scheduleEndTime = profileWithRules.profile.scheduleEndTime,
                                    activeDaysMask = profileWithRules.profile.activeDaysMask,
                                    blockedPackages = profileWithRules.blockedPackages.map { it.packageName }.toSet(),
                                    blockedDomains = profileWithRules.blockedDomains.map { it.domain }.toSet()
                                )
                            }
                    }
            }

            // 3. Observe Strict Sessions
            serviceScope.launch {
                db.strictSessionDao().getActiveStrictSession()
                    .catch { e -> Log.e(TAG, "Error observing strict sessions", e) }
                    .collectLatest { session ->
                        isStrictModeActive = session != null && session.isActive
                    }
            }

            // 4. Observe Break Sessions
            serviceScope.launch {
                db.breakSessionDao().getActiveBreak()
                    .catch { e -> Log.e(TAG, "Error observing break sessions", e) }
                    .collectLatest { session ->
                        cachedBreakEndTimeMs = if (session != null && session.isActive) session.endTime else 0L
                    }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not initialize database observer (e.g. running in isolated test)", e)
        }
    }
}
