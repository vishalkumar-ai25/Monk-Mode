package com.stayfocused.app.domain

import com.stayfocused.app.BuildConfig

/**
 * Pure Kotlin inspector for detecting settings, uninstallation, and service deactivation tampering.
 * Contains zero Android UI/Service dependencies to ensure 100% JVM unit testability.
 */
class SettingsTamperInspector(
    val selfPackageName: String = "com.stayfocused.app",
    val selfAppNames: Set<String> = setOf("monk mode", "stay focused", "stayfocused")
) {

    sealed class TamperDecision {
        data object Allow : TamperDecision()
        data class BlockTamper(val reason: String) : TamperDecision()
    }

    companion object {
        val SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.google.android.settings",
            "com.coloros.settings",
            "com.oplus.settings",
            "com.miui.securitycenter",
            "com.miui.securityadd",
            "com.samsung.android.app.settings",
            "com.transsion.settings"
        )

        val INSTALLER_PACKAGES = setOf(
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller"
        )

        const val PLAY_STORE_PACKAGE = "com.android.vending"
    }

    /**
     * Evaluates window state to detect uninstallation or anti-tamper circumvention attempts.
     */
    fun evaluate(
        packageName: String,
        className: String?,
        windowTexts: Collection<String>,
        isStrictModeActive: Boolean,
        isGracePeriodActive: Boolean = false,
        antiTamperEnabled: Boolean = BuildConfig.ANTI_TAMPER_ENABLED
    ): TamperDecision {
        // Invariant 1: If anti-tamper is disabled (e.g. debug builds) or grace period is active, allow
        if (!antiTamperEnabled || isGracePeriodActive) {
            return TamperDecision.Allow
        }

        // Invariant 2: Only enforce when Strict Mode is active
        if (!isStrictModeActive) {
            return TamperDecision.Allow
        }

        val pkgLower = packageName.trim().lowercase()
        val clsLower = className?.trim()?.lowercase() ?: ""

        val normalizedTexts = windowTexts.asSequence()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()

        val referencesSelf = normalizedTexts.any { text ->
            text.contains(selfPackageName) || selfAppNames.any { name -> text.contains(name) }
        }

        // Vector 1: Android System Settings
        if (SETTINGS_PACKAGES.contains(pkgLower) || pkgLower.endsWith(".settings")) {
            if (referencesSelf) {
                // Scenario 1A: App Info screen for Monk Mode (Uninstall, Force stop, Clear data)
                val isAppInfo = clsLower.contains("installedappdetails") ||
                        clsLower.contains("appinfo") ||
                        clsLower.contains("manageapplications") ||
                        clsLower.contains("appdetails") ||
                        clsLower.contains("applicationsettings") ||
                        clsLower.contains("subsettings")

                if (isAppInfo) {
                    return TamperDecision.BlockTamper("App Info settings for Monk Mode are locked during Strict Mode.")
                }

                // Scenario 1B: Device Administrator deactivation
                val isDeviceAdmin = clsLower.contains("deviceadmin") ||
                        clsLower.contains("specialaccess")

                if (isDeviceAdmin) {
                    return TamperDecision.BlockTamper("Device Admin deactivation is locked during Strict Mode.")
                }

                // Scenario 1C: Accessibility service deactivation
                val isAccessibility = clsLower.contains("accessibility")
                if (isAccessibility) {
                    return TamperDecision.BlockTamper("Accessibility settings for Monk Mode are locked during Strict Mode.")
                }

                // Fallback: If any settings screen specifically focuses our app name
                return TamperDecision.BlockTamper("Settings tampering targeting Monk Mode is locked during Strict Mode.")
            }

            // Normal Settings navigation (Wi-Fi, Bluetooth, Display, Sound) is permitted
            return TamperDecision.Allow
        }

        // Vector 2: Package Installer (uninstallation prompt)
        if (INSTALLER_PACKAGES.contains(pkgLower)) {
            if (referencesSelf && (clsLower.contains("uninstall") || normalizedTexts.any { it.contains("uninstall") })) {
                return TamperDecision.BlockTamper("Package Installer uninstallation is locked during Strict Mode.")
            }
        }

        // Vector 3: Google Play Store
        if (pkgLower == PLAY_STORE_PACKAGE) {
            if (referencesSelf && (clsLower.contains("appdetails") || normalizedTexts.any { it.contains("uninstall") })) {
                return TamperDecision.BlockTamper("Google Play Store uninstallation is locked during Strict Mode.")
            }
        }

        return TamperDecision.Allow
    }
}
