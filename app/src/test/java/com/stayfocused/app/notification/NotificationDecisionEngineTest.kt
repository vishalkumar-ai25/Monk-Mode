package com.stayfocused.app.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationDecisionEngineTest {

    private lateinit var engine: NotificationDecisionEngine

    @Before
    fun setUp() {
        engine = NotificationDecisionEngine()
    }

    @Test
    fun testSuppressesDistractingAppWhenBlocked() {
        val notification = NotificationInfo(
            packageName = "com.instagram.android",
            isOngoing = false,
            category = null,
            channelId = "general"
        )
        val result = engine.shouldSuppressNotification(
            notification = notification,
            isPackageBlocked = true,
            isBreakActive = false
        )
        assertEquals(NotificationInterceptionResult.SUPPRESS_DISTRACTION, result)
        assertTrue(result.shouldCancel)
    }

    @Test
    fun testAllowsDistractingAppWhenNotBlocked() {
        val notification = NotificationInfo(
            packageName = "com.instagram.android",
            isOngoing = false,
            category = null,
            channelId = "general"
        )
        val result = engine.shouldSuppressNotification(
            notification = notification,
            isPackageBlocked = false,
            isBreakActive = false
        )
        assertEquals(NotificationInterceptionResult.ALLOW_NOT_BLOCKED, result)
        assertFalse(result.shouldCancel)
    }

    @Test
    fun testAllowsOngoingNotificationEvenIfAppIsBlocked() {
        val notification = NotificationInfo(
            packageName = "com.spotify.music",
            isOngoing = true, // Media playback
            category = null,
            channelId = "playback"
        )
        val result = engine.shouldSuppressNotification(
            notification = notification,
            isPackageBlocked = true,
            isBreakActive = false
        )
        assertEquals(NotificationInterceptionResult.ALLOW_SAFETY_CRITICAL, result)
        assertFalse(result.shouldCancel)
    }

    @Test
    fun testAllowsPhoneCallCategoryRegardlessOfBlock() {
        val notification = NotificationInfo(
            packageName = "com.whatsapp",
            isOngoing = false,
            category = "call", // Incoming call
            channelId = "call_channel"
        )
        val result = engine.shouldSuppressNotification(
            notification = notification,
            isPackageBlocked = true,
            isBreakActive = false
        )
        assertEquals(NotificationInterceptionResult.ALLOW_SAFETY_CRITICAL, result)
        assertFalse(result.shouldCancel)
    }

    @Test
    fun testAllowsAlarmCategoryRegardlessOfBlock() {
        val notification = NotificationInfo(
            packageName = "com.google.android.deskclock",
            isOngoing = false,
            category = "alarm",
            channelId = "alarm_channel"
        )
        val result = engine.shouldSuppressNotification(
            notification = notification,
            isPackageBlocked = true,
            isBreakActive = false
        )
        assertEquals(NotificationInterceptionResult.ALLOW_SAFETY_CRITICAL, result)
        assertFalse(result.shouldCancel)
    }

    @Test
    fun testAllowsSystemUIPackages() {
        val systemPackages = listOf(
            "com.android.systemui",
            "com.android.phone",
            "com.google.android.dialer",
            "com.android.server.telecom"
        )

        for (pkg in systemPackages) {
            val notification = NotificationInfo(
                packageName = pkg,
                isOngoing = false,
                category = null,
                channelId = "system"
            )
            val result = engine.shouldSuppressNotification(
                notification = notification,
                isPackageBlocked = true, // even if marked blocked
                isBreakActive = false
            )
            assertEquals("Package $pkg should be ALLOW_SAFETY_CRITICAL", NotificationInterceptionResult.ALLOW_SAFETY_CRITICAL, result)
            assertFalse(result.shouldCancel)
        }
    }

    @Test
    fun testAllowsNotificationWhenBreakIsActive() {
        val notification = NotificationInfo(
            packageName = "com.instagram.android",
            isOngoing = false,
            category = null,
            channelId = "general"
        )
        val result = engine.shouldSuppressNotification(
            notification = notification,
            isPackageBlocked = true,
            isBreakActive = true // Break is active
        )
        assertEquals(NotificationInterceptionResult.ALLOW_BREAK_ACTIVE, result)
        assertFalse(result.shouldCancel)
    }
}
