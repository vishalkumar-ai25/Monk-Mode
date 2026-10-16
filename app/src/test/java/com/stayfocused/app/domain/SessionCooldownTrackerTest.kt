package com.stayfocused.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionCooldownTrackerTest {

    private lateinit var tracker: SessionCooldownTracker

    @Before
    fun setUp() {
        tracker = SessionCooldownTracker()
    }

    @Test
    fun testNoLimitConfiguredReturnsNoLimit() {
        val result = tracker.tick(
            packageName = "com.instagram.android",
            sessionAllowanceMinutes = 0,
            cooldownDurationMinutes = 15,
            nowElapsed = 10_000L,
            nowWallMs = 1_000_000L,
            currentBootCount = 1,
            isScreenInteractive = true
        )
        assertEquals(SessionCooldownTickResult.NoLimitConfigured, result)
    }

    @Test
    fun testScreenOffPausedDoesNotAccumulate() {
        tracker.onPackageForegrounded("com.instagram.android", nowElapsed = 10_000L, cooldownDurationMinutes = 15)

        val result = tracker.tick(
            packageName = "com.instagram.android",
            sessionAllowanceMinutes = 10,
            cooldownDurationMinutes = 15,
            nowElapsed = 20_000L,
            nowWallMs = 1_000_000L,
            currentBootCount = 1,
            isScreenInteractive = false // Screen is OFF
        )

        assertEquals(SessionCooldownTickResult.ScreenOffPaused, result)
        assertEquals(0L, tracker.getAccumulatedActiveMs("com.instagram.android"))
    }

    @Test
    fun testContinuousAccumulationUnderAllowance() {
        tracker.onPackageForegrounded("com.instagram.android", nowElapsed = 10_000L, cooldownDurationMinutes = 15)

        val result1 = tracker.tick(
            packageName = "com.instagram.android",
            sessionAllowanceMinutes = 10,
            cooldownDurationMinutes = 15,
            nowElapsed = 11_000L, // 1s tick
            nowWallMs = 1_000_000L,
            currentBootCount = 1,
            isScreenInteractive = true
        )

        assertEquals(SessionCooldownTickResult.ActiveSessionContinues, result1)
        assertEquals(1_000L, tracker.getAccumulatedActiveMs("com.instagram.android"))

        val result2 = tracker.tick(
            packageName = "com.instagram.android",
            sessionAllowanceMinutes = 10,
            cooldownDurationMinutes = 15,
            nowElapsed = 15_000L, // 4s tick
            nowWallMs = 1_004_000L,
            currentBootCount = 1,
            isScreenInteractive = true
        )

        assertEquals(SessionCooldownTickResult.ActiveSessionContinues, result2)
        assertEquals(5_000L, tracker.getAccumulatedActiveMs("com.instagram.android"))
    }

    @Test
    fun testTriggerCooldownAtAllowanceAndResetAccumulator() {
        tracker.onPackageForegrounded("com.instagram.android", nowElapsed = 10_000L, cooldownDurationMinutes = 15)

        // Simulate reaching 10-minute allowance (600,000 ms)
        val allowanceElapsed = 10_000L + 600_000L
        val wallTime = 1_700_000_000_000L

        val result = tracker.tick(
            packageName = "com.instagram.android",
            sessionAllowanceMinutes = 10,
            cooldownDurationMinutes = 15,
            nowElapsed = allowanceElapsed,
            nowWallMs = wallTime,
            currentBootCount = 3,
            isScreenInteractive = true
        )

        assertTrue("Must trigger cooldown at allowance threshold", result is SessionCooldownTickResult.TriggerCooldown)
        val trigger = result as SessionCooldownTickResult.TriggerCooldown
        assertEquals("com.instagram.android", trigger.packageName)
        assertEquals(15, trigger.cooldownDurationMinutes)
        assertEquals(allowanceElapsed, trigger.startElapsedRealtime)
        assertEquals(wallTime + (15 * 60_000L), trigger.activeUntilWallMs)
        assertEquals(3, trigger.bootCount)

        // Adversarial invariant: Accumulator must be reset to 0 to prevent instant re-trigger
        assertEquals(0L, tracker.getAccumulatedActiveMs("com.instagram.android"))

        // Next tick should continue from 0 rather than re-triggering
        val nextTick = tracker.tick(
            packageName = "com.instagram.android",
            sessionAllowanceMinutes = 10,
            cooldownDurationMinutes = 15,
            nowElapsed = allowanceElapsed + 1_000L,
            nowWallMs = wallTime + 1_000L,
            currentBootCount = 3,
            isScreenInteractive = true
        )
        assertEquals(SessionCooldownTickResult.ActiveSessionContinues, nextTick)
        assertEquals(1_000L, tracker.getAccumulatedActiveMs("com.instagram.android"))
    }

    @Test
    fun testQuickExitUnderThresholdDoesNotResetAccumulator() {
        tracker.onPackageForegrounded("com.instagram.android", nowElapsed = 10_000L, cooldownDurationMinutes = 15)

        // Use for 8 minutes (480,000 ms)
        tracker.tick(
            packageName = "com.instagram.android",
            sessionAllowanceMinutes = 10,
            cooldownDurationMinutes = 15,
            nowElapsed = 10_000L + 480_000L,
            nowWallMs = 1_000_000L,
            currentBootCount = 1,
            isScreenInteractive = true
        )
        assertEquals(480_000L, tracker.getAccumulatedActiveMs("com.instagram.android"))

        // User switches away / backgrounds app
        val exitElapsed = 10_000L + 480_000L
        tracker.onPackageBackgrounded("com.instagram.android", exitElapsed)

        // User returns after 2 minutes (120,000 ms) -> away duration is 2m, below max(15m, 5m) = 15m threshold!
        val returnElapsed = exitElapsed + 120_000L
        tracker.onPackageForegrounded("com.instagram.android", returnElapsed, cooldownDurationMinutes = 15)

        // Accumulator must be preserved!
        assertEquals(480_000L, tracker.getAccumulatedActiveMs("com.instagram.android"))

        // After 2 more minutes of use (now 10 minutes total), cooldown should trigger!
        val finalTick = tracker.tick(
            packageName = "com.instagram.android",
            sessionAllowanceMinutes = 10,
            cooldownDurationMinutes = 15,
            nowElapsed = returnElapsed + 120_000L,
            nowWallMs = 2_000_000L,
            currentBootCount = 1,
            isScreenInteractive = true
        )
        assertTrue("Must trigger cooldown after total 10m use despite quick 2m break", finalTick is SessionCooldownTickResult.TriggerCooldown)
    }

    @Test
    fun testGenuineBreakOverThresholdResetsAccumulator() {
        tracker.onPackageForegrounded("com.instagram.android", nowElapsed = 10_000L, cooldownDurationMinutes = 15)

        // Use for 8 minutes (480,000 ms)
        tracker.tick(
            packageName = "com.instagram.android",
            sessionAllowanceMinutes = 10,
            cooldownDurationMinutes = 15,
            nowElapsed = 10_000L + 480_000L,
            nowWallMs = 1_000_000L,
            currentBootCount = 1,
            isScreenInteractive = true
        )
        assertEquals(480_000L, tracker.getAccumulatedActiveMs("com.instagram.android"))

        // User exits app
        val exitElapsed = 10_000L + 480_000L
        tracker.onPackageBackgrounded("com.instagram.android", exitElapsed)

        // User returns after 16 minutes (960,000 ms) -> exceeds 15m threshold!
        val returnElapsed = exitElapsed + 960_000L
        tracker.onPackageForegrounded("com.instagram.android", returnElapsed, cooldownDurationMinutes = 15)

        // Genuine break: accumulator must be reset to 0
        assertEquals(0L, tracker.getAccumulatedActiveMs("com.instagram.android"))
    }

    @Test
    fun testClearStateRemovesPackage() {
        tracker.onPackageForegrounded("com.instagram.android", nowElapsed = 10_000L, cooldownDurationMinutes = 15)
        tracker.tick(
            packageName = "com.instagram.android",
            sessionAllowanceMinutes = 10,
            cooldownDurationMinutes = 15,
            nowElapsed = 20_000L,
            nowWallMs = 1_000_000L,
            currentBootCount = 1,
            isScreenInteractive = true
        )
        assertEquals(10_000L, tracker.getAccumulatedActiveMs("com.instagram.android"))

        tracker.clearState("com.instagram.android")
        assertEquals(0L, tracker.getAccumulatedActiveMs("com.instagram.android"))
    }
}
