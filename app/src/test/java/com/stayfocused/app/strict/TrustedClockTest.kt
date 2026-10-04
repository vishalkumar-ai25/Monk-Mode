package com.stayfocused.app.strict

import com.stayfocused.app.data.local.entities.StrictSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM unit tests for [TrustedClock].
 * Covers tamper-resistance, reboot resilience, delayed unlock protection, and graceful expiration.
 */
class TrustedClockTest {

    private val trustedClock = TrustedClock(tamperToleranceMs = 60_000L)

    private fun createBaseSession(
        startTime: Long = 1_000_000L,
        durationMs: Long = 2 * 60 * 60 * 1000L, // 2 hours
        startElapsed: Long = 50_000L,
        bootCount: Int = 1
    ): StrictSessionEntity {
        return StrictSessionEntity(
            id = 1,
            profileId = 1,
            startTime = startTime,
            targetEndTime = startTime + durationMs,
            startElapsedRealtime = startElapsed,
            isActive = true,
            accumulatedMonotonicMs = 0L,
            lastElapsedRealtime = startElapsed,
            lastWallTime = startTime,
            bootCount = bootCount
        )
    }

    // ── Test 1: Clock set forward 48h while strict -> still active ───────────

    @Test
    fun `clock set forward 48h while strict remains active and flags tamper`() {
        val session = createBaseSession()
        var tamperDetected = false
        var recordedWallDiff = 0L
        var recordedMonoDiff = 0L

        // 10 minutes pass of real time: elapsed advances by 10m (600,000ms), but wall leaps by 48h + 10m
        val forward48hMs = 48 * 60 * 60 * 1000L
        val realPassMs = 10 * 60 * 1000L

        val spoofedSnapshot = ClockSnapshot(
            elapsedRealtimeMs = session.lastElapsedRealtime + realPassMs,
            wallTimeMs = session.lastWallTime + forward48hMs + realPassMs,
            bootCount = session.bootCount
        )

        val isActive = trustedClock.isSessionActive(
            session = session,
            currentSnapshot = spoofedSnapshot,
            onClockTamper = { wallDiff, monoDiff ->
                tamperDetected = true
                recordedWallDiff = wallDiff
                recordedMonoDiff = monoDiff
            }
        )

        // Strict mode must remain active because only 10m of monotonic time elapsed out of 2h!
        assertTrue("Strict mode must remain active despite 48h wall jump", isActive)
        assertTrue("Clock tamper must be detected", tamperDetected)
        assertTrue("Wall difference must be massive", recordedWallDiff >= forward48hMs)
        assertEquals(realPassMs, recordedMonoDiff)

        val remainingMs = trustedClock.getRemainingSessionMs(session, spoofedSnapshot)
        // 2 hours (7,200,000) - 10 minutes (600,000) = 6,600,000 ms (1h 50m remaining)
        assertEquals(2 * 3600 * 1000L - realPassMs, remainingMs)
    }

    // ── Test 2: Reboot then clock forward -> still active ─────────────────────

    @Test
    fun `reboot then clock forward remains active and clamps powered-off duration`() {
        val initialSession = createBaseSession()

        // Session accumulates 30 minutes before shutdown
        val runningSnapshot = ClockSnapshot(
            elapsedRealtimeMs = initialSession.lastElapsedRealtime + 30 * 60 * 1000L,
            wallTimeMs = initialSession.lastWallTime + 30 * 60 * 1000L,
            bootCount = initialSession.bootCount
        )
        val checkpointed = trustedClock.checkpoint(initialSession, runningSnapshot)
        assertEquals(30 * 60 * 1000L, checkpointed.accumulatedMonotonicMs)

        // Phone is turned off, clock is manipulated forward by 48 hours, phone boots
        // Post-boot uptime is only 15 seconds (15,000 ms). Boot count incremented to 2.
        val postBootSnapshot = ClockSnapshot(
            elapsedRealtimeMs = 15_000L,
            wallTimeMs = checkpointed.lastWallTime + 48 * 3600 * 1000L,
            bootCount = checkpointed.bootCount + 1
        )

        val isActive = trustedClock.isSessionActive(checkpointed, postBootSnapshot)
        assertTrue("Session must remain active post-reboot despite 48h jump", isActive)

        val remainingMs = trustedClock.getRemainingSessionMs(checkpointed, postBootSnapshot)
        // Expected: 2h (7,200,000) - 30m before boot (1,800,000) - 15s post boot (15,000) = 5,385,000 ms (~1h 29m 45s)
        val expectedRemaining = (2 * 3600 * 1000L) - (30 * 60 * 1000L) - 15_000L
        assertEquals(expectedRemaining, remainingMs)
    }

    // ── Test 3: Normal expiry -> deactivates ──────────────────────────────────

