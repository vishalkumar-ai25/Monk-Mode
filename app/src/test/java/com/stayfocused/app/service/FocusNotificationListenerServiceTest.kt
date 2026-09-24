package com.stayfocused.app.service

import android.app.Notification
import android.service.notification.StatusBarNotification
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FocusNotificationListenerServiceTest {

    private class TestableFocusNotificationListenerService : FocusNotificationListenerService() {
        val canceledKeys = mutableListOf<String>()

        override fun cancelNotificationByKey(key: String) {
            canceledKeys.add(key)
        }
    }

    private lateinit var service: TestableFocusNotificationListenerService

    @Before
    fun setUp() {
        val controller = Robolectric.buildService(TestableFocusNotificationListenerService::class.java)
        service = controller.get()
        service.autoObserveDatabase = false
        controller.create()
    }

    @After
    fun tearDown() {
        service.onDestroy()
    }

    @Test
    fun testDistractingNotificationFromBlockedAppIsCanceled() {
        service.cachedBlockedPackages = setOf("com.instagram.android")
        service.isBreakActive = false

        val mockSbn = mockk<StatusBarNotification>()
        val notification = Notification().apply {
            flags = 0
            category = null
        }
        every { mockSbn.packageName } returns "com.instagram.android"
        every { mockSbn.isOngoing } returns false
        every { mockSbn.key } returns "0|com.instagram.android|101|null|10001"
        every { mockSbn.postTime } returns 123456789L
        every { mockSbn.notification } returns notification

        service.onNotificationPosted(mockSbn)

        assertEquals(1, service.canceledKeys.size)
        assertEquals("0|com.instagram.android|101|null|10001", service.canceledKeys[0])
    }

    @Test
    fun testAllowedAppNotificationIsNotCanceled() {
        service.cachedBlockedPackages = setOf("com.instagram.android")
        service.isBreakActive = false

        val mockSbn = mockk<StatusBarNotification>()
        val notification = Notification().apply {
            flags = 0
            category = null
        }
        every { mockSbn.packageName } returns "com.slack"
        every { mockSbn.isOngoing } returns false
        every { mockSbn.key } returns "0|com.slack|202|null|10002"
        every { mockSbn.postTime } returns 123456789L
        every { mockSbn.notification } returns notification

        service.onNotificationPosted(mockSbn)

        assertTrue(service.canceledKeys.isEmpty())
    }

    @Test
    fun testOngoingNotificationFromBlockedAppIsNotCanceled() {
        service.cachedBlockedPackages = setOf("com.spotify.music")
        service.isBreakActive = false

        val mockSbn = mockk<StatusBarNotification>()
        val notification = Notification().apply {
            flags = Notification.FLAG_ONGOING_EVENT
            category = null
        }
        every { mockSbn.packageName } returns "com.spotify.music"
        every { mockSbn.isOngoing } returns true // Ongoing playback!
        every { mockSbn.key } returns "0|com.spotify.music|303|null|10003"
        every { mockSbn.postTime } returns 123456789L
        every { mockSbn.notification } returns notification

        service.onNotificationPosted(mockSbn)

        assertTrue(service.canceledKeys.isEmpty())
    }

    @Test
    fun testNotificationAllowedDuringActiveBreak() {
        service.cachedBlockedPackages = setOf("com.instagram.android")
        service.isBreakActive = true // User is taking an authorized break

        val mockSbn = mockk<StatusBarNotification>()
        val notification = Notification().apply {
            flags = 0
            category = null
        }
        every { mockSbn.packageName } returns "com.instagram.android"
        every { mockSbn.isOngoing } returns false
        every { mockSbn.key } returns "0|com.instagram.android|404|null|10004"
        every { mockSbn.postTime } returns 123456789L
        every { mockSbn.notification } returns notification

        service.onNotificationPosted(mockSbn)

        assertTrue(service.canceledKeys.isEmpty())
    }
}
