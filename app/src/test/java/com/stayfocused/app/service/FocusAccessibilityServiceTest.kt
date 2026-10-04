package com.stayfocused.app.service

import android.content.Context
import android.view.accessibility.AccessibilityEvent
import androidx.test.core.app.ApplicationProvider
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.data.registry.PackageRegistry
import com.stayfocused.app.domain.InterceptionDecisionEngine
import com.stayfocused.app.domain.TransientWindowFilter
import com.stayfocused.app.domain.model.AppLimitSnapshot
import com.stayfocused.app.domain.model.BlockReason
import com.stayfocused.app.domain.model.FocusProfileRule
import com.stayfocused.app.domain.model.LimitType
import com.stayfocused.app.tracker.UsageStatsTracker
import com.stayfocused.app.ui.overlay.BlockOverlayManager
import io.mockk.clearMocks
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@Suppress("DEPRECATION")
class FocusAccessibilityServiceTest {

    private lateinit var service: FocusAccessibilityService
    private lateinit var mockOverlayManager: BlockOverlayManager

    @Before
    fun setUp() {
        service = spyk(Robolectric.buildService(FocusAccessibilityService::class.java).create().get())
        mockOverlayManager = mockk(relaxed = true)
        every { mockOverlayManager.canDrawOverlays() } returns true
        service.overlayManager = mockOverlayManager
        service.packageRegistry = PackageRegistry.createDefault()
        service.isBootGracePeriodProvider = { false }
        // Install a deterministic transient filter with no IME packages so tests that
        // don't customise it get predictable, framework-free behaviour.
        service.transientFilter = TransientWindowFilter(
            imePackagesProvider = { emptySet() },
            selfPackage = "com.stayfocused.app",
            imeCacheTtlMs = 0L
        )
    }

    @After
    fun tearDown() {
        service.onDestroy()
    }

    @Test
    fun testSelfPackageNeverIntercepted() {
        val event = AccessibilityEvent.obtain().apply {
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageName = "com.stayfocused.app"
        }

        service.onAccessibilityEvent(event)

        verify(exactly = 0) { mockOverlayManager.showOverlay(any(), any()) }
        verify(exactly = 0) { service.performGlobalAction(any()) }
    }

    @Test
    fun testBlockedAppTriggersOverlay() {
        val blockedPackage = "com.instagram.android"
        service.cachedAppLimits = mapOf(
            blockedPackage to AppLimitSnapshot(
                packageName = blockedPackage,
                appName = "Instagram",
                dailyTimeLimitMinutes = 30,
                currentDayUsageMs = 30 * 60 * 1000L
            )
        )

        val event = AccessibilityEvent.obtain().apply {
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageName = blockedPackage
        }

        service.onAccessibilityEvent(event)

        verify(atLeast = 1) {
            mockOverlayManager.showOverlay(
                match { it is BlockReason.LimitReached && it.appName == "Instagram" },
                any()
            )
        }
    }

    @Test
    fun testAllowedAppHidesOverlay() {
        val allowedPackage = "com.google.android.calculator"

        val event = AccessibilityEvent.obtain().apply {
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageName = allowedPackage
        }

        service.onAccessibilityEvent(event)

        verify(atLeast = 1) { mockOverlayManager.hideOverlay() }
        verify(exactly = 0) { mockOverlayManager.showOverlay(any(), any()) }
    }

