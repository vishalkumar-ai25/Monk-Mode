package com.stayfocused.app.oem

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OemSurvivalHelperTest {

    @Test
    fun testBatteryOptimizationIntentStructure() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = OemSurvivalHelper.createBatteryOptimizationIntent(context)

        assertNotNull(intent)
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, intent.action)
        assertEquals(Uri.parse("package:${context.packageName}"), intent.data)
        assertTrue(
            "Intent should include FLAG_ACTIVITY_NEW_TASK",
            (intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0
        )
    }

    @Test
    fun testOemAutostartRegistryContainsRealmeAndMajorOems() {
        val candidates = OemSurvivalHelper.getOemAutostartCandidates()
        assertTrue("Candidate registry must not be empty", candidates.isNotEmpty())

        val realmeOppoCandidates = candidates.filter { it.component?.packageName?.contains("coloros") == true || it.component?.packageName?.contains("oppo") == true }
        assertTrue("Registry must include ColorOS / realme UI candidates for Realme C65 5G", realmeOppoCandidates.isNotEmpty())

        val xiaomiCandidates = candidates.filter { it.component?.packageName?.contains("miui") == true }
        assertTrue("Registry must include MIUI/HyperOS candidates", xiaomiCandidates.isNotEmpty())

        val samsungCandidates = candidates.filter { it.component?.packageName?.contains("samsung") == true }
        assertTrue("Registry must include Samsung candidates", samsungCandidates.isNotEmpty())
    }
}
