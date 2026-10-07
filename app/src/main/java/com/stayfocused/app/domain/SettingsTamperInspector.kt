package com.stayfocused.app.domain

import com.stayfocused.app.BuildConfig

/**
 * Pure Kotlin inspector for detecting settings, uninstallation, and service deactivation tampering.
 * Contains zero Android UI/Service dependencies to ensure 100% JVM unit testability.
 */
class SettingsTamperInspector(
    val selfPackageName: String = "com.stayfocused.app",
    val selfAppNames: Set<String> = setOf("monk mode", "monkmode")
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
            "com.iqoo.secure",
            "com.oplus.securitypermission",
            "com.coloros.securitypermission",
            "com.oplus.battery"
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

        /**
         * Accessibility shortcut tokens — blocked because hardware shortcuts
         * can disable accessibility services without entering the detail page.
         */
        private val ACCESSIBILITY_SHORTCUT_TOKENS = setOf(
            "accessibilityshortcut",
            "accessibility_shortcut",
            "accessibilitybutton"
        )

        /**
         * Accessibility service-detail tokens — only blocked when window text
         * references Monk Mode (self). This allows users to configure StayFree,
         * Stay Focused, and other apps' accessibility services during Strict Mode.
         */
        private val ACCESSIBILITY_DETAIL_TOKENS = setOf(
            "toggleservice",
            "toggleaccessibilityservice",
            "accessibilitydetail"
        )

        private val ACCESSIBILITY_HEADERS = setOf(
            "accessibility",
            "downloaded apps",
            "downloaded services",
            "installed apps",
            "installed services"
        )

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
            "deactivate admin app",
            "uninstall",
            "stop monk mode",
            "turn off monk mode",
            "disable monk mode",
            "use monk mode",
            "stop service",
            "stop this service",
            "turn off service"
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
        wasStrictActiveAtBoot: Boolean = false,
        antiTamperEnabled: Boolean = BuildConfig.ANTI_TAMPER_ENABLED
    ): TamperDecision {
        // Invariant 1: If anti-tamper is disabled (e.g. debug builds), allow
        if (!antiTamperEnabled) {
            return TamperDecision.Allow
        }

        // Invariant 2: Boot grace period applies ONLY if no strict session was active at boot.
        // If strict mode was active at boot, no grace is granted (escape hatches: Layer 4 ADB or Layer 2 recovery code).
        if (isGracePeriodActive && !wasStrictActiveAtBoot) {
            return TamperDecision.Allow
        }

        // Invariant 3: Only enforce when Strict Mode is active
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
            // Self-exemption for Monk Mode runtime permission requests (e.g. POST_NOTIFICATIONS)
            if (clsLower.contains("grantpermissions") || clsLower.contains("reviewpermissions")) {
                return TamperDecision.Allow
            }
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
            if (isAllowedSettingsClass(clsLower) || (!referencesSelf && isAllowedSettingsContent(normalizedTexts))) {
                return TamperDecision.Allow
            }

            // 4B: Deterministic blocking by CLASS NAME alone
            // 1) App details
            if (APP_DETAILS_TOKENS.any { clsLower.contains(it) }) {
                return TamperDecision.BlockTamper("App details settings are locked during Strict Mode.")
            }

            // 2) Device Admin (always locked during Strict Mode)
            if (DEVICE_ADMIN_TOKENS.any { clsLower.contains(it) }) {
                return TamperDecision.BlockTamper("Device Admin settings are locked during Strict Mode.")
            }

            // 3) Accessibility shortcuts (hardware shortcut bypass prevention)
            if (ACCESSIBILITY_SHORTCUT_TOKENS.any { clsLower.contains(it) }) {
                return TamperDecision.BlockTamper("Accessibility shortcut settings are locked during Strict Mode.")
            }

            // 4) Accessibility service detail (only block when targeting Monk Mode)
            if (ACCESSIBILITY_DETAIL_TOKENS.any { clsLower.contains(it) }) {
                if (referencesSelf) {
                    return TamperDecision.BlockTamper("Accessibility settings targeting Monk Mode are locked during Strict Mode.")
                }
                // Allow configuring third-party accessibility services (StayFree, Stay Focused, etc.)
                return TamperDecision.Allow
            }

            // 5) Special app access
            if (SPECIAL_ACCESS_TOKENS.any { clsLower.contains(it) }) {
                return TamperDecision.BlockTamper("Special app access settings are locked during Strict Mode.")
            }

            // 6) Date & time settings
            if (DATE_TIME_TOKENS.any { clsLower.contains(it) }) {
                return TamperDecision.BlockTamper("Date and time settings are locked during Strict Mode.")
            }

            // 7) VPN settings
            if (clsLower.contains("vpnsettings")) {
                return TamperDecision.BlockTamper("VPN settings are locked during Strict Mode.")
            }

            // 8) Private DNS settings
            if (PRIVATE_DNS_TOKENS.any { clsLower.contains(it) }) {
                return TamperDecision.BlockTamper("Private DNS settings are locked during Strict Mode.")
            }

            // 4C: Accessibility header / category matching scoped to Monk Mode detail
            val isContainerOrDialog = clsLower.contains("subsettings") ||
                    clsLower.contains("dialog") ||
                    clsLower.contains("accessibility") ||
                    clsLower.contains("preference")
            val isAccessibilityHeader = normalizedTexts.any { it in ACCESSIBILITY_HEADERS }
            if (isAccessibilityHeader && isContainerOrDialog && referencesSelf && !isAllowedSettingsContent(normalizedTexts)) {
                return TamperDecision.BlockTamper("Accessibility settings targeting Monk Mode are locked during Strict Mode.")
            }

            // 4D: Text-match fallback for generic container activities (e.g. SubSettings or dialogs)
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

    private fun isAllowedSettingsContent(normalizedTexts: Set<String>): Boolean {
        if (normalizedTexts.isEmpty()) return false
        return normalizedTexts.any { text ->
            text == "sound" || text == "sound & vibration" || text == "sounds & vibration" ||
            text == "sound and vibration" || text == "sounds and vibration" ||
            text == "sound & notifications" || text == "sound and notifications" ||
            text == "volume" || text == "display" ||
            text == "display & brightness" || text == "display and brightness" ||
            text == "network & internet" || text == "network and internet" ||
            text == "wi-fi" || text == "wifi" || text == "bluetooth"
        }
    }

    private fun isAllowedSettingsClass(clsLower: String): Boolean {
        if (clsLower.isEmpty()) return false
        val isHomepage = (clsLower.contains("homepage") ||
                clsLower.endsWith(".settings") ||
                clsLower.endsWith(".settingsactivity") ||
                clsLower.endsWith(".mainsettings") ||
                clsLower.endsWith(".rootsettings")) &&
                !clsLower.contains("subsettings")
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

        return isHomepage || isWifi || isBluetooth || isDisplay || isSound
    }
}
