package com.stayfocused.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PowerMenuInspectorTest {

    private val inspector = PowerMenuInspector()

    @Test
    fun `aosp power dialog with power off tokens is dismissed when strict and enabled`() {
        val decision = inspector.evaluate(
            packageName = "com.android.systemui",
            className = "com.android.systemui.globalactions.GlobalActionsDialog",
            windowTexts = listOf("Power off", "Restart", "Emergency"),
            isStrictActive = true,
            isBlockPowerOffEnabled = true
        )
        assertEquals(PowerMenuDecision.DismissPowerMenu, decision)
    }

    @Test
    fun `realme ui and coloros global actions dialog is dismissed`() {
        val decision = inspector.evaluate(
            packageName = "com.oplus.globalactions",
            className = "com.oplus.globalactions.GlobalActionsActivity",
            windowTexts = listOf("Restart", "Power off"),
            isStrictActive = true,
            isBlockPowerOffEnabled = true
        )
        assertEquals(PowerMenuDecision.DismissPowerMenu, decision)
    }

    @Test
    fun `samsung global actions dialog is dismissed`() {
        val decision = inspector.evaluate(
            packageName = "com.samsung.android.globalactions",
            className = "com.samsung.android.globalactions.presentation.view.GlobalActionsDialog",
            windowTexts = listOf("Power off", "Restart"),
            isStrictActive = true,
            isBlockPowerOffEnabled = true
        )
        assertEquals(PowerMenuDecision.DismissPowerMenu, decision)
    }

    @Test
    fun `emergency packages are strictly allowed and never dismissed`() {
        val emergencyPackages = listOf(
            "com.android.emergency",
            "com.google.android.apps.safetyhub",
            "com.android.phone",
            "com.coloros.emergency",
            "com.oplus.sos",
            "com.sec.android.app.safetyassurance"
        )

        for (pkg in emergencyPackages) {
            val decision = inspector.evaluate(
                packageName = pkg,
                className = "EmergencyDialerActivity",
                windowTexts = listOf("Emergency Call", "112", "911", "Medical info"),
                isStrictActive = true,
                isBlockPowerOffEnabled = true
            )
            assertEquals("Emergency package $pkg must never be blocked!", PowerMenuDecision.Allow, decision)
        }
    }

    @Test
    fun `power menu is allowed if strict mode is inactive`() {
        val decision = inspector.evaluate(
            packageName = "com.android.systemui",
            className = "GlobalActionsDialog",
            windowTexts = listOf("Power off", "Restart"),
            isStrictActive = false,
            isBlockPowerOffEnabled = true
        )
        assertEquals(PowerMenuDecision.Allow, decision)
    }

    @Test
    fun `power menu is allowed if block power off setting is disabled`() {
        val decision = inspector.evaluate(
            packageName = "com.android.systemui",
            className = "GlobalActionsDialog",
            windowTexts = listOf("Power off", "Restart"),
            isStrictActive = true,
            isBlockPowerOffEnabled = false
        )
        assertEquals(PowerMenuDecision.Allow, decision)
    }

    @Test
    fun `regular app screen is allowed even if it contains words like power`() {
        val decision = inspector.evaluate(
            packageName = "com.google.android.youtube",
            className = "com.google.android.apps.youtube.app.watchwhile.WatchWhileActivity",
            windowTexts = listOf("The Power of Habit - Book Summary"),
            isStrictActive = true,
            isBlockPowerOffEnabled = true
        )
        assertEquals(PowerMenuDecision.Allow, decision)
    }
}
