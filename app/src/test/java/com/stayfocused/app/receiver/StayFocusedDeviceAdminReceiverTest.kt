package com.stayfocused.app.receiver

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StayFocusedDeviceAdminReceiverTest {

    @Test
    fun testComponentNameMatchesReceiver() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val component = StayFocusedDeviceAdminReceiver.getComponentName(context)

        assertEquals(context.packageName, component.packageName)
        assertEquals(StayFocusedDeviceAdminReceiver::class.java.name, component.className)
    }

    @Test
    fun testOnDisableRequestedWithAntiTamperEnabledReturnsWarning() {
        val receiver = StayFocusedDeviceAdminReceiver()
        val warning = receiver.getDisableWarning(antiTamperEnabled = true)
        assertNotNull("Warning message must be returned when anti-tamper is enabled", warning)
        assertTrue(warning.toString().contains("Stay Focused", ignoreCase = true))
    }

    @Test
    fun testOnDisableRequestedWithAntiTamperDisabledReturnsNull() {
        val receiver = StayFocusedDeviceAdminReceiver()
        val warning = receiver.getDisableWarning(antiTamperEnabled = false)
        assertNull("No warning should be returned when anti-tamper is disabled (debug mode)", warning)
    }

    @Test
    fun testCreateAddDeviceAdminIntent() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = StayFocusedDeviceAdminReceiver.createAddDeviceAdminIntent(context)

        assertEquals(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN, intent.action)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        val component = intent.getParcelableExtra<ComponentName>(DevicePolicyManager.EXTRA_DEVICE_ADMIN)
        assertEquals(StayFocusedDeviceAdminReceiver.getComponentName(context), component)
        assertNotNull(intent.getStringExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION))
    }

    @Test
    fun testIsDeviceAdminActiveReturnsFalseInitiallyInRobolectric() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val isActive = StayFocusedDeviceAdminReceiver.isDeviceAdminActive(context)
        assertEquals(false, isActive)
    }

    @Test
    fun testOnDisableRequestedWhenStrictActiveCachedIsTrueReturnsWarning() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        try {
            StayFocusedDeviceAdminReceiver.setStrictActiveCached(true)
            val receiver = StayFocusedDeviceAdminReceiver()
            val warning = receiver.onDisableRequested(context, Intent())

            assertNotNull("Warning message must be returned when strict mode is cached active", warning)
            assertTrue(warning.toString().contains("Strict Mode", ignoreCase = true))
        } finally {
            StayFocusedDeviceAdminReceiver.resetStrictActiveForTesting(context)
        }
    }

    @Test
    fun testOnDisableRequestedWhenStrictPersistedInPrefsReturnsWarning() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        try {
            StayFocusedDeviceAdminReceiver.resetStrictActiveForTesting(context)
            // Persist true to SharedPreferences while in-memory cached flag is false
            context.getSharedPreferences(StayFocusedDeviceAdminReceiver.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(StayFocusedDeviceAdminReceiver.KEY_STRICT_ACTIVE, true)
                .commit()

            val receiver = StayFocusedDeviceAdminReceiver()
            val warning = receiver.onDisableRequested(context, Intent())

            assertNotNull("Warning message must be returned when strict mode is active in SharedPreferences", warning)
            assertTrue(warning.toString().contains("Strict Mode", ignoreCase = true))
        } finally {
            StayFocusedDeviceAdminReceiver.resetStrictActiveForTesting(context)
        }
    }

    @Test
    fun testOnDisableRequestedWhenStrictInactiveReturnsNull() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        try {
            StayFocusedDeviceAdminReceiver.resetStrictActiveForTesting(context)
            val receiver = StayFocusedDeviceAdminReceiver()
            val warning = receiver.onDisableRequested(context, Intent())

            assertNull("No warning message when strict mode is inactive", warning)
        } finally {
            StayFocusedDeviceAdminReceiver.resetStrictActiveForTesting(context)
        }
    }

    @Test
    fun testOnDisableRequestedWhenStrictSessionHasNaturallyExpiredReturnsNull() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        try {
            // Armed with an end time in the past (expired session)
            val pastEndTime = System.currentTimeMillis() - 5000L
            StayFocusedDeviceAdminReceiver.setStrictActiveCached(true, pastEndTime, context)

            val receiver = StayFocusedDeviceAdminReceiver()
            val warning = receiver.onDisableRequested(context, Intent())

            assertNull("Expired strict session must return null and clear stale cache", warning)
            assertEquals(false, StayFocusedDeviceAdminReceiver.isStrictActive(context))
        } finally {
            StayFocusedDeviceAdminReceiver.resetStrictActiveForTesting(context)
        }
    }

    @Test
    fun testOnDisableRequestedWhenStrictActiveWithFutureEndTimeReturnsWarning() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        try {
            // Armed with an end time in the future
            val futureEndTime = System.currentTimeMillis() + 3600000L
            StayFocusedDeviceAdminReceiver.setStrictActiveCached(true, futureEndTime, context)

            val receiver = StayFocusedDeviceAdminReceiver()
            val warning = receiver.onDisableRequested(context, Intent())

            assertNotNull("Active strict session must return warning", warning)
            assertTrue(warning.toString().contains("Strict Mode", ignoreCase = true))
        } finally {
            StayFocusedDeviceAdminReceiver.resetStrictActiveForTesting(context)
        }
    }
}


