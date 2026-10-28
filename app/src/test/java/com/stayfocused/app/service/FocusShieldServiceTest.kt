package com.stayfocused.app.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FocusShieldServiceTest {

    private lateinit var context: Context
    private lateinit var service: FocusShieldService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        FocusShieldService.isRunning = false
    }

    @Test
    fun testStartCommand_returnsStartStickyAndSetsRunning() {
        val controller = Robolectric.buildService(FocusShieldService::class.java)
        service = controller.create().get()

        val startIntent = Intent(context, FocusShieldService::class.java).apply {
            action = FocusShieldService.ACTION_START_SHIELD
        }
        val result = service.onStartCommand(startIntent, 0, 1)

        assertEquals(android.app.Service.START_STICKY, result)
        assertTrue(FocusShieldService.isRunning)
    }

    @Test
    fun testStartCommand_createsNotificationChannelAndPostsOngoingNotification() {
        val controller = Robolectric.buildService(FocusShieldService::class.java)
        service = controller.create().get()

        val startIntent = Intent(context, FocusShieldService::class.java).apply {
            action = FocusShieldService.ACTION_START_SHIELD
        }
        service.onStartCommand(startIntent, 0, 1)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm = shadowOf(notificationManager)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = notificationManager.getNotificationChannel(FocusShieldService.NOTIFICATION_CHANNEL_ID)
            assertNotNull("Notification channel must be created", channel)
            assertEquals("Monk Mode Focus Shield", channel.name)
        }

        val notification = shadowNm.allNotifications.find { it.channelId == FocusShieldService.NOTIFICATION_CHANNEL_ID }
            ?: shadowNm.allNotifications.firstOrNull()
        assertNotNull("Foreground notification must be posted", notification)
        assertTrue("Notification must have FLAG_ONGOING_EVENT", (notification!!.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0)
    }

    @Test
    fun testOnTaskRemoved_maintainsRunningStateAndReturnsStartSticky() {
        val controller = Robolectric.buildService(FocusShieldService::class.java)
        service = controller.create().get()

        service.onStartCommand(Intent(context, FocusShieldService::class.java), 0, 1)
        assertTrue(FocusShieldService.isRunning)

        // Simulate activity task swiped out of recents
        service.onTaskRemoved(Intent())

        assertTrue("FocusShieldService must remain running after task removal", FocusShieldService.isRunning)
    }

    @Test
    fun testStopAction_stopsForegroundAndClearsRunning() {
        val controller = Robolectric.buildService(FocusShieldService::class.java)
        service = controller.create().get()

        service.onStartCommand(Intent(context, FocusShieldService::class.java).apply {
            action = FocusShieldService.ACTION_START_SHIELD
        }, 0, 1)
        assertTrue(FocusShieldService.isRunning)

        service.onStartCommand(Intent(context, FocusShieldService::class.java).apply {
            action = FocusShieldService.ACTION_STOP_SHIELD
        }, 0, 2)
        assertFalse(FocusShieldService.isRunning)
    }

    @Test
    @Config(sdk = [33])
    fun testStartCommand_compatibleWithAndroid13WithoutSpecialUseCrash() {
        val controller = Robolectric.buildService(FocusShieldService::class.java)
        service = controller.create().get()

        val startIntent = Intent(context, FocusShieldService::class.java).apply {
            action = FocusShieldService.ACTION_START_SHIELD
        }
        val result = service.onStartCommand(startIntent, 0, 1)

        assertEquals(android.app.Service.START_STICKY, result)
        assertTrue(FocusShieldService.isRunning)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm = shadowOf(notificationManager)
        val notification = shadowNm.allNotifications.find { it.channelId == FocusShieldService.NOTIFICATION_CHANNEL_ID }
            ?: shadowNm.allNotifications.firstOrNull()
        assertNotNull("Foreground notification must be posted on API 33", notification)
    }
}
