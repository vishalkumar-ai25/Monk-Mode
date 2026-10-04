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
            "com.google.android.settings.intelligence",
            "com.coloros.settings",
            "com.oplus.settings",
            "com.coloros.safecenter",
            "com.oplus.safecenter",
            "com.miui.securitycenter",
            "com.miui.securityadd",
            "com.miui.cleanmaster",
            "com.samsung.android.settings",
            "com.samsung.android.app.settings",
            "com.transsion.settings",
            "com.vivo.permissionmanager",
            "com.iqoo.secure"
        )

        val INSTALLER_PACKAGES = setOf(
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller"
        )

        const val PLAY_STORE_PACKAGE = "com.android.vending"
        const val VPN_DIALOGS_PACKAGE = "com.android.vpndialogs"

        private val APP_DETAILS_TOKENS = setOf(
            "installedappdetails",
            "appdetails",
            "appinfo",
            "applicationsettings",
            "manageapplications",
            "appmanager"
        )

        private val DEVICE_ADMIN_TOKENS = setOf("deviceadmin")

        private val ACCESSIBILITY_TOKENS = setOf("accessibility")

        private val SPECIAL_ACCESS_TOKENS = setOf(
            "specialaccess",
            "specialappaccess",
            "drawoverlay",
            "appdrawoverlay"
        )

        private val DATE_TIME_TOKENS = setOf(
            "datetime",
            "date_time",
            "zonepicker",
            "setdate",
            "settime"
        )

        private val PRIVATE_DNS_TOKENS = setOf("privatedns", "private_dns")

        private val DANGEROUS_ACTIONS = setOf(
            "force stop",
            "clear data",
            "clear storage",
            "deactivate this device admin app",
            "deactivate",
            "uninstall",
            "turn off"
        )

        private val DANGEROUS_CATEGORIES = setOf(
            "date & time",
            "date and time",
            "private dns",
            "device admin"
        )
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

        // Vector 1: Package Installer (uninstallation prompt)
        if (INSTALLER_PACKAGES.contains(pkgLower)) {
            // Block package-installer uninstallation by class name or text (no self-reference needed)
            if (clsLower.contains("uninstall") || normalizedTexts.any { it.contains("uninstall") }) {
                return TamperDecision.BlockTamper("Package uninstallation is locked during Strict Mode.")
            }
            if (referencesSelf) {
                return TamperDecision.BlockTamper("Package installer tampering targeting Monk Mode is locked during Strict Mode.")
            }
            return TamperDecision.Allow
        }

        // Vector 2: Google Play Store
        if (pkgLower == PLAY_STORE_PACKAGE) {
            if (clsLower.contains("uninstall")) {
                return TamperDecision.BlockTamper("Google Play Store uninstallation is locked during Strict Mode.")
            }
            if (referencesSelf && (clsLower.contains("appdetails") || normalizedTexts.any { it.contains("uninstall") })) {
                return TamperDecision.BlockTamper("Google Play Store uninstallation is locked during Strict Mode.")
            }
            return TamperDecision.Allow
        }

        // Vector 3: VPN Dialogs Package
        if (pkgLower == VPN_DIALOGS_PACKAGE) {
            if (clsLower.contains("confirmdialog")) {
                return TamperDecision.Allow
            }
            return TamperDecision.BlockTamper("VPN configuration is locked during Strict Mode.")
        }

        // Vector 4: Android System Settings & OEM Security Centers
        if (SETTINGS_PACKAGES.contains(pkgLower) || pkgLower.endsWith(".settings") ||
            pkgLower.contains("safecenter") || pkgLower.contains("securitycenter")) {
            // 4A: Safe settings allow-list (Wi-Fi, Bluetooth, Display, Sound) takes precedence
            if (isAllowedSettingsClass(clsLower)) {
                return TamperDecision.Allow
            }

            // 4B: Deterministic blocking by CLASS NAME alone (no text match required)
            // 1) App details
            if (APP_DETAILS_TOKENS.any { clsLower.contains(it) }) {
                return TamperDecision.BlockTamper("App details settings are locked during Strict Mode.")
            }

            // 2) Device Admin
            if (DEVICE_ADMIN_TOKENS.any { clsLower.contains(it) }) {
                return TamperDecision.BlockTamper("Device Admin settings are locked during Strict Mode.")
            }

            // 3) Accessibility service detail
            if (ACCESSIBILITY_TOKENS.any { clsLower.contains(it) }) {
                return TamperDecision.BlockTamper("Accessibility settings are locked during Strict Mode.")
            }

            // 4) Special app access
            if (SPECIAL_ACCESS_TOKENS.any { clsLower.contains(it) }) {
                return TamperDecision.BlockTamper("Special app access settings are locked during Strict Mode.")
            }

            // 5) Date & time settings
            if (DATE_TIME_TOKENS.any { clsLower.contains(it) }) {
                return TamperDecision.BlockTamper("Date and time settings are locked during Strict Mode.")
            }

            // 6) VPN settings
            if (clsLower.contains("vpnsettings")) {
                return TamperDecision.BlockTamper("VPN settings are locked during Strict Mode.")
            }

            // 7) Private DNS settings
            if (PRIVATE_DNS_TOKENS.any { clsLower.contains(it) }) {
                return TamperDecision.BlockTamper("Private DNS settings are locked during Strict Mode.")
            }

            // 4C: Text-match fallback for generic container activities (e.g. SubSettings or dialogs)
            if (referencesSelf) {
                return TamperDecision.BlockTamper("Settings tampering targeting Monk Mode is locked during Strict Mode.")
            }

            val isDangerousAction = normalizedTexts.any { text ->
                DANGEROUS_ACTIONS.any { action -> text.contains(action) }
            }
            if (isDangerousAction) {
                return TamperDecision.BlockTamper("Settings tampering action is locked during Strict Mode.")
            }

            val isDangerousCategory = normalizedTexts.any { text ->
                DANGEROUS_CATEGORIES.any { category -> text.contains(category) }
            }
            if (isDangerousCategory) {
                return TamperDecision.BlockTamper("Settings tampering targeting critical settings is locked during Strict Mode.")
            }

            // Safe navigation in Settings
            return TamperDecision.Allow
        }

        return TamperDecision.Allow
    }

    private fun isAllowedSettingsClass(clsLower: String): Boolean {
        if (clsLower.isEmpty()) return false
        val isWifi = clsLower.contains("wifi") || clsLower.contains("networkprovider")
        val isBluetooth = clsLower.contains("bluetooth")
        val isDisplay = (clsLower.contains("display") ||
                clsLower.contains("brightness") ||
                clsLower.contains("wallpaper") ||
                clsLower.contains("darkmode")) &&
                !clsLower.contains("overlay") &&
                !clsLower.contains("drawoverlay") &&
                !clsLower.contains("specialaccess")
        val isSound = clsLower.contains("sound") ||
                clsLower.contains("volume") ||
                clsLower.contains("ringtone") ||
                clsLower.contains("audio")

        return isWifi || isBluetooth || isDisplay || isSound
    }
}
