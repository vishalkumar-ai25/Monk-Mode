package com.stayfocused.app.service

import android.content.Context
import android.view.accessibility.AccessibilityEvent
import androidx.test.core.app.ApplicationProvider
import com.stayfocused.app.data.registry.PackageRegistry
import com.stayfocused.app.domain.InterceptionDecisionEngine
import com.stayfocused.app.domain.model.AppLimitSnapshot
import com.stayfocused.app.domain.model.BlockReason
import com.stayfocused.app.domain.model.FocusProfileRule
import com.stayfocused.app.domain.model.LimitType
import com.stayfocused.app.ui.overlay.BlockOverlayManager
import io.mockk.clearMocks
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
}
