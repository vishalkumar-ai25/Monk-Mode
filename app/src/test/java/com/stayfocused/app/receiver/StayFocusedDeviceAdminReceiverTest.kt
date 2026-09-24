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
}
