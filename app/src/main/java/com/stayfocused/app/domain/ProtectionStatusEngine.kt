package com.stayfocused.app.domain

import com.stayfocused.app.domain.model.ProtectionCheckResult
import com.stayfocused.app.domain.model.ProtectionCheckStatus
import com.stayfocused.app.domain.model.ProtectionCheckType
import com.stayfocused.app.domain.model.ProtectionOverallStatus
import com.stayfocused.app.domain.model.ProtectionStatusSnapshot

/**
 * Pure Kotlin decision engine for evaluating subsystem health and aggregate protection status.
 * Completely decoupled from Android platform classes to guarantee 100% JVM unit testability.
 */
class ProtectionStatusEngine {

    /**
     * Aggregates individual check results into an overall Green / Amber / Red status.
     * - RED: Any check in FAIL status.
     * - AMBER: No FAIL status, but at least one WARN status.
     * - GREEN: All checks in PASS status.
     */
    fun evaluate(checks: List<ProtectionCheckResult>): ProtectionOverallStatus {
        if (checks.any { it.status == ProtectionCheckStatus.FAIL }) {
            return ProtectionOverallStatus.RED
        }
        if (checks.any { it.status == ProtectionCheckStatus.WARN }) {
            return ProtectionOverallStatus.AMBER
        }
        return ProtectionOverallStatus.GREEN
    }

    /**
     * Creates an immutable snapshot of all checks and the overall aggregate status.
     */
    fun createSnapshot(
        checks: List<ProtectionCheckResult>,
        timestamp: Long = System.currentTimeMillis()
    ): ProtectionStatusSnapshot {
        return ProtectionStatusSnapshot(
            overallStatus = evaluate(checks),
            checks = checks,
            timestamp = timestamp
        )
    }

    /**
     * Evaluates FocusAccessibilityService status.
     */
    fun evaluateAccessibility(isEnabled: Boolean): ProtectionCheckResult {
        return if (isEnabled) {
            ProtectionCheckResult(
                type = ProtectionCheckType.ACCESSIBILITY,
                status = ProtectionCheckStatus.PASS,
                title = "Accessibility Shield",
                summary = "Active and intercepting foreground applications",
                actionLabel = null
            )
        } else {
            ProtectionCheckResult(
                type = ProtectionCheckType.ACCESSIBILITY,
                status = ProtectionCheckStatus.FAIL,
                title = "Accessibility Shield",
                summary = "Service disabled or killed by system",
                actionLabel = "Enable"
            )
        }
    }

    /**
     * Evaluates DnsVpnService status against configured blocked domains and VPN preparation state.
     */
    fun evaluateVpn(
        isVpnRunning: Boolean,
        isVpnPrepared: Boolean,
        hasBlockedDomains: Boolean
    ): ProtectionCheckResult {
        return when {
            !isVpnPrepared -> {
                ProtectionCheckResult(
                    type = ProtectionCheckType.VPN,
                    status = ProtectionCheckStatus.FAIL,
                    title = "DNS Shield",
                    summary = "VPN not prepared or superseded by another VPN app",
                    actionLabel = "Prepare"
                )
            }
            isVpnRunning -> {
                ProtectionCheckResult(
                    type = ProtectionCheckType.VPN,
                    status = ProtectionCheckStatus.PASS,
                    title = "DNS Shield",
                    summary = "Active and filtering DNS queries",
                    actionLabel = null
                )
            }
            hasBlockedDomains -> {
                ProtectionCheckResult(
                    type = ProtectionCheckType.VPN,
                    status = ProtectionCheckStatus.FAIL,
                    title = "DNS Shield",
                    summary = "Blocked domains configured, but DNS tunnel is not running",
                    actionLabel = "Start"
                )
            }
            else -> {
                ProtectionCheckResult(
                    type = ProtectionCheckType.VPN,
                    status = ProtectionCheckStatus.PASS,
                    title = "DNS Shield",
                    summary = "Idle (no blocked domains configured)",
                    actionLabel = null
                )
            }
        }
    }

    /**
     * Evaluates PowerManager battery optimization exemption status.
     */
    fun evaluateBatteryOptimization(isIgnoring: Boolean): ProtectionCheckResult {
        return if (isIgnoring) {
            ProtectionCheckResult(
                type = ProtectionCheckType.BATTERY_OPTIMIZATION,
                status = ProtectionCheckStatus.PASS,
                title = "Background Survival",
                summary = "Battery optimization exemption granted",
                actionLabel = null
            )
        } else {
            ProtectionCheckResult(
                type = ProtectionCheckType.BATTERY_OPTIMIZATION,
                status = ProtectionCheckStatus.WARN,
                title = "Background Survival",
                summary = "Battery optimizations not exempted; OEM skins may kill background protection",
                actionLabel = "Exempt"
            )
        }
    }

    /**
     * Evaluates WorkManager watchdog liveness against elapsed time.
     */
    fun evaluateWatchdog(lastRunTimestamp: Long, currentTimestamp: Long): ProtectionCheckResult {
        if (lastRunTimestamp <= 0L) {
            return ProtectionCheckResult(
                type = ProtectionCheckType.WATCHDOG,
                status = ProtectionCheckStatus.WARN,
                title = "System Watchdog",
                summary = "Watchdog pending initial run",
                actionLabel = "Run Now"
            )
        }

        val elapsedMs = (currentTimestamp - lastRunTimestamp).coerceAtLeast(0L)
        val elapsedMinutes = elapsedMs / (60 * 1000L)

        return when {
            elapsedMinutes <= 30L -> {
                ProtectionCheckResult(
                    type = ProtectionCheckType.WATCHDOG,
                    status = ProtectionCheckStatus.PASS,
                    title = "System Watchdog",
                    summary = "Active (last ran ${elapsedMinutes}m ago)",
                    actionLabel = null
                )
            }
            elapsedMinutes <= 60L -> {
                ProtectionCheckResult(
                    type = ProtectionCheckType.WATCHDOG,
                    status = ProtectionCheckStatus.WARN,
                    title = "System Watchdog",
                    summary = "Watchdog slightly delayed (last ran ${elapsedMinutes}m ago)",
                    actionLabel = "Run Now"
                )
            }
            else -> {
                ProtectionCheckResult(
                    type = ProtectionCheckType.WATCHDOG,
                    status = ProtectionCheckStatus.FAIL,
                    title = "System Watchdog",
                    summary = "Watchdog stalled (last ran ${elapsedMinutes}m ago)",
                    actionLabel = "Restart"
                )
            }
        }
    }
}
