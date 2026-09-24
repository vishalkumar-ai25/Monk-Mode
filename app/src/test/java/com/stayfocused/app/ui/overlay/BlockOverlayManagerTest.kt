package com.stayfocused.app.ui.overlay

import android.content.Context
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.stayfocused.app.domain.model.BlockReason
import com.stayfocused.app.domain.model.LimitType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BlockOverlayManagerTest {

    private lateinit var context: Context
    private lateinit var windowManager: WindowManager
    private lateinit var overlayManager: BlockOverlayManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    @After
    fun tearDown() {
        if (::overlayManager.isInitialized) {
            overlayManager.hideOverlay()
        }
    }

    @Test
    fun testOverlayNotShownWhenPermissionDenied() {
        overlayManager = BlockOverlayManager(
            context = context,
            windowManager = windowManager,
            permissionChecker = { false }
        )

        assertFalse("Permission should report false", overlayManager.canDrawOverlays())

        val reason = BlockReason.ManuallyBlocked("Instagram")
        overlayManager.showOverlay(reason)

        assertFalse("Overlay should not be showing when permission denied", overlayManager.isShowing)
    }

    @Test
    fun testOverlayShownAndHiddenWhenPermissionGranted() {
        overlayManager = BlockOverlayManager(
            context = context,
            windowManager = windowManager,
            permissionChecker = { true }
        )

        assertTrue("Permission should report true", overlayManager.canDrawOverlays())
        assertFalse("Initially overlay is not showing", overlayManager.isShowing)

        var returnHomeClicked = false
        val reason = BlockReason.LimitReached(
            appName = "Instagram",
            limitType = LimitType.TIME_LIMIT,
            used = 1800000L,
            limit = 1800000L
        )

        overlayManager.showOverlay(reason, onReturnHome = { returnHomeClicked = true })

        assertTrue("Overlay should be showing after showOverlay()", overlayManager.isShowing)
        assertNotNull("Current reason must be tracked", overlayManager.currentReason)
        assertEquals(reason, overlayManager.currentReason)

        // Trigger return home
        overlayManager.triggerReturnHome()
        assertTrue("onReturnHome callback must be called", returnHomeClicked)
        assertFalse("Overlay should be hidden after returning home", overlayManager.isShowing)
    }

    @Test
    fun testShowOverlayUpdatesReasonWhenAlreadyShowing() {
        overlayManager = BlockOverlayManager(
            context = context,
            windowManager = windowManager,
            permissionChecker = { true }
        )

        val reason1 = BlockReason.ManuallyBlocked("Twitter")
        val reason2 = BlockReason.ProfileActive("Deep Work", 101L)

        overlayManager.showOverlay(reason1)
        assertTrue(overlayManager.isShowing)
        assertEquals(reason1, overlayManager.currentReason)

        // Show second reason
        overlayManager.showOverlay(reason2)
        assertTrue(overlayManager.isShowing)
        assertEquals(reason2, overlayManager.currentReason)

        overlayManager.hideOverlay()
        assertFalse(overlayManager.isShowing)
    }

    @Test
    fun testQuotesListNotEmptyAndContainsDisciplineQuotes() {
        val quotes = BlockOverlayQuotes.QUOTES
        assertTrue("Quotes list should not be empty", quotes.isNotEmpty())
        assertTrue("Quotes should contain focus and discipline quotes", quotes.any { it.contains("focus", ignoreCase = true) })
    }
}
