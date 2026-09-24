package com.stayfocused.app.oem

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Helper to survive aggressive OEM background app killers (realme UI 5.0, ColorOS, MIUI, HyperOS, etc.)
 * by requesting battery optimization exemptions and providing autostart deep links.
 */
object OemSurvivalHelper {

    /**
     * Checks if battery optimizations are currently ignored for Stay Focused.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Creates an Intent directing the user to grant battery optimization exemption.
     */
    fun createBatteryOptimizationIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Comprehensive registry of OEM autostart, background running, and power manager activities.
     */
    fun getOemAutostartCandidates(): List<Intent> {
        return listOf(
            // Realme / Oppo (ColorOS / realme UI)
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
            Intent().setComponent(ComponentName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaard.PowerConsumptionActivity")),
            Intent().setComponent(ComponentName("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaard.PowerSaverModeActivity")),

            // Xiaomi (MIUI / HyperOS)
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.powercenter.PowerSettings")),

            // Vivo / iQOO (FuntouchOS / OriginOS)
            Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),

            // Samsung (One UI)
            Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")),
            Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"))
        ).map { it.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK } }
    }

    /**
     * Finds the first OEM autostart Intent that can be resolved on the current device.
     */
    fun findResolvableAutostartIntent(context: Context): Intent? {
        val pm = context.packageManager
        for (intent in getOemAutostartCandidates()) {
            val resolved = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (resolved != null) {
                return intent
            }
        }
        return null
    }
}
