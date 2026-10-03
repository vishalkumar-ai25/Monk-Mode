package com.stayfocused.app.domain

import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsTamperInspectorTest {

    private lateinit var inspector: SettingsTamperInspector

    @Before
    fun setUp() {
        inspector = SettingsTamperInspector()
    }

    @Test
    fun testNormalSettingsAllowedDuringStrictMode() {
        val decision = inspector.evaluate(
            packageName = "com.android.settings",
            className = "com.android.settings.wifi.WifiSettings",
            windowTexts = listOf("Wi-Fi", "Connected", "Add network"),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue(decision is SettingsTamperInspector.TamperDecision.Allow)
    }

    @Test
    fun testOtherAppInfoAllowedDuringStrictMode() {
        val decision = inspector.evaluate(
            packageName = "com.android.settings",
            className = "com.android.settings.applications.InstalledAppDetails",
            windowTexts = listOf("YouTube", "Uninstall", "Storage & cache", "Force stop"),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue(decision is SettingsTamperInspector.TamperDecision.Allow)
    }

    @Test
    fun testMonkModeAppInfoBlockedDuringStrictMode() {
        val decision = inspector.evaluate(
            packageName = "com.android.settings",
            className = "com.android.settings.applications.InstalledAppDetails",
            windowTexts = listOf("Monk Mode", "Uninstall", "Force stop", "Storage"),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("Must block Monk Mode App Info", decision is SettingsTamperInspector.TamperDecision.BlockTamper)
    }

    @Test
    fun testMonkModeAppInfoBlockedInOemSettings() {
        val decision = inspector.evaluate(
            packageName = "com.coloros.settings",
            className = "com.coloros.settings.feature.appmanager.AppDetailsActivity",
            windowTexts = listOf("Stay Focused", "com.stayfocused.app", "Desinstalar"),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("Must block in ColorOS settings regardless of language", decision is SettingsTamperInspector.TamperDecision.BlockTamper)
    }

    @Test
    fun testDeviceAdminDeactivationBlockedDuringStrictMode() {
        val decision = inspector.evaluate(
            packageName = "com.android.settings",
            className = "com.android.settings.DeviceAdminAddActivity",
            windowTexts = listOf("Monk Mode", "Deactivate this device admin app"),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("Must block Device Admin deactivation for Monk Mode", decision is SettingsTamperInspector.TamperDecision.BlockTamper)
    }

    @Test
    fun testAccessibilityTamperingBlockedDuringStrictMode() {
        val decision = inspector.evaluate(
            packageName = "com.android.settings",
            className = "com.android.settings.accessibility.AccessibilitySettings",
            windowTexts = listOf("Monk Mode", "Use service", "Turn off"),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("Must block Accessibility toggling for Monk Mode", decision is SettingsTamperInspector.TamperDecision.BlockTamper)
    }

    @Test
    fun testPackageInstallerUninstallationBlockedDuringStrictMode() {
        val decision = inspector.evaluate(
            packageName = "com.google.android.packageinstaller",
            className = "com.android.packageinstaller.UninstallerActivity",
            windowTexts = listOf("Do you want to uninstall Monk Mode?", "Cancel", "OK"),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("Must block Package Installer uninstallation", decision is SettingsTamperInspector.TamperDecision.BlockTamper)
    }

    @Test
    fun testPlayStoreUninstallationBlockedDuringStrictMode() {
        val decision = inspector.evaluate(
            packageName = "com.android.vending",
            className = "com.google.android.finsky.activities.AppDetailsActivity",
            windowTexts = listOf("Monk Mode", "Uninstall", "Open"),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("Must block Play Store uninstallation", decision is SettingsTamperInspector.TamperDecision.BlockTamper)
    }

    @Test
    fun testAllowedWhenStrictModeInactive() {
        val decision = inspector.evaluate(
            packageName = "com.android.settings",
            className = "com.android.settings.applications.InstalledAppDetails",
            windowTexts = listOf("Monk Mode", "Uninstall", "Force stop"),
            isStrictModeActive = false,
            antiTamperEnabled = true
        )
        assertTrue("Must allow when strict mode is not active", decision is SettingsTamperInspector.TamperDecision.Allow)
    }

    @Test
    fun testAllowedWhenBootGracePeriodActive() {
        val decision = inspector.evaluate(
            packageName = "com.android.settings",
            className = "com.android.settings.applications.InstalledAppDetails",
            windowTexts = listOf("Monk Mode", "Uninstall", "Force stop"),
            isStrictModeActive = true,
            isGracePeriodActive = true,
            antiTamperEnabled = true
        )
        assertTrue("Must allow during boot grace period for emergency recovery", decision is SettingsTamperInspector.TamperDecision.Allow)
    }

    @Test
    fun testUnrelatedAppUninstallationAllowedInPackageInstaller() {
        val decision = inspector.evaluate(
            packageName = "com.google.android.packageinstaller",
            className = "com.android.packageinstaller.UninstallerActivity",
            windowTexts = listOf("Do you want to uninstall Spotify?", "Cancel", "OK"),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("Unrelated app uninstallation must be allowed during Strict Mode", decision is SettingsTamperInspector.TamperDecision.Allow)
    }
}
