package com.stayfocused.app.service

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.stayfocused.app.BuildConfig
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.data.registry.PackageRegistry
import com.stayfocused.app.domain.InterceptionDecisionEngine
import com.stayfocused.app.domain.SettingsTamperInspector
import com.stayfocused.app.domain.TransientWindowFilter
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    var tamperInspector: SettingsTamperInspector = SettingsTamperInspector()
    var overlayManager: BlockOverlayManager? = null
    var packageRegistry: PackageRegistry? = null
    var usageStatsTracker: UsageStatsTracker? = null
    var database: StayFocusedDatabase? = null

    /**
     * Classifies transient system overlays (notification shade, IME, permission dialogs).
     * Initialized in [onCreate] with the live IME lambda; replaced in unit tests for
     * deterministic, framework-free behaviour.
     */
    var transientFilter: TransientWindowFilter? = null

    /** The last *real* (non-transient) foreground package name observed. */
    private var lastForegroundPackage: String? = null
    private var foregroundMonitorJob: kotlinx.coroutines.Job? = null

    // Boot grace period un-spoofable hardware check
    var isBootGracePeriodProvider: () -> Boolean = {
        com.stayfocused.app.strict.GracePeriodManager.isDefaultGracePeriodActive()
    }

    // In-memory hot cache for zero-disk-latency interception (< 10ms budget)
    @Volatile var cachedActiveProfiles: List<FocusProfileRule> = emptyList()
    @Volatile var cachedAppLimits: Map<String, AppLimitSnapshot> = emptyMap()
    @Volatile var isStrictModeActive: Boolean = false
    @Volatile var cachedStrictEndTimeMs: Long = 0L
    @Volatile var cachedActiveStrictSession: com.stayfocused.app.data.local.entities.StrictSessionEntity? = null
    var trustedClock: com.stayfocused.app.strict.TrustedClock = com.stayfocused.app.strict.TrustedClock()
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
        // Initialize transientFilter here so getSystemService is called after super.onCreate().
        // Tests override this field after create() with a fixed-set lambda.
        if (transientFilter == null) {
            transientFilter = TransientWindowFilter(
                imePackagesProvider = {
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.enabledInputMethodList?.map { it.packageName }?.toSet() ?: emptySet()
                },
                selfPackage = BuildConfig.APPLICATION_ID
            )
        }
        observeDatabaseState()
    }

    override fun onDestroy() {
        super.onDestroy()
        foregroundMonitorJob?.cancel()
        serviceScope.cancel()
        overlayManager?.hideOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString()
        val windowTexts = buildList {
            event.text?.forEach { add(it.toString()) }
            event.contentDescription?.let { add(it.toString()) }
        }

        handleWindowEvent(packageName, className, windowTexts)
    }

    fun handlePackageChanged(packageName: String) {
        handleWindowEvent(packageName, className = null, windowTexts = emptyList())
    }

    fun handleWindowEvent(
        packageName: String,
        className: String?,
        windowTexts: List<String>
    ) {
        val target = packageName.trim()
        if (target.isEmpty() || target.equals(applicationContext.packageName, ignoreCase = true)) {
            foregroundMonitorJob?.cancel()
            return
        }

        // ── Transient-window guard ────────────────────────────────────────────
        // Notification shade, volume panel, IME, permission dialogs and our own
        // overlay windows must NOT hide a live block overlay, update the last real
        // foreground package, or count as a new app launch.
        // Exception: Uninstaller dialogs (e.g. UninstallerActivity in permissioncontroller)
        // must NOT be suppressed so anti-tamper can intercept uninstallation attempts.
        val isUninstallDialog = className?.contains("uninstall", ignoreCase = true) == true ||
                windowTexts.any { it.contains("uninstall", ignoreCase = true) }
        if (!isUninstallDialog && transientFilter?.isTransient(target, className) == true) {
            Log.d(TAG, "Ignoring transient window event from $target ($className)")
            return
        }
        // ─────────────────────────────────────────────────────────────────────

        val activeSession = cachedActiveStrictSession
        val isStrictActive = if (activeSession != null && activeSession.isActive) {
            val snapshot = com.stayfocused.app.strict.SystemClockSnapshotProvider.getSnapshot(applicationContext)
            trustedClock.isSessionActive(activeSession, snapshot)
        } else {
            isStrictModeActive && (cachedStrictEndTimeMs == 0L || System.currentTimeMillis() < cachedStrictEndTimeMs)
        }
        val isSettingsOrInstaller = packageRegistry?.isSettingsOrInstaller(target) ?: false
        val isTargetSettingsOrInstaller = isSettingsOrInstaller ||
                SettingsTamperInspector.SETTINGS_PACKAGES.contains(target.lowercase()) ||
                SettingsTamperInspector.INSTALLER_PACKAGES.contains(target.lowercase()) ||
                target.lowercase() == SettingsTamperInspector.PLAY_STORE_PACKAGE ||
                target.lowercase() == SettingsTamperInspector.VPN_DIALOGS_PACKAGE ||
                target.lowercase().endsWith(".settings") ||
                target.lowercase().contains("safecenter") ||
                target.lowercase().contains("securitycenter")

        // Code-level privacy scoping: node text extraction is ONLY performed when navigating
        // into settings or installer packages, ensuring zero screen-scraping for user apps.
        val effectiveTexts = if (isTargetSettingsOrInstaller) {
            try {
                val gathered = mutableListOf<String>()
                gathered.addAll(windowTexts)
                fun collectTexts(node: android.view.accessibility.AccessibilityNodeInfo?, depth: Int) {
                    if (node == null || depth > 4 || gathered.size >= 25) return
                    node.text?.toString()?.takeIf { it.isNotBlank() }?.let { gathered.add(it) }
                    node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { gathered.add(it) }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        node.paneTitle?.toString()?.takeIf { it.isNotBlank() }?.let { gathered.add(it) }
                    }
                    for (i in 0 until node.childCount) {
                        if (gathered.size >= 25) break
                        collectTexts(node.getChild(i), depth + 1)
                    }
                }
                collectTexts(rootInActiveWindow, 0)
                gathered
            } catch (_: Throwable) {
                windowTexts
            }
        } else {
            windowTexts
        }

        // Diagnostics logging (Step 1): debug-only log of (packageName, className, windowTexts) for settings/installer windows
        if (BuildConfig.DEBUG && isTargetSettingsOrInstaller) {
            Log.d(TAG, "Settings/Installer window: pkg=$target, cls=$className, texts=$effectiveTexts")
        }

        // Fine-grained anti-tamper inspection for Settings, PackageInstaller, and Play Store
        if (isTargetSettingsOrInstaller) {
            val isGracePeriodActive = isBootGracePeriodProvider()
            val tamperDecision = tamperInspector.evaluate(
                packageName = target,
                className = className,
                windowTexts = effectiveTexts,
                isStrictModeActive = isStrictActive,
                isGracePeriodActive = isGracePeriodActive,
                wasStrictActiveAtBoot = com.stayfocused.app.strict.GracePeriodManager.wasStrictActiveAtBoot,
                antiTamperEnabled = BuildConfig.ANTI_TAMPER_ENABLED || isStrictActive
            )

            when (tamperDecision) {
                is SettingsTamperInspector.TamperDecision.BlockTamper -> {
                    foregroundMonitorJob?.cancel()
                    Log.w(TAG, "Blocking tamper attempt in $target ($className): ${tamperDecision.reason}")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    val manager = overlayManager
                    val canDraw = manager?.canDrawOverlays() ?: false
                    if (manager != null && canDraw) {
                        manager.showOverlay(
                            reason = BlockReason.SettingsTamper(tamperDecision.reason),
                            onReturnHome = { performGlobalAction(GLOBAL_ACTION_HOME) }
                        )
                    }
                    return
                }
                is SettingsTamperInspector.TamperDecision.Allow -> {
                    // Safe Settings navigation (Wi-Fi, Bluetooth, Audio, Display, etc.)
                    overlayManager?.hideOverlay()
                    foregroundMonitorJob?.cancel()
                    return
                }
            }
        }

        val isGracePeriodActive = isBootGracePeriodProvider()
        val appLimit = cachedAppLimits[target.lowercase()]

        val context = InterceptionContext(
            targetPackageName = target,
            currentTimeMillis = System.currentTimeMillis(),
            appLimit = appLimit,
            activeProfiles = cachedActiveProfiles,
            isStrictModeActive = isStrictActive,
            isBreakActive = isBreakActive,
            antiTamperEnabled = BuildConfig.ANTI_TAMPER_ENABLED || isStrictActive,
            isSettingsOrInstaller = false,
            isGracePeriodActive = isGracePeriodActive,
            wasStrictActiveAtBoot = com.stayfocused.app.strict.GracePeriodManager.wasStrictActiveAtBoot
        )

        val decision = engine.evaluate(context)

        // Increment launch counts on *real* package transition (transients have already returned above)
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
                foregroundMonitorJob?.cancel()
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
                    // Fallback when SYSTEM_ALERT_WINDOW is not granted
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
            }
            is InterceptionResult.Allow -> {
                // If user switched away from a blocked app to an allowed app or home launcher, dismiss overlay
                overlayManager?.hideOverlay()
                foregroundMonitorJob?.cancel()

                // If app has active time limit, check asynchronously and poll every 5s while in foreground
                if (appLimit != null && appLimit.dailyTimeLimitMinutes > 0) {
                    val limitMs = appLimit.dailyTimeLimitMinutes * 60 * 1000L
                    foregroundMonitorJob = serviceScope.launch {
                        while (isActive) {
                            val current = usageStatsTracker?.queryPackageUsageToday(target) ?: 0L
                            if (current >= limitMs) {
                                val db = database
                                if (db != null) {
                                    try {
                                        db.appLimitDao().updateUsageAndLaunches(
                                            packageName = target,
                                            usageMs = current,
                                            launches = appLimit.currentDayLaunches
                                        )
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Failed to persist limit lockout for $target", e)
                                    }
                                }

                                withContext(Dispatchers.Main) {
                                    Log.i(TAG, "Limit expired in foreground for $target ($current >= $limitMs). Intercepting.")
                                    val reason = BlockReason.LimitReached(
                                        appName = appLimit.appName,
                                        limitType = com.stayfocused.app.domain.model.LimitType.TIME_LIMIT,
                                        used = current,
                                        limit = limitMs
                                    )
                                    performGlobalAction(GLOBAL_ACTION_HOME)
                                    overlayManager?.showOverlay(
                                        reason = reason,
                                        onReturnHome = { performGlobalAction(GLOBAL_ACTION_HOME) }
                                    )
                                }
                                break
                            }
                            delay(5000L)
                        }
                    }
                }
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
                        cachedActiveStrictSession = session
                        isStrictModeActive = session != null && session.isActive
                        cachedStrictEndTimeMs = session?.targetEndTime ?: 0L
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
