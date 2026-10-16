package com.stayfocused.app.domain

sealed interface PowerMenuDecision {
    data object Allow : PowerMenuDecision
    data object DismissPowerMenu : PowerMenuDecision
}

/**
 * Pure Kotlin evaluator for intercepting system Power Menus during active Strict Mode.
 * Dismisses soft reboot/shutdown dialogs while strictly preserving emergency SOS and dialer functionality.
 */
class PowerMenuInspector {

    companion object {
        val POWER_DIALOG_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.internal.app",
            "com.coloros.globalactions",
            "com.oplus.globalactions",
            "com.samsung.android.globalactions",
            "com.miui.powerkeeper"
        )

        val POWER_DIALOG_CLASSES = setOf(
            "globalactionsdialog",
            "globalactions",
            "shutdownactivity",
            "shutdowndialog",
            "poweroffdialog",
            "rebootdialog"
        )

        // Strict power/reboot tokens.
        // NOTICE: "emergency", "sos", "medical" are strictly excluded to preserve life safety!
        val POWER_ACTION_TOKENS = setOf(
            "power off",
            "turn off",
            "restart",
            "reboot",
            "poweroff"
        )

        // Life-safety allowlist: NEVER intercept or dismiss emergency dialogs or dialers
        val EMERGENCY_PACKAGES = setOf(
            "com.android.emergency",
            "com.google.android.apps.safetyhub",
            "com.android.phone",
            "com.coloros.emergency",
            "com.oplus.sos",
            "com.sec.android.app.safetyassurance",
            "com.samsung.android.emergency"
        )
    }

    fun evaluate(
        packageName: String,
        className: String?,
        windowTexts: List<String>,
        isStrictActive: Boolean,
        isBlockPowerOffEnabled: Boolean
    ): PowerMenuDecision {
        if (!isStrictActive || !isBlockPowerOffEnabled) return PowerMenuDecision.Allow

        val lowerPkg = packageName.lowercase()
        // Never touch emergency packages
        if (EMERGENCY_PACKAGES.contains(lowerPkg) || lowerPkg.contains("emergency") || lowerPkg.contains("sos")) {
            return PowerMenuDecision.Allow
        }

        val lowerCls = className?.lowercase() ?: ""
        val isTargetPkg = POWER_DIALOG_PACKAGES.contains(lowerPkg) || lowerPkg.contains("globalactions")
        val isTargetCls = POWER_DIALOG_CLASSES.any { lowerCls.contains(it) }

        val hasPowerTokens = windowTexts.any { text ->
            val lower = text.lowercase()
            POWER_ACTION_TOKENS.any { token -> lower.contains(token) }
        }

        // Must match either a known power dialog class or a power dialog package with explicit power action tokens
        if ((isTargetCls && (hasPowerTokens || lowerCls.contains("shutdown") || lowerCls.contains("poweroff"))) ||
            (isTargetPkg && hasPowerTokens && (lowerCls.contains("dialog") || lowerCls.contains("global") || lowerCls.contains("actions") || lowerCls.isEmpty()))
        ) {
            return PowerMenuDecision.DismissPowerMenu
        }

        return PowerMenuDecision.Allow
    }
}
