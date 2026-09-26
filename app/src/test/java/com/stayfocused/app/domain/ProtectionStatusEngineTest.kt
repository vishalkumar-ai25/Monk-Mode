package com.stayfocused.app.domain

import com.stayfocused.app.domain.model.ProtectionCheckResult
import com.stayfocused.app.domain.model.ProtectionCheckStatus
import com.stayfocused.app.domain.model.ProtectionCheckType
import com.stayfocused.app.domain.model.ProtectionOverallStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProtectionStatusEngineTest {

    private lateinit var engine: ProtectionStatusEngine

    @Before
    fun setUp() {
        engine = ProtectionStatusEngine()
    }

    @Test
    fun testAllChecksPassGivesGreen() {
        val checks = listOf(
            ProtectionCheckResult(
                type = ProtectionCheckType.ACCESSIBILITY,
                status = ProtectionCheckStatus.PASS,
                title = "Accessibility Shield",
                summary = "Active and monitoring foreground apps"
            ),
            ProtectionCheckResult(
                type = ProtectionCheckType.VPN,
                status = ProtectionCheckStatus.PASS,
                title = "DNS Shield",
                summary = "DNS VPN tunnel active"
            ),
            ProtectionCheckResult(
                type = ProtectionCheckType.BATTERY_OPTIMIZATION,
                status = ProtectionCheckStatus.PASS,
                title = "Background Survival",
                summary = "Battery optimizations ignored"
            ),
            ProtectionCheckResult(
                type = ProtectionCheckType.WATCHDOG,
                status = ProtectionCheckStatus.PASS,
                title = "System Watchdog",
                summary = "Last ran 5m ago"
            )
        )

        val overall = engine.evaluate(checks)
        assertEquals(ProtectionOverallStatus.GREEN, overall)

        val snapshot = engine.createSnapshot(checks, 1000L)
        assertEquals(ProtectionOverallStatus.GREEN, snapshot.overallStatus)
        assertEquals(4, snapshot.checks.size)
    }

    @Test
    fun testWarningGivesAmberWhenNoFailures() {
        val checks = listOf(
            ProtectionCheckResult(
                type = ProtectionCheckType.ACCESSIBILITY,
                status = ProtectionCheckStatus.PASS,
                title = "Accessibility Shield",
                summary = "Active"
            ),
            ProtectionCheckResult(
                type = ProtectionCheckType.VPN,
                status = ProtectionCheckStatus.PASS,
                title = "DNS Shield",
                summary = "Active"
            ),
            ProtectionCheckResult(
                type = ProtectionCheckType.BATTERY_OPTIMIZATION,
                status = ProtectionCheckStatus.WARN,
                title = "Background Survival",
                summary = "Battery optimizations not exempted"
            ),
            ProtectionCheckResult(
                type = ProtectionCheckType.WATCHDOG,
                status = ProtectionCheckStatus.PASS,
                title = "System Watchdog",
                summary = "Healthy"
            )
        )

        val overall = engine.evaluate(checks)
        assertEquals(ProtectionOverallStatus.AMBER, overall)
    }

    @Test
    fun testAccessibilityFailureGivesRed() {
        val checks = listOf(
            ProtectionCheckResult(
                type = ProtectionCheckType.ACCESSIBILITY,
                status = ProtectionCheckStatus.FAIL,
                title = "Accessibility Shield",
                summary = "Service disabled by system"
            ),
            ProtectionCheckResult(
                type = ProtectionCheckType.VPN,
                status = ProtectionCheckStatus.PASS,
                title = "DNS Shield",
                summary = "Active"
            ),
            ProtectionCheckResult(
                type = ProtectionCheckType.BATTERY_OPTIMIZATION,
                status = ProtectionCheckStatus.PASS,
                title = "Background Survival",
                summary = "Ignored"
            ),
            ProtectionCheckResult(
                type = ProtectionCheckType.WATCHDOG,
                status = ProtectionCheckStatus.PASS,
                title = "System Watchdog",
                summary = "Healthy"
            )
        )

        val overall = engine.evaluate(checks)
        assertEquals(ProtectionOverallStatus.RED, overall)
    }

    @Test
    fun testWatchdogStalledGivesRed() {
        val checks = listOf(
            ProtectionCheckResult(
                type = ProtectionCheckType.ACCESSIBILITY,
                status = ProtectionCheckStatus.PASS,
                title = "Accessibility Shield",
                summary = "Active"
            ),
            ProtectionCheckResult(
                type = ProtectionCheckType.VPN,
                status = ProtectionCheckStatus.PASS,
                title = "DNS Shield",
                summary = "Active"
            ),
            ProtectionCheckResult(
                type = ProtectionCheckType.BATTERY_OPTIMIZATION,
                status = ProtectionCheckStatus.PASS,
                title = "Background Survival",
                summary = "Ignored"
            ),
            ProtectionCheckResult(
                type = ProtectionCheckType.WATCHDOG,
                status = ProtectionCheckStatus.FAIL,
                title = "System Watchdog",
                summary = "Stalled for > 60m"
            )
        )

        val overall = engine.evaluate(checks)
        assertEquals(ProtectionOverallStatus.RED, overall)
    }

    @Test
    fun testVpnFailureWhenBlockedDomainsExistGivesRed() {
        val check = engine.evaluateVpn(
            isVpnRunning = false,
            isVpnPrepared = true,
            hasBlockedDomains = true
        )
        assertEquals(ProtectionCheckStatus.FAIL, check.status)

        val overall = engine.evaluate(listOf(check))
        assertEquals(ProtectionOverallStatus.RED, overall)
    }

    @Test
    fun testVpnNotRunningAllowedWhenNoBlockedDomains() {
        val check = engine.evaluateVpn(
            isVpnRunning = false,
            isVpnPrepared = true,
            hasBlockedDomains = false
        )
        assertEquals(ProtectionCheckStatus.PASS, check.status)
    }

    @Test
    fun testVpnSupersededGivesFail() {
        val check = engine.evaluateVpn(
            isVpnRunning = false,
            isVpnPrepared = false,
            hasBlockedDomains = true
        )
        assertEquals(ProtectionCheckStatus.FAIL, check.status)
        assertTrue(check.summary.contains("superseded", ignoreCase = true) || check.summary.contains("not prepared", ignoreCase = true))
    }

    @Test
    fun testWatchdogEvaluationThresholds() {
        val now = 100_000_000L

        // Never run (first install) -> WARN
        val neverRun = engine.evaluateWatchdog(lastRunTimestamp = 0L, currentTimestamp = now)
        assertEquals(ProtectionCheckStatus.WARN, neverRun.status)

        // Ran 10 minutes ago (< 30 min) -> PASS
        val recentRun = engine.evaluateWatchdog(lastRunTimestamp = now - (10 * 60 * 1000L), currentTimestamp = now)
        assertEquals(ProtectionCheckStatus.PASS, recentRun.status)

        // Ran 45 minutes ago (30..60 min) -> WARN
        val delayedRun = engine.evaluateWatchdog(lastRunTimestamp = now - (45 * 60 * 1000L), currentTimestamp = now)
        assertEquals(ProtectionCheckStatus.WARN, delayedRun.status)

        // Ran 90 minutes ago (> 60 min) -> FAIL
        val stalledRun = engine.evaluateWatchdog(lastRunTimestamp = now - (90 * 60 * 1000L), currentTimestamp = now)
        assertEquals(ProtectionCheckStatus.FAIL, stalledRun.status)
    }

    @Test
    fun testAccessibilityEvaluation() {
        val enabled = engine.evaluateAccessibility(isEnabled = true)
        assertEquals(ProtectionCheckStatus.PASS, enabled.status)

        val disabled = engine.evaluateAccessibility(isEnabled = false)
        assertEquals(ProtectionCheckStatus.FAIL, disabled.status)
    }

    @Test
    fun testBatteryOptimizationEvaluation() {
        val exempted = engine.evaluateBatteryOptimization(isIgnoring = true)
        assertEquals(ProtectionCheckStatus.PASS, exempted.status)

        val notExempted = engine.evaluateBatteryOptimization(isIgnoring = false)
        assertEquals(ProtectionCheckStatus.WARN, notExempted.status)
    }
}
