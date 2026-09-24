package com.stayfocused.app.ui.onboarding

import android.content.Intent
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrivateDnsNoticeTest {

    @Test
    fun testSettingsIntentTargetingWirelessOrNetworkSettings() {
        val intent = PrivateDnsNoticeHelper.createPrivateDnsSettingsIntent()
        assertNotNull(intent)
        assertTrue(
            "Intent action should target network/wireless settings",
            intent.action == Settings.ACTION_WIRELESS_SETTINGS ||
                intent.action == "android.settings.NETWORK_PROVIDER_SETTINGS"
        )
        assertTrue(
            "Intent should include FLAG_ACTIVITY_NEW_TASK",
            (intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0
        )
    }

    @Test
    fun testInstructionStepsContainRequiredGuidance() {
        val steps = PrivateDnsNoticeHelper.getSetupInstructions()
        assertTrue("Setup instructions must contain multiple steps", steps.size >= 3)
        assertTrue("Instructions must mention Settings", steps.any { it.contains("Settings", ignoreCase = true) })
        assertTrue("Instructions must mention Private DNS", steps.any { it.contains("Private DNS", ignoreCase = true) })
        assertTrue("Instructions must instruct to select Off", steps.any { it.contains("Off", ignoreCase = true) })
    }
}