    @Test
    fun `normal expiry deactivates gracefully when monotonic duration elapses`() {
        val session = createBaseSession(durationMs = 1 * 60 * 60 * 1000L) // 1 hour

        // Exactly 1 hour of real monotonic time passes
        val expiredSnapshot = ClockSnapshot(
            elapsedRealtimeMs = session.lastElapsedRealtime + 3600 * 1000L,
            wallTimeMs = session.lastWallTime + 3600 * 1000L,
            bootCount = session.bootCount
        )

        val remainingMs = trustedClock.getRemainingSessionMs(session, expiredSnapshot)
        assertEquals(0L, remainingMs)

        val isActive = trustedClock.isSessionActive(session, expiredSnapshot)
        assertFalse("Session must deactivate once target duration has elapsed", isActive)
    }

    // ── Test 4: Indefinite session remains active indefinitely ────────────────

    @Test
    fun `indefinite session remains active indefinitely without underflow`() {
        val indefiniteSession = StrictSessionEntity(
            id = 2,
            profileId = 1,
            startTime = 1_000_000L,
            targetEndTime = 0L, // Indefinite
            isActive = true,
            accumulatedMonotonicMs = 0L,
            lastElapsedRealtime = 50_000L,
            lastWallTime = 1_000_000L,
            bootCount = 1
        )

        val farFutureSnapshot = ClockSnapshot(
            elapsedRealtimeMs = 100_000_000L,
            wallTimeMs = 1_000_000_000L,
            bootCount = 1
        )

        assertTrue(trustedClock.isSessionActive(indefiniteSession, farFutureSnapshot))
        assertEquals(Long.MAX_VALUE, trustedClock.getRemainingSessionMs(indefiniteSession, farFutureSnapshot))
    }

    // ── Test 5: Backward clock tampering is detected ──────────────────────────

    @Test
    fun `backward clock roll is detected as tamper`() {
        val session = createBaseSession()
        var tamperDetected = false

        // User rolls clock backward by 1 day
        val backwardSnapshot = ClockSnapshot(
            elapsedRealtimeMs = session.lastElapsedRealtime + 10_000L,
            wallTimeMs = session.lastWallTime - 24 * 3600 * 1000L,
            bootCount = session.bootCount
        )

        trustedClock.computeAccumulatedMonotonicMs(session, backwardSnapshot) { _, _ ->
            tamperDetected = true
        }

        assertTrue("Backward clock tampering must be detected", tamperDetected)
    }

    // ── Test 6: Delayed unlock cannot be bypassed by clock roll-forward ───────

    @Test
    fun `delayed unlock cannot be bypassed by forward clock jump`() {
        val session = createBaseSession().copy(
            delayedUnlockRequestTime = 1_000_000L,
            delayedUnlockDurationMs = 24 * 3600 * 1000L, // 24h delay
            delayedUnlockStartAccumulatedMs = 0L
        )

        // 1 minute of real time passes, user sets clock 48 hours forward
        val spoofedSnapshot = ClockSnapshot(
            elapsedRealtimeMs = session.lastElapsedRealtime + 60_000L,
            wallTimeMs = session.lastWallTime + 48 * 3600 * 1000L,
            bootCount = session.bootCount
        )

        val remainingDelayMs = trustedClock.getRemainingDelayMs(session, spoofedSnapshot)
        // Must still have ~23h 59m remaining, not 0!
        assertEquals(24 * 3600 * 1000L - 60_000L, remainingDelayMs)
    }

    // ── Test 7: Delayed unlock finalizes after genuine monotonic delay ────────

    @Test
    fun `delayed unlock finalizes after genuine monotonic delay`() {
        val delay24h = 24 * 3600 * 1000L
        val session = createBaseSession().copy(
            delayedUnlockRequestTime = 1_000_000L,
            delayedUnlockDurationMs = delay24h,
            delayedUnlockStartAccumulatedMs = 0L
        )

        // 24 hours of genuine monotonic time elapses
        val completedSnapshot = ClockSnapshot(
            elapsedRealtimeMs = session.lastElapsedRealtime + delay24h,
            wallTimeMs = session.lastWallTime + delay24h,
            bootCount = session.bootCount
        )

        val remainingDelayMs = trustedClock.getRemainingDelayMs(session, completedSnapshot)
        assertEquals(0L, remainingDelayMs)
    }

    // ── Test 8: Reboot detection via elapsedRealtime rollback when bootCount=-1

    @Test
    fun `reboot detected via elapsedRealtime rollback when bootCount is unavailable`() {
        val session = createBaseSession(
            startElapsed = 50_000_000L,
            bootCount = -1 // Unreadable on OEM ROM
        ).copy(lastElapsedRealtime = 50_000_000L)

        // After reboot, bootCount is still -1, but elapsed is 10_000ms (< 50,000,000ms)
        val postBootSnapshot = ClockSnapshot(
            elapsedRealtimeMs = 10_000L,
            wallTimeMs = session.lastWallTime + 300_000L,
            bootCount = -1
        )

        assertTrue("Must detect reboot via elapsedRealtime rollback even if bootCount is -1",
            trustedClock.isReboot(session, postBootSnapshot))

        // Monotonic time must not freeze at 0; it should add uptime since boot (10,000 ms)
        val accumulated = trustedClock.computeAccumulatedMonotonicMs(session, postBootSnapshot)
        assertEquals(10_000L, accumulated)
    }
}
