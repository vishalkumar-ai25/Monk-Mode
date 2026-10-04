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

    // ── Allow-List Tests ───────────────────────────────────────────────────────

    @Test
    fun testWifiSettingsAllowedDuringStrictMode() {
        val decision = inspector.evaluate(
            packageName = "com.android.settings",
            className = "com.android.settings.wifi.WifiSettings",
            windowTexts = emptyList(),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("Wi-Fi settings must be allowed", decision is SettingsTamperInspector.TamperDecision.Allow)
    }

    @Test
    fun testNetworkProviderSettingsAllowedDuringStrictMode() {
        val decision = inspector.evaluate(
            packageName = "com.android.settings",
            className = "com.android.settings.network.NetworkProviderSettings",
            windowTexts = emptyList(),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("Android 12+ Network Provider settings must be allowed", decision is SettingsTamperInspector.TamperDecision.Allow)
    }

    @Test
    fun testBluetoothSettingsAllowedDuringStrictMode() {
        val decision = inspector.evaluate(
            packageName = "com.android.settings",
            className = "com.android.settings.bluetooth.BluetoothSettings",
            windowTexts = emptyList(),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("Bluetooth settings must be allowed", decision is SettingsTamperInspector.TamperDecision.Allow)
    }

    @Test
    fun testDisplaySettingsAllowedDuringStrictMode() {
        val decision = inspector.evaluate(
            packageName = "com.android.settings",
            className = "com.android.settings.DisplaySettings",
            windowTexts = emptyList(),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("Display settings must be allowed", decision is SettingsTamperInspector.TamperDecision.Allow)
    }

    @Test
    fun testSoundSettingsAllowedDuringStrictMode() {
        val decision = inspector.evaluate(
            packageName = "com.android.settings",
            className = "com.android.settings.sound.SoundSettings",
            windowTexts = emptyList(),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("Sound settings must be allowed", decision is SettingsTamperInspector.TamperDecision.Allow)
    }

    @Test
    fun testVpnConfirmDialogAllowedDuringStrictMode() {
        val decision = inspector.evaluate(
            packageName = "com.android.vpndialogs",
            className = "com.android.vpndialogs.ConfirmDialog",
            windowTexts = listOf("Monk Mode wants to set up a VPN connection"),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("Monk Mode VPN ConfirmDialog must be allowed", decision is SettingsTamperInspector.TamperDecision.Allow)
    }

    // ── 8 Block Categories by Class Name / Package Alone (Zero Text Match) ───────

    @Test
    fun testAppDetailsBlockedByClassNameAlone() {
        val decision = inspector.evaluate(
            packageName = "com.android.settings",
            className = "com.android.settings.applications.InstalledAppDetails",
            windowTexts = emptyList(),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("App details must be blocked by class name alone", decision is SettingsTamperInspector.TamperDecision.BlockTamper)
    }

    @Test
    fun testOemAppDetailsBlockedByClassNameAlone() {
        val decision = inspector.evaluate(
            packageName = "com.coloros.settings",
            className = "com.coloros.settings.feature.appmanager.AppDetailsActivity",
            windowTexts = emptyList(),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("ColorOS AppDetails must be blocked by class name alone", decision is SettingsTamperInspector.TamperDecision.BlockTamper)
    }

    @Test
    fun testDeviceAdminBlockedByClassNameAlone() {
        val decision = inspector.evaluate(
            packageName = "com.android.settings",
            className = "com.android.settings.DeviceAdminAddActivity",
            windowTexts = emptyList(),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("Device Admin must be blocked by class name alone", decision is SettingsTamperInspector.TamperDecision.BlockTamper)
    }

    @Test
    fun testAccessibilityBlockedByClassNameAlone() {
        val decision = inspector.evaluate(
            packageName = "com.android.settings",
            className = "com.android.settings.accessibility.AccessibilitySettings",
            windowTexts = emptyList(),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("Accessibility must be blocked by class name alone", decision is SettingsTamperInspector.TamperDecision.BlockTamper)
    }

    @Test
    fun testSpecialAccessBlockedByClassNameAlone() {
        val decision = inspector.evaluate(
            packageName = "com.android.settings",
            className = "com.android.settings.specialaccess.SpecialAccessSettings",
            windowTexts = emptyList(),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("Special access must be blocked by class name alone", decision is SettingsTamperInspector.TamperDecision.BlockTamper)
    }

    @Test
    fun testDrawOverlayBlockedEvenIfDisplayInName() {
        val decision = inspector.evaluate(
            packageName = "com.android.settings",
            className = "com.android.settings.display.DrawOverlayDetails",
            windowTexts = emptyList(),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("Draw overlay must not be whitelisted by display pattern", decision is SettingsTamperInspector.TamperDecision.BlockTamper)
    }

    @Test
    fun testDateTimeBlockedByClassNameAlone() {
        val decision = inspector.evaluate(
            packageName = "com.android.settings",
            className = "com.android.settings.datetime.DateTimeSettings",
            windowTexts = emptyList(),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("Date & Time must be blocked by class name alone", decision is SettingsTamperInspector.TamperDecision.BlockTamper)
    }

    @Test
    fun testVpnSettingsBlockedByClassNameAlone() {
        val decision = inspector.evaluate(
            packageName = "com.android.settings",
            className = "com.android.settings.vpn2.VpnSettings",
            windowTexts = emptyList(),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("VPN settings must be blocked by class name alone", decision is SettingsTamperInspector.TamperDecision.BlockTamper)
    }

    @Test
    fun testVpnDialogsNonConfirmBlocked() {
        val decision = inspector.evaluate(
            packageName = "com.android.vpndialogs",
            className = "com.android.vpndialogs.ManageDialog",
            windowTexts = emptyList(),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("Non-confirm VPN dialogs must be blocked", decision is SettingsTamperInspector.TamperDecision.BlockTamper)
    }

    @Test
    fun testPrivateDnsBlockedByClassNameAlone() {
        val decision = inspector.evaluate(
            packageName = "com.android.settings",
            className = "com.android.settings.network.PrivateDnsSettings",
            windowTexts = emptyList(),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("Private DNS must be blocked by class name alone", decision is SettingsTamperInspector.TamperDecision.BlockTamper)
    }

    @Test
    fun testPackageInstallerUninstallationBlockedByClassNameAlone() {
        val decision = inspector.evaluate(
            packageName = "com.google.android.packageinstaller",
            className = "com.android.packageinstaller.UninstallerActivity",
            windowTexts = emptyList(),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("Package installer uninstall must be blocked by class name alone", decision is SettingsTamperInspector.TamperDecision.BlockTamper)
    }

    @Test
    fun testPermissionControllerUninstallationBlockedByClassNameAlone() {
        val decision = inspector.evaluate(
            packageName = "com.android.permissioncontroller",
            className = "com.android.permissioncontroller.permission.ui.UninstallerActivity",
            windowTexts = emptyList(),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("PermissionController uninstallation must be blocked by class name alone", decision is SettingsTamperInspector.TamperDecision.BlockTamper)
    }

    @Test
    fun testPlayStoreUninstallationBlocked() {
        val decision = inspector.evaluate(
            packageName = "com.android.vending",
            className = "com.google.android.finsky.activities.AppDetailsActivity",
            windowTexts = listOf("Monk Mode", "Uninstall"),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("Play Store uninstallation targeting Monk Mode must be blocked", decision is SettingsTamperInspector.TamperDecision.BlockTamper)
    }

    // ── Text Fallback for Generic Containers (SubSettings) ─────────────────────

    @Test
    fun testSubSettingsBlockedWhenReferencingSelf() {
        val decision = inspector.evaluate(
            packageName = "com.android.settings",
            className = "com.android.settings.SubSettings",
            windowTexts = listOf("Monk Mode", "Storage & cache"),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("SubSettings referencing Monk Mode must be blocked", decision is SettingsTamperInspector.TamperDecision.BlockTamper)
    }

    @Test
    fun testSubSettingsBlockedWhenDangerousActionDetected() {
        val decision = inspector.evaluate(
            packageName = "com.android.settings",
            className = "com.android.settings.SubSettings",
            windowTexts = listOf("Force stop", "Uninstall"),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("SubSettings with force stop action must be blocked", decision is SettingsTamperInspector.TamperDecision.BlockTamper)
    }

    @Test
    fun testSubSettingsBlockedWhenDangerousCategoryDetected() {
        val decision = inspector.evaluate(
            packageName = "com.android.settings",
            className = "com.android.settings.SubSettings",
            windowTexts = listOf("Date & time", "Set time automatically"),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("SubSettings with Date & time header must be blocked", decision is SettingsTamperInspector.TamperDecision.BlockTamper)
    }

    @Test
    fun testSubSettingsAllowedWhenGenericAndSafe() {
        val decision = inspector.evaluate(
            packageName = "com.android.settings",
            className = "com.android.settings.SubSettings",
            windowTexts = listOf("System navigation", "Gestures"),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("SubSettings with safe content must be allowed", decision is SettingsTamperInspector.TamperDecision.Allow)
    }

    @Test
    fun testColorOsSafeCenterAppDetailsBlocked() {
        val decision = inspector.evaluate(
            packageName = "com.coloros.safecenter",
            className = "com.coloros.safecenter.sysapp.AppInfoActivity",
            windowTexts = emptyList(),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("ColorOS SafeCenter AppInfo must be blocked by class name alone", decision is SettingsTamperInspector.TamperDecision.BlockTamper)
    }

    @Test
    fun testOplusSafeCenterReferencingSelfBlocked() {
        val decision = inspector.evaluate(
            packageName = "com.oplus.safecenter",
            className = "com.oplus.safecenter.GenericActivity",
            windowTexts = listOf("Monk Mode", "Clear data"),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("Oplus SafeCenter targeting Monk Mode must be blocked", decision is SettingsTamperInspector.TamperDecision.BlockTamper)
    }

    @Test
    fun testTurnOffActionBlocked() {
        val decision = inspector.evaluate(
            packageName = "com.android.settings",
            className = "com.android.settings.SubSettings",
            windowTexts = listOf("Accessibility", "Turn off service", "Turn off"),
            isStrictModeActive = true,
            antiTamperEnabled = true
        )
        assertTrue("SubSettings with turn off action must be blocked", decision is SettingsTamperInspector.TamperDecision.BlockTamper)
    }

    // ── Invariant Tests ────────────────────────────────────────────────────────

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
        assertTrue("Must allow during boot grace period for recovery", decision is SettingsTamperInspector.TamperDecision.Allow)
    }
}
