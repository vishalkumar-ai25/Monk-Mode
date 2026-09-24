package com.stayfocused.app.data.registry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PackageRegistryTest {

    private lateinit var registry: PackageRegistry

    @Before
    fun setUp() {
        registry = PackageRegistry.createDefault()
    }

    @Test
    fun testRecognizedBrowsers() {
        // Common browsers
        assertTrue("Chrome must be recognized", registry.isBrowser("com.android.chrome"))
        assertTrue("Firefox must be recognized", registry.isBrowser("org.mozilla.firefox"))
        assertTrue("Brave must be recognized", registry.isBrowser("com.brave.browser"))
        assertTrue("Edge must be recognized", registry.isBrowser("com.microsoft.emmx"))
        assertTrue("Samsung Internet must be recognized", registry.isBrowser("com.sec.android.app.sbrowser"))
        assertTrue("DuckDuckGo must be recognized", registry.isBrowser("com.duckduckgo.mobile.android"))
        assertTrue("Opera must be recognized", registry.isBrowser("com.opera.browser"))

        // Non-browsers
        assertFalse("Instagram is not a browser", registry.isBrowser("com.instagram.android"))
        assertFalse("Settings is not a browser", registry.isBrowser("com.android.settings"))
        assertFalse("YouTube is not a browser", registry.isBrowser("com.google.android.youtube"))
    }

    @Test
    fun testRecognizedSettingsAndInstallerPackages() {
        // Stock/AOSP/Pixel settings
        assertTrue("Stock Settings must be recognized", registry.isSettingsOrInstaller("com.android.settings"))
        assertTrue("Package Installer must be recognized", registry.isSettingsOrInstaller("com.android.packageinstaller"))
        assertTrue("Google Package Installer must be recognized", registry.isSettingsOrInstaller("com.google.android.packageinstaller"))

        // OEM Skins
        assertTrue("Samsung Settings must be recognized", registry.isSettingsOrInstaller("com.samsung.android.settings"))
        assertTrue("MIUI Security Center must be recognized", registry.isSettingsOrInstaller("com.miui.securitycenter"))
        assertTrue("ColorOS / Oppo Safe Center must be recognized", registry.isSettingsOrInstaller("com.coloros.safecenter"))
        assertTrue("Vivo Permission Manager must be recognized", registry.isSettingsOrInstaller("com.vivo.permissionmanager"))

        // Regular apps
        assertFalse("Chrome is not settings", registry.isSettingsOrInstaller("com.android.chrome"))
        assertFalse("WhatsApp is not settings", registry.isSettingsOrInstaller("com.whatsapp"))
    }

    @Test
    fun testJsonDeserialization() {
        val sampleJson = """
            {
              "browserPackages": ["custom.browser.app"],
              "settingsPackages": ["custom.settings.app"]
            }
        """.trimIndent()

        val customRegistry = PackageRegistry.fromJson(sampleJson)
        assertTrue(customRegistry.isBrowser("custom.browser.app"))
        assertFalse(customRegistry.isBrowser("com.android.chrome"))
        assertTrue(customRegistry.isSettingsOrInstaller("custom.settings.app"))
        assertFalse(customRegistry.isSettingsOrInstaller("com.android.settings"))
    }

    @Test
    fun testRuntimeRegistration() {
        assertFalse(registry.isBrowser("com.new.browser"))
        registry.registerBrowser("com.new.browser")
        assertTrue(registry.isBrowser("com.new.browser"))

        assertFalse(registry.isSettingsOrInstaller("com.new.oem.settings"))
        registry.registerSettingsPackage("com.new.oem.settings")
        assertTrue(registry.isSettingsOrInstaller("com.new.oem.settings"))
    }
}
