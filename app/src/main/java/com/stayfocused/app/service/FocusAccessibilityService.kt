package com.stayfocused.app.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.annotation.VisibleForTesting
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
import android.view.accessibility.AccessibilityNodeInfo
import com.stayfocused.app.domain.ShortFormFeedInspector
import com.stayfocused.app.domain.ShortFormViewContext
import com.stayfocused.app.data.local.entities.ShortFormRuleEntity
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
    var shortFormInspector: ShortFormFeedInspector = ShortFormFeedInspector()
    @Volatile var cachedShortFormRules: Map<String, ShortFormRuleEntity> = emptyMap()
    private val lastShortFormCheckElapsed = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val lastScrollIncrementElapsed = java.util.concurrent.ConcurrentHashMap<String, Long>()
    @Volatile var shortFormBackCount: Int = 0
    @Volatile var lastShortFormBackElapsed: Long = 0L

    /**
     * Classifies transient system overlays (notification shade, IME, permission dialogs).
     * Initialized in [onCreate] with the live IME lambda; replaced in unit tests for
     * deterministic, framework-free behaviour.
     */
    var transientFilter: TransientWindowFilter? = null

    /** The last *real* (non-transient) foreground package name observed. */
    private var lastForegroundPackage: String? = null
    private var foregroundMonitorJob: kotlinx.coroutines.Job? = null

    // Real-time monotonic foreground tracking fields
    @Volatile var currentForegroundPackage: String? = null
    @Volatile var currentForegroundStartElapsed: Long = 0L
    @Volatile var currentForegroundBaseUsageMs: Long = 0L
    @Volatile var isScreenInteractive: Boolean = true
    private var screenReceiverRegistered: Boolean = false

    fun setScreenInteractiveState(interactive: Boolean) {
        isScreenInteractive = interactive
        if (!interactive) {
            val pkg = currentForegroundPackage
            if (pkg != null) {
                val elapsed = SystemClock.elapsedRealtime() - currentForegroundStartElapsed
                if (elapsed > 0) {
                    val total = currentForegroundBaseUsageMs + elapsed
                    currentForegroundBaseUsageMs = total
                    currentForegroundStartElapsed = SystemClock.elapsedRealtime()
                    persistUsage(pkg, total)
                }
            }
            foregroundMonitorJob?.cancel()
        } else {
            currentForegroundStartElapsed = SystemClock.elapsedRealtime()
            val pkg = currentForegroundPackage
            if (pkg != null) {
                val limit = cachedAppLimits[pkg.lowercase()]
                if (limit != null && limit.dailyTimeLimitMinutes > 0) {
                    startForegroundMonitor(pkg, limit)
                }
            }
        }
    }

    @VisibleForTesting
    internal val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> setScreenInteractiveState(false)
                Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_ON -> setScreenInteractiveState(true)
            }
        }
    }

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
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            registerReceiver(screenReceiver, filter)
            screenReceiverRegistered = true
        } catch (e: Exception) {
            Log.w(TAG, "Could not register screen receiver", e)
        }
        observeDatabaseState()
    }

    override fun onDestroy() {
        super.onDestroy()
        foregroundMonitorJob?.cancel()
        serviceScope.cancel()
        overlayManager?.hideOverlay()
        if (screenReceiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering screen receiver", e)
            }
            screenReceiverRegistered = false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        runCatching {
            if (event == null) return
            val eventType = event.eventType
            val packageName = event.packageName?.toString() ?: return

            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val className = event.className?.toString()
                val windowTexts = buildList {
                    event.text?.forEach { add(it.toString()) }
                    event.contentDescription?.let { add(it.toString()) }
                }
                handleWindowEvent(packageName, className, windowTexts)
                checkShortFormVideoFeed(packageName, className, event)
            } else if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                checkShortFormVideoFeed(packageName, event.className?.toString(), event)
            } else if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                // Only inspect content changes for Settings/Installer packages to prevent event flooding on user apps
                if (isSettingsOrInstallerPackage(packageName)) {
                    val className = event.className?.toString()
                    val windowTexts = buildList {
                        event.text?.forEach { add(it.toString()) }
                        event.contentDescription?.let { add(it.toString()) }
                    }
                    handleWindowEvent(packageName, className, windowTexts)
                } else if (shortFormInspector.isMonitoredPackage(packageName)) {
                    checkShortFormVideoFeed(packageName, event.className?.toString(), event)
                }
            }
        }.onFailure { e ->
            Log.e(TAG, "Safely caught error in onAccessibilityEvent", e)
        }
    }

    fun isSettingsOrInstallerPackage(target: String): Boolean {
        val lower = target.lowercase()
        return packageRegistry?.isSettingsOrInstaller(target) == true ||
                SettingsTamperInspector.SETTINGS_PACKAGES.contains(lower) ||
                SettingsTamperInspector.INSTALLER_PACKAGES.contains(lower) ||
                lower == SettingsTamperInspector.PLAY_STORE_PACKAGE ||
                lower == SettingsTamperInspector.VPN_DIALOGS_PACKAGE ||
                lower.endsWith(".settings") ||
                lower.contains("safecenter") ||
                lower.contains("securitycenter")
    }

    @Suppress("DEPRECATION")
    fun checkShortFormVideoFeed(
        packageName: String,
        className: String?,
        event: AccessibilityEvent
    ) {
        if (!shortFormInspector.isMonitoredPackage(packageName)) return

        // Fast-path: check if any short form rule is blocked for this package
        val hasBlockedRules = cachedShortFormRules.values.any { it.packageName == packageName && it.isBlocked }
        if (!hasBlockedRules) return

        val eventType = event.eventType
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            // Phase 1 bitmask gate: ONLY evaluate if SUBTREE structure changed
            if ((event.contentChangeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE) == 0) {
                return
            }
            // Cooldown throttle: 150ms per package
            val now = SystemClock.elapsedRealtime()
            val last = lastShortFormCheckElapsed[packageName] ?: 0L
            if (now - last < 150L) return
            lastShortFormCheckElapsed[packageName] = now
        }

        val presentIds = mutableSetOf<String>()
        val selectedTabDescs = mutableSetOf<String>()
        val selectedTabIds = mutableSetOf<String>()

        val root = rootInActiveWindow
        if (root != null) {
            try {
                val targetIds = when (packageName) {
                    ShortFormFeedInspector.PKG_YOUTUBE -> listOf(
                        "com.google.android.youtube:id/reel_recycler",
                        "com.google.android.youtube:id/reel_watch_fragment_root",
                        "com.google.android.youtube:id/reel_player_page_container"
                    )
                    ShortFormFeedInspector.PKG_INSTAGRAM -> listOf(
                        "com.instagram.android:id/clips_viewer_view_pager",
                        "com.instagram.android:id/clips_video_container",
                        "com.instagram.android:id/reel_viewer_root",
                        "com.instagram.android:id/story_viewer_container"
                    )
                    ShortFormFeedInspector.PKG_SNAPCHAT -> listOf(
                        "com.snapchat.android:id/spotlight_view_container"
                    )
                    ShortFormFeedInspector.PKG_FACEBOOK -> listOf(
                        "com.facebook.katana:id/fb_shorts_viewer_container"
                    )
                    else -> emptyList()
                }
                for (targetId in targetIds) {
                    val nodes = root.findAccessibilityNodeInfosByViewId(targetId)
                    try {
                        if (nodes.isNotEmpty()) {
                            presentIds.add(targetId)
                            break // Early exit on first matching container
                        }
                    } finally {
                        nodes.forEach { it.recycle() }
                    }
                }

                // Tab bar checks: strictly scope to navigation bars to preserve Zero Screen Scraping
                if (packageName == ShortFormFeedInspector.PKG_YOUTUBE) {
                    val pivotBars = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/pivot_bar")
                    val pivotBar = pivotBars.firstOrNull()
                    val searchNode = pivotBar ?: root
                    val shortsTabs = searchNode.findAccessibilityNodeInfosByText("Shorts")
                    try {
                        for (tab in shortsTabs) {
                            val isNavTab = tab.className?.contains("Tab") == true ||
                                    tab.viewIdResourceName?.contains("tab") == true ||
                                    tab.viewIdResourceName?.contains("pivot") == true ||
                                    tab.parent?.viewIdResourceName?.contains("pivot") == true
                            if (isNavTab && tab.isSelected) {
                                selectedTabDescs.add("shorts")
                            }
                        }
                    } finally {
                        shortsTabs.forEach { it.recycle() }
                        pivotBars.forEach { it.recycle() }
                    }
                } else if (packageName == ShortFormFeedInspector.PKG_INSTAGRAM) {
                    val clipsTabs = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_tab")
                    try {
                        for (tab in clipsTabs) {
                            if (tab.isSelected) {
                                selectedTabIds.add("com.instagram.android:id/clips_tab")
                            }
                        }
                    } finally {
                        clipsTabs.forEach { it.recycle() }
                    }
                }
            } finally {
                root.recycle()
            }
        }

        val context = ShortFormViewContext(
            packageName = packageName,
            className = className,
            presentViewIds = presentIds,
            selectedTabDescriptions = selectedTabDescs,
            selectedTabIds = selectedTabIds
        )

        val detectedFeedId = shortFormInspector.detectFeed(context)
        if (detectedFeedId != null) {
            val rule = cachedShortFormRules[detectedFeedId]
            if (rule != null && rule.isBlocked) {
                if (rule.dailyScrollLimit > 0 && rule.currentDayScrolls < rule.dailyScrollLimit) {
                    // Quota in progress: track video transition (throttled by 2000ms per video)
                    val now = SystemClock.elapsedRealtime()
                    val lastScroll = lastScrollIncrementElapsed[detectedFeedId] ?: 0L
                    if (now - lastScroll >= 2000L) {
                        lastScrollIncrementElapsed[detectedFeedId] = now
                        val nextScrolls = rule.currentDayScrolls + 1
                        val updatedRule = rule.copy(currentDayScrolls = nextScrolls)
                        cachedShortFormRules = cachedShortFormRules + (detectedFeedId to updatedRule)

                        val db = database
                        if (db != null) {
                            serviceScope.launch(Dispatchers.IO) {
                                try {
                                    db.shortFormRuleDao().incrementScrollCount(detectedFeedId, System.currentTimeMillis())
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to increment scroll count for $detectedFeedId", e)
                                }
                            }
                        }

                        if (nextScrolls >= rule.dailyScrollLimit) {
                            val safeTarget = shortFormInspector.getSafeNavigationTarget(detectedFeedId)
                            handleShortFormEviction(
                                ShortFormFeedInspector.ShortFormDecision.Evict(
                                    feedId = detectedFeedId,
                                    rule = updatedRule,
                                    safeTabId = safeTarget.first,
                                    safeTabDescription = safeTarget.second
                                )
                            )
                        }
                    }
                } else if (rule.dailyScrollLimit == 0 || rule.currentDayScrolls >= rule.dailyScrollLimit) {
                    val safeTarget = shortFormInspector.getSafeNavigationTarget(detectedFeedId)
                    handleShortFormEviction(
                        ShortFormFeedInspector.ShortFormDecision.Evict(
                            feedId = detectedFeedId,
                            rule = rule,
                            safeTabId = safeTarget.first,
                            safeTabDescription = safeTarget.second
                        )
                    )
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    fun handleShortFormEviction(decision: ShortFormFeedInspector.ShortFormDecision.Evict) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastShortFormBackElapsed < 2000L) {
            shortFormBackCount++
        } else {
            shortFormBackCount = 1
        }
        lastShortFormBackElapsed = now

        Log.i(TAG, "Short-form feed detected (${decision.feedId}). Eviction attempt $shortFormBackCount.")

        var safeNavigationSuccess = false
        if (shortFormBackCount >= 2 && (decision.safeTabId != null || decision.safeTabDescription != null)) {
            val rootSafe = rootInActiveWindow
            if (rootSafe != null) {
                try {
                    if (decision.safeTabId != null) {
                        val safeNodes = rootSafe.findAccessibilityNodeInfosByViewId(decision.safeTabId)
                        try {
                            val targetNode = safeNodes.firstOrNull { node ->
                                node.isClickable || node.parent?.isClickable == true
                            }
                            val clickableNode = if (targetNode?.isClickable == true) targetNode else targetNode?.parent
                            if (clickableNode != null) {
                                safeNavigationSuccess = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            }
                        } finally {
                            safeNodes.forEach { it.recycle() }
                        }
                    }
                    if (!safeNavigationSuccess && decision.safeTabDescription != null) {
                        val safeNodes = rootSafe.findAccessibilityNodeInfosByText(decision.safeTabDescription)
                        try {
                            val targetNode = safeNodes.firstOrNull { node ->
                                (node.isClickable || node.parent?.isClickable == true) &&
                                (node.className?.contains("Tab") == true ||
                                 node.viewIdResourceName?.contains("tab") == true ||
                                 node.viewIdResourceName?.contains("pivot") == true ||
                                 node.parent?.viewIdResourceName?.contains("pivot") == true)
                            }
                            val clickableNode = if (targetNode?.isClickable == true) targetNode else targetNode?.parent
                            if (clickableNode != null) {
                                safeNavigationSuccess = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            }
                        } finally {
                            safeNodes.forEach { it.recycle() }
                        }
                    }
                } finally {
                    rootSafe.recycle()
                }
            }
        }

        if (safeNavigationSuccess) {
            shortFormBackCount = 0
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }

        // Persist eviction/scroll count asynchronously for instant blocks (quota limit is tracked per scroll)
        if (decision.rule.dailyScrollLimit == 0) {
            val db = database
            if (db != null) {
                serviceScope.launch(Dispatchers.IO) {
                    try {
                        db.shortFormRuleDao().incrementScrollCount(decision.feedId, System.currentTimeMillis())
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to increment scroll count for ${decision.feedId}", e)
                    }
                }
            }
        }
    }

    fun persistUsage(packageName: String, usageMs: Long) {
        val lower = packageName.lowercase()
        val limit = cachedAppLimits[lower] ?: return
        cachedAppLimits = cachedAppLimits + (lower to limit.copy(currentDayUsageMs = usageMs))
        val db = database ?: return
        serviceScope.launch {
            try {
                db.appLimitDao().updateUsageAndLaunches(
                    packageName = packageName,
                    usageMs = usageMs,
                    launches = limit.currentDayLaunches
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist usage for $packageName", e)
            }
        }
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
            val previousPkg = currentForegroundPackage
            if (previousPkg != null && isScreenInteractive) {
                val elapsed = SystemClock.elapsedRealtime() - currentForegroundStartElapsed
                if (elapsed > 0) {
                    val updatedUsage = currentForegroundBaseUsageMs + elapsed
                    persistUsage(previousPkg, updatedUsage)
                }
            }
            currentForegroundPackage = target
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
                val selfTokens = tamperInspector.selfAppNames + tamperInspector.selfPackageName.lowercase()
                var foundSelf = windowTexts.any { wt ->
                    val lower = wt.lowercase()
                    selfTokens.any { token -> lower.contains(token) }
                }

                fun checkAndAdd(text: CharSequence?) {
                    if (text == null) return
                    val str = text.toString().trim()
                    if (str.isNotEmpty()) {
                        gathered.add(str)
                        val lower = str.lowercase()
                        if (selfTokens.any { token -> lower.contains(token) }) {
                            foundSelf = true
                        }
                    }
                }

                val root = rootInActiveWindow
                if (root != null) {
                    // Depth-bounded traversal collecting headers, pane titles, and window items
                    var visitedNodes = 0
                    fun collectTexts(node: android.view.accessibility.AccessibilityNodeInfo?, depth: Int) {
                        if (node == null || depth > 12 || gathered.size >= 40 || visitedNodes >= 40) return
                        visitedNodes++
                        checkAndAdd(node.text)
                        checkAndAdd(node.contentDescription)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            checkAndAdd(node.paneTitle)
                        }
                        val childCount = node.childCount
                        for (i in 0 until childCount) {
                            if (gathered.size >= 40 || visitedNodes >= 40) break
                            try {
                                val child = node.getChild(i)
                                collectTexts(child, depth + 1)
                            } catch (_: Throwable) {
                                continue
                            }
                        }
                    }

                    collectTexts(root, 0)

                    // Fast indexed text lookup for Monk Mode tokens if not encountered in traversal
                    if (!foundSelf) {
                        for (token in selfTokens) {
                            try {
                                val matchedNodes = root.findAccessibilityNodeInfosByText(token)
                                if (!matchedNodes.isNullOrEmpty()) {
                                    foundSelf = true
                                    gathered.add(token)
                                    break
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                }
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
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    val manager = overlayManager
                    val canDraw = manager?.canDrawOverlays() ?: false
                    if (manager != null && canDraw) {
                        manager.showOverlay(
                            reason = BlockReason.SettingsTamper(tamperDecision.reason),
                            onReturnHome = {
                                performGlobalAction(GLOBAL_ACTION_BACK)
                                performGlobalAction(GLOBAL_ACTION_HOME)
                            }
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
        val nowElapsed = SystemClock.elapsedRealtime()

        // Handle package transitions: commit departure usage and initialize new session
        if (target != currentForegroundPackage) {
            val previousPkg = currentForegroundPackage
            if (previousPkg != null && isScreenInteractive) {
                val elapsed = nowElapsed - currentForegroundStartElapsed
                if (elapsed > 0) {
                    val updatedUsage = currentForegroundBaseUsageMs + elapsed
                    persistUsage(previousPkg, updatedUsage)
                }
            }

            currentForegroundPackage = target
            currentForegroundStartElapsed = nowElapsed

            val dbUsage = appLimit?.currentDayUsageMs ?: 0L
            val osUsage = if (appLimit != null && appLimit.dailyTimeLimitMinutes > 0) {
                usageStatsTracker?.queryPackageUsageToday(target) ?: 0L
            } else {
                0L
            }
            currentForegroundBaseUsageMs = maxOf(dbUsage, osUsage)
        }

        // Immediate launch check: if daily time limit is already exhausted, block immediately!
        if (appLimit != null && appLimit.dailyTimeLimitMinutes > 0) {
            val limitMs = appLimit.dailyTimeLimitMinutes * 60 * 1000L
            if (currentForegroundBaseUsageMs >= limitMs) {
                foregroundMonitorJob?.cancel()
                Log.i(TAG, "Blocking package $target on launch: usage $currentForegroundBaseUsageMs >= $limitMs limit")
                val reason = BlockReason.LimitReached(
                    appName = appLimit.appName,
                    limitType = com.stayfocused.app.domain.model.LimitType.TIME_LIMIT,
                    used = currentForegroundBaseUsageMs,
                    limit = limitMs
                )
                val manager = overlayManager
                val canDraw = manager?.canDrawOverlays() ?: false
                if (manager != null && canDraw) {
                    manager.showOverlay(
                        reason = reason,
                        onReturnHome = { performGlobalAction(GLOBAL_ACTION_HOME) }
                    )
                } else {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                }
                return
            }
        }

        val context = InterceptionContext(
            targetPackageName = target,
            currentTimeMillis = System.currentTimeMillis(),
            appLimit = appLimit?.copy(currentDayUsageMs = currentForegroundBaseUsageMs),
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

                // If app has active time limit, monitor live in 1-second ticks
                if (appLimit != null && appLimit.dailyTimeLimitMinutes > 0) {
                    startForegroundMonitor(target, appLimit)
                }
            }
        }
    }

    private fun startForegroundMonitor(target: String, appLimit: AppLimitSnapshot) {
        val limitMs = appLimit.dailyTimeLimitMinutes * 60 * 1000L
        var sessionStartDay = usageStatsTracker?.getStartOfToday() ?: 0L
        foregroundMonitorJob?.cancel()
        foregroundMonitorJob = serviceScope.launch {
            var lastPersistElapsed = SystemClock.elapsedRealtime()
            while (isActive && isScreenInteractive) {
                delay(1000L)
                val nowElapsed = SystemClock.elapsedRealtime()

                // Midnight rollover check: commit yesterday's usage, reset base usage, and continue monitoring
                val todayStart = usageStatsTracker?.getStartOfToday() ?: sessionStartDay
                if (todayStart > sessionStartDay) {
                    val yesterdayTotal = currentForegroundBaseUsageMs + (nowElapsed - currentForegroundStartElapsed)
                    persistUsage(target, yesterdayTotal)
                    currentForegroundBaseUsageMs = 0L
                    currentForegroundStartElapsed = nowElapsed
                    lastPersistElapsed = nowElapsed
                    sessionStartDay = todayStart
                    continue
                }

                val sessionElapsed = nowElapsed - currentForegroundStartElapsed
                val currentTotal = currentForegroundBaseUsageMs + sessionElapsed

                // Incremental persistence every 15 seconds to defend against crashes/reboots
                if (nowElapsed - lastPersistElapsed >= 15_000L) {
                    lastPersistElapsed = nowElapsed
                    persistUsage(target, currentTotal)
                }

                if (currentTotal >= limitMs) {
                    persistUsage(target, currentTotal)
                    withContext(Dispatchers.Main) {
                        Log.i(TAG, "Limit expired in foreground for $target ($currentTotal >= $limitMs). Intercepting.")
                        val reason = BlockReason.LimitReached(
                            appName = appLimit.appName,
                            limitType = com.stayfocused.app.domain.model.LimitType.TIME_LIMIT,
                            used = currentTotal,
                            limit = limitMs
                        )
                        val manager = overlayManager
                        val canDraw = manager?.canDrawOverlays() ?: false
                        if (manager != null && canDraw) {
                            manager.showOverlay(
                                reason = reason,
                                onReturnHome = { performGlobalAction(GLOBAL_ACTION_HOME) }
                            )
                        } else {
                            performGlobalAction(GLOBAL_ACTION_HOME)
                        }
                    }
                    break
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

            // 5. Observe Short-Form Video Feed Rules
            serviceScope.launch {
                try {
                    val existing = db.shortFormRuleDao().getAllRules()
                    if (existing.isEmpty()) {
                        db.shortFormRuleDao().insertDefaultRules(StayFocusedDatabase.DEFAULT_SHORT_FORM_RULES)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed seeding default short form rules", e)
                }
                db.shortFormRuleDao().getAllRulesFlow()
                    .catch { e -> Log.e(TAG, "Error observing short-form rules", e) }
                    .collectLatest { rules ->
                        cachedShortFormRules = rules.associateBy { it.id }
                    }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not initialize database observer (e.g. running in isolated test)", e)
        }
    }
}
