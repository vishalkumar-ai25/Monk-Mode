package com.stayfocused.app.domain

import android.content.Context
import android.net.VpnService
import android.os.PowerManager
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.domain.model.ProtectionStatusSnapshot
import com.stayfocused.app.util.ProtectionPreferences
import com.stayfocused.app.vpn.DnsVpnService
import com.stayfocused.app.worker.WatchdogWorker

/**
 * Health checker adapter bridging Android system APIs and the pure Kotlin ProtectionStatusEngine.
 */
class ProtectionHealthChecker(
    private val context: Context,
    private val database: StayFocusedDatabase = StayFocusedDatabase.getInstance(context),
    private val preferences: ProtectionPreferences = ProtectionPreferences(context),
    val engine: ProtectionStatusEngine = ProtectionStatusEngine()
) {

    /**
     * Checks all 4 subsystems and aggregates their status into a snapshot.
     */
    fun checkAll(hasBlockedDomains: Boolean? = null): ProtectionStatusSnapshot {
        val now = System.currentTimeMillis()

        // 1. Accessibility Service check
        val isAccessibilityEnabled = try {
            WatchdogWorker.isAccessibilityServiceEnabled(context)
        } catch (e: Exception) {
            false
        }
        val accessibilityResult = engine.evaluateAccessibility(isAccessibilityEnabled)

        // 2. VPN Tunnel check
        val isVpnRunning = try {
            DnsVpnService.isVpnRunning
        } catch (e: Exception) {
            false
        }
        val isVpnPrepared = try {
            VpnService.prepare(context) == null
        } catch (e: Exception) {
            false
        }
        val blockedDomainsExist = hasBlockedDomains ?: try {
            database.blockedDomainDao().getAllBlockedDomainsSync().any { it.isBlocked }
        } catch (e: Exception) {
            false
        }
        val vpnResult = engine.evaluateVpn(isVpnRunning, isVpnPrepared, blockedDomainsExist)

        // 3. Battery Optimization exemption check
        val isIgnoringBatteryOptimizations = try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        } catch (e: Exception) {
            false
        }
        val batteryResult = engine.evaluateBatteryOptimization(isIgnoringBatteryOptimizations)

        // 4. Watchdog liveness check
        val lastWatchdog = try {
            preferences.lastWatchdogRunTimestamp
        } catch (e: Exception) {
            0L
        }
        val watchdogResult = engine.evaluateWatchdog(lastWatchdog, now)

        val checks = listOf(accessibilityResult, vpnResult, batteryResult, watchdogResult)
        return engine.createSnapshot(checks, now)
    }
}
