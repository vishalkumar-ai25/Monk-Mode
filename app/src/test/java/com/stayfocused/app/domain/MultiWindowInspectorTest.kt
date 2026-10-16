package com.stayfocused.app.domain

import com.stayfocused.app.domain.model.BlockReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiWindowInspectorTest {

    private val blockedPackages = setOf("com.google.android.youtube", "com.instagram.android")
    private val inspector = MultiWindowInspector { pkg ->
        if (blockedPackages.contains(pkg.lowercase())) {
            BlockReason.ManuallyBlocked(pkg)
        } else {
            null
        }
    }

    @Test
    fun `single visible application window is always allowed`() {
        val windows = listOf(
            WindowSnapshot(
                id = 1,
                packageName = "com.google.android.youtube",
                isFocused = true,
                isActive = true,
                isApplication = true,
                isInPictureInPicture = false
            )
        )
        val decision = inspector.evaluate(windows)
        assertEquals(MultiWindowDecision.Allow, decision)
    }

    @Test
    fun `split screen with two allowed apps is allowed`() {
        val windows = listOf(
            WindowSnapshot(
                id = 1,
                packageName = "com.stayfocused.app",
                isFocused = true,
                isActive = true,
                isApplication = true,
                isInPictureInPicture = false
            ),
            WindowSnapshot(
                id = 2,
                packageName = "com.android.calculator2",
                isFocused = false,
                isActive = true,
                isApplication = true,
                isInPictureInPicture = false
            )
        )
        val decision = inspector.evaluate(windows)
        assertEquals(MultiWindowDecision.Allow, decision)
    }

    @Test
    fun `split screen with allowed app and blocked app evicts blocked window`() {
        val windows = listOf(
            WindowSnapshot(
                id = 1,
                packageName = "com.stayfocused.app",
                isFocused = true,
                isActive = true,
                isApplication = true,
                isInPictureInPicture = false
            ),
            WindowSnapshot(
                id = 2,
                packageName = "com.google.android.youtube",
                isFocused = false,
                isActive = true,
                isApplication = true,
                isInPictureInPicture = false
            )
        )
        val decision = inspector.evaluate(windows)
        assertTrue(decision is MultiWindowDecision.EvictBlockedWindow)
        val evict = decision as MultiWindowDecision.EvictBlockedWindow
        assertEquals("com.google.android.youtube", evict.packageName)
        assertEquals(false, evict.isPictureInPicture)
    }

    @Test
    fun `picture-in-picture window for blocked app triggers PiP eviction`() {
        val windows = listOf(
            WindowSnapshot(
                id = 1,
                packageName = "com.android.settings",
                isFocused = true,
                isActive = true,
                isApplication = true,
                isInPictureInPicture = false
            ),
            WindowSnapshot(
                id = 2,
                packageName = "com.google.android.youtube",
                isFocused = false,
                isActive = true,
                isApplication = true,
                isInPictureInPicture = true
            )
        )
        val decision = inspector.evaluate(windows)
        assertTrue(decision is MultiWindowDecision.EvictBlockedWindow)
        val evict = decision as MultiWindowDecision.EvictBlockedWindow
        assertEquals("com.google.android.youtube", evict.packageName)
        assertEquals(true, evict.isPictureInPicture)
    }

    @Test
    fun `non-application windows do not count towards app window limits`() {
        val windows = listOf(
            WindowSnapshot(
                id = 1,
                packageName = "com.android.calculator2",
                isFocused = true,
                isActive = true,
                isApplication = true,
                isInPictureInPicture = false
            ),
            WindowSnapshot(
                id = 2,
                packageName = "com.android.systemui",
                isFocused = false,
                isActive = true,
                isApplication = false, // System UI navigation / status bar
                isInPictureInPicture = false
            )
        )
        val decision = inspector.evaluate(windows)
        assertEquals(MultiWindowDecision.Allow, decision)
    }

    @Test
    fun `system UI and self package are never evicted`() {
        val windows = listOf(
            WindowSnapshot(
                id = 1,
                packageName = "com.stayfocused.app",
                isFocused = true,
                isActive = true,
                isApplication = true,
                isInPictureInPicture = false
            ),
            WindowSnapshot(
                id = 2,
                packageName = "com.android.systemui",
                isFocused = false,
                isActive = true,
                isApplication = true,
                isInPictureInPicture = false
            )
        )
        val decision = inspector.evaluate(windows)
        assertEquals(MultiWindowDecision.Allow, decision)
    }
}