    @Test
    fun testFallbackToGlobalActionHomeWhenOverlayPermissionDenied() {
        every { mockOverlayManager.canDrawOverlays() } returns false

        val blockedPackage = "com.twitter.android"
        service.cachedAppLimits = mapOf(
            blockedPackage to AppLimitSnapshot(
                packageName = blockedPackage,
                appName = "X",
                isBlocked = true
            )
        )

        every { service.performGlobalAction(any()) } returns true

        val event = AccessibilityEvent.obtain().apply {
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageName = blockedPackage
        }

        service.onAccessibilityEvent(event)

        verify(atLeast = 1) { service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME) }
    }

    @Test
    fun testActiveFocusProfileBlocksTargetPackage() {
        val blockedPackage = "com.netflix.mediaclient"
        service.cachedActiveProfiles = listOf(
            FocusProfileRule(
                profileId = 1L,
                name = "Deep Focus",
                isActive = true,
                blockedPackages = setOf(blockedPackage)
            )
        )

        val event = AccessibilityEvent.obtain().apply {
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageName = blockedPackage
        }

        service.onAccessibilityEvent(event)

        verify(atLeast = 1) {
            mockOverlayManager.showOverlay(
                match { it is BlockReason.ProfileActive && it.profileName == "Deep Focus" },
                any()
            )
        }
    }

    @Test
    fun testMonkModeAppInfoBlockedInStrictMode() {
        service.isStrictModeActive = true
        every { service.performGlobalAction(any()) } returns true

        val event = AccessibilityEvent.obtain().apply {
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageName = "com.android.settings"
            className = "com.android.settings.applications.InstalledAppDetails"
            text.add("Monk Mode")
        }

        service.onAccessibilityEvent(event)

        verify(atLeast = 1) { service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME) }
        verify(atLeast = 1) {
            mockOverlayManager.showOverlay(
                match { it is BlockReason.SettingsTamper },
                any()
            )
        }
    }

    @Test
    fun testNormalSettingsAllowedInStrictMode() {
        service.isStrictModeActive = true

        val event = AccessibilityEvent.obtain().apply {
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageName = "com.android.settings"
            className = "com.android.settings.wifi.WifiSettings"
            text.add("Wi-Fi")
        }

        service.onAccessibilityEvent(event)

        verify(atLeast = 1) { mockOverlayManager.hideOverlay() }
        verify(exactly = 0) { mockOverlayManager.showOverlay(any(), any()) }
    }

    @Test
    fun testPackageInstallerBlockedInStrictMode() {
        service.isStrictModeActive = true
        every { service.performGlobalAction(any()) } returns true

        val event = AccessibilityEvent.obtain().apply {
            eventType = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            packageName = "com.google.android.packageinstaller"
            className = "com.android.packageinstaller.UninstallerActivity"
            text.add("Do you want to uninstall Monk Mode?")
        }

        service.onAccessibilityEvent(event)

        verify(atLeast = 1) { service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME) }
        verify(atLeast = 1) {
            mockOverlayManager.showOverlay(
                match { it is BlockReason.SettingsTamper },
                any()
            )
        }
    }

    // ─── Transient-window bug-fix tests ───────────────────────────────────────

    /**
     * Bug 1 regression: A systemui event (notification shade pull) while a blocked app
     * is foreground must NOT call hideOverlay().
     */
    @Test
    fun testSystemUiTransientEventDoesNotHideOverlay() {
        // Install a filter that treats systemui as transient
        service.transientFilter = TransientWindowFilter(
            imePackagesProvider = { emptySet() },
            selfPackage = "com.stayfocused.app",
            imeCacheTtlMs = 0L
        )

        // First, block Instagram
        val blockedPackage = "com.instagram.android"
        service.cachedAppLimits = mapOf(
            blockedPackage to AppLimitSnapshot(
                packageName = blockedPackage,
                appName = "Instagram",
                isBlocked = true
            )
        )
        every { service.performGlobalAction(any()) } returns true
        service.handleWindowEvent(blockedPackage, className = null, windowTexts = emptyList())
        // overlay should have been shown
        verify(atLeast = 1) { mockOverlayManager.showOverlay(any(), any()) }

        // Clear recorded call counts but retain existing stubs and behaviors (answers = false)
        clearMocks(mockOverlayManager, answers = false)

        // Now fire a systemui event (notification shade)
        service.handleWindowEvent("com.android.systemui", className = null, windowTexts = emptyList())

        // hideOverlay must NOT be called for a transient event
        verify(exactly = 0) { mockOverlayManager.hideOverlay() }
        verify(exactly = 0) { mockOverlayManager.showOverlay(any(), any()) }
    }

    /**
     * Bug 2 regression: An IME event must not count as a new launch of the limited app.
     * After blocked-app -> IME -> blocked-app again, there should still be exactly 1
     * call to recordAppLaunch for blocked-app and 0 calls for IME package.
     */
    @Test
    fun testImeTransientEventDoesNotCountAsNewLaunch() {
        val imePackage = "com.google.android.inputmethod.latin"
        service.transientFilter = TransientWindowFilter(
            imePackagesProvider = { setOf(imePackage) },
            selfPackage = "com.stayfocused.app",
            imeCacheTtlMs = 0L
        )

        val mockTracker = mockk<UsageStatsTracker>(relaxed = true)
        val mockDatabase = mockk<StayFocusedDatabase>(relaxed = true)
        service.usageStatsTracker = mockTracker
        service.database = mockDatabase

        val blockedPackage = "com.instagram.android"
        service.cachedAppLimits = mapOf(
            blockedPackage to AppLimitSnapshot(
                packageName = blockedPackage,
                appName = "Instagram",
                dailyLaunchLimit = 2,
                currentDayLaunches = 0,
                isBlocked = false
            )
        )
        every { service.performGlobalAction(any()) } returns true

        // 1st real foreground: Instagram (launch #1)
        service.handleWindowEvent(blockedPackage, className = null, windowTexts = emptyList())

        // Transient IME popup — must NOT update lastForegroundPackage or record launch
        service.handleWindowEvent(imePackage, className = null, windowTexts = emptyList())

        // 2nd appearance of Instagram — because lastForegroundPackage is still Instagram,
        // this is NOT a new package transition and must NOT record another launch.
        service.handleWindowEvent(blockedPackage, className = null, windowTexts = emptyList())

        // Verify: recordAppLaunch was called exactly once for blockedPackage, and never for IME
        coVerify(timeout = 2000, exactly = 1) { mockTracker.recordAppLaunch(eq(blockedPackage), any()) }
        coVerify(exactly = 0) { mockTracker.recordAppLaunch(eq(imePackage), any()) }
    }

    /**
     * Verifies that the block overlay is shown with onReturnHome wired to performGlobalAction(GLOBAL_ACTION_HOME),
     * and when canDrawOverlays is true, GLOBAL_ACTION_HOME is NOT called prematurely to avoid race condition
     * where the launcher dismisses the overlay.
     */
    @Test
    fun testBlockShowsOverlayWithReturnHomeAction() {
        val blockedPackage = "com.netflix.mediaclient"
        service.cachedAppLimits = mapOf(
            blockedPackage to AppLimitSnapshot(
                packageName = blockedPackage,
                appName = "Netflix",
                isBlocked = true
            )
        )
        every { service.performGlobalAction(any()) } returns true

        service.handleWindowEvent(blockedPackage, className = null, windowTexts = emptyList())

        // Overlay is shown
        verify(atLeast = 1) {
            mockOverlayManager.showOverlay(
                match { it is BlockReason.ManuallyBlocked },
                any()
            )
        }
        // GLOBAL_ACTION_HOME should NOT be called immediately while overlay is showing (avoids race condition)
        verify(exactly = 0) { service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME) }
    }
}
