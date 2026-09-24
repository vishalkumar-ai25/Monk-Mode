package com.stayfocused.app.strict

import com.stayfocused.app.domain.InterceptionDecisionEngine
import com.stayfocused.app.domain.model.BlockReason
import com.stayfocused.app.domain.model.FocusProfileRule
import com.stayfocused.app.domain.model.InterceptionContext
import com.stayfocused.app.domain.model.InterceptionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId

class GracePeriodManagerTest {

    private lateinit var gracePeriodManager: GracePeriodManager
    private lateinit var decisionEngine: InterceptionDecisionEngine

    @Before
    fun setUp() {
        gracePeriodManager = GracePeriodManager(
            gracePeriodDurationMs = 5 * 60 * 1000L // 5 minutes
        )
        decisionEngine = InterceptionDecisionEngine()
    }

    @Test
    fun testGracePeriodActiveWithinFiveMinutesOfBoot() {
        // Device booted 2 minutes ago
        val elapsed2Min = 2 * 60 * 1000L
        assertTrue(
            "Grace period should be active at 2 minutes post-boot",
            gracePeriodManager.isGracePeriodActive(elapsedRealtimeMs = elapsed2Min)
        )

        val remaining = gracePeriodManager.getRemainingGracePeriodMs(elapsedRealtimeMs = elapsed2Min)
        assertEquals(3 * 60 * 1000L, remaining)
    }

    @Test
    fun testGracePeriodExpiresAfterFiveMinutes() {
        // Exactly 5 minutes + 1ms
        val elapsed5Min1Ms = 5 * 60 * 1000L + 1L
        assertFalse(
            "Grace period should expire after 5 minutes",
            gracePeriodManager.isGracePeriodActive(elapsedRealtimeMs = elapsed5Min1Ms)
        )

        val remaining = gracePeriodManager.getRemainingGracePeriodMs(elapsedRealtimeMs = elapsed5Min1Ms)
        assertEquals(0L, remaining)
    }

    @Test
    fun testSettingsAllowedDuringGracePeriodButBlockedAfterwards() {
        val settingsContextGraceActive = InterceptionContext(
            targetPackageName = "com.android.settings",
            isSettingsOrInstaller = true,
            antiTamperEnabled = true,
            isStrictModeActive = true,
            isGracePeriodActive = true,
            currentTimeMillis = 1_000_000L,
            zoneId = ZoneId.systemDefault()
        )

        val resultDuringGrace = decisionEngine.evaluate(settingsContextGraceActive)
        assertTrue(
            "Settings must be allowed during boot grace period",
            resultDuringGrace is InterceptionResult.Allow
        )

        val settingsContextGraceExpired = settingsContextGraceActive.copy(isGracePeriodActive = false)
        val resultAfterGrace = decisionEngine.evaluate(settingsContextGraceExpired)
        assertTrue(
            "Settings must be blocked after grace period expires under strict mode",
            resultAfterGrace is InterceptionResult.Block
        )
    }

    @Test
    fun testAppBlocksRemainStrictlyArmedDuringGracePeriod() {
        val profile = FocusProfileRule(
            profileId = 1,
            name = "Deep Work",
            blockedPackages = setOf("com.instagram.android")
        )

        val instagramContextDuringGrace = InterceptionContext(
            targetPackageName = "com.instagram.android",
            isSettingsOrInstaller = false,
            antiTamperEnabled = true,
            isStrictModeActive = true,
            isGracePeriodActive = true, // Grace period is active!
            activeProfiles = listOf(profile),
            currentTimeMillis = 1_000_000L,
            zoneId = ZoneId.systemDefault()
        )

        val result = decisionEngine.evaluate(instagramContextDuringGrace)
        assertTrue(
            "App blocks must remain strictly enforced during boot grace period",
            result is InterceptionResult.Block
        )
        val block = result as InterceptionResult.Block
        assertTrue(block.reason is BlockReason.ProfileActive)
    }
}
