package com.stayfocused.app.breaks

import com.stayfocused.app.data.local.entities.BreakSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BreakDecisionEngineTest {

    private lateinit var engine: BreakDecisionEngine

    @Before
    fun setUp() {
        engine = BreakDecisionEngine()
    }

    @Test
    fun testBreakActiveWhenWithinTimeWindow() {
        val session = BreakSessionEntity(
            startTime = 1000L,
            endTime = 10_000L,
            durationMinutes = 5,
            isActive = true
        )
        val isActive = engine.isBreakActive(
            currentTimeMs = 5000L,
            breakSession = session,
            isStrictModeActive = false
        )
        assertTrue(isActive)

        val remainingSec = engine.calculateRemainingSeconds(
            currentTimeMs = 5000L,
            breakSession = session
        )
        assertEquals(5L, remainingSec)
    }

    @Test
    fun testBreakExpiredWhenPastEndTime() {
        val session = BreakSessionEntity(
            startTime = 1000L,
            endTime = 10_000L,
            durationMinutes = 5,
            isActive = true
        )
        val isActive = engine.isBreakActive(
            currentTimeMs = 10_001L,
            breakSession = session,
            isStrictModeActive = false
        )
        assertFalse(isActive)

        val remainingSec = engine.calculateRemainingSeconds(
            currentTimeMs = 10_001L,
            breakSession = session
        )
        assertEquals(0L, remainingSec)
    }

    @Test
    fun testBreakInactiveWhenExplicitlyDeactivated() {
        val session = BreakSessionEntity(
            startTime = 1000L,
            endTime = 10_000L,
            durationMinutes = 5,
            isActive = false // Explicitly deactivated
        )
        val isActive = engine.isBreakActive(
            currentTimeMs = 5000L,
            breakSession = session,
            isStrictModeActive = false
        )
        assertFalse(isActive)
    }

    @Test
    fun testBreakForbiddenDuringStrictMode() {
        val session = BreakSessionEntity(
            startTime = 1000L,
            endTime = 10_000L,
            durationMinutes = 5,
            isActive = true
        )
        // Strict Mode is ACTIVE!
        val isActive = engine.isBreakActive(
            currentTimeMs = 5000L,
            breakSession = session,
            isStrictModeActive = true
        )
        assertFalse("Breaks must be strictly forbidden during strict sessions", isActive)
    }

    @Test
    fun testCreateBreakSessionCalculatesCorrectEndTime() {
        val session = engine.createBreakSession(
            currentTimeMs = 1_000_000L,
            durationMinutes = 10,
            reason = "Quick test break"
        )
        assertEquals(1L, session.id)
        assertEquals(1_000_000L, session.startTime)
        assertEquals(1_000_000L + (10 * 60 * 1000L), session.endTime)
        assertEquals(10, session.durationMinutes)
        assertEquals("Quick test break", session.reason)
        assertTrue(session.isActive)
    }

    @Test
    fun testCanStartBreakWithValidReasonWhenNotStrict() {
        val allowed = engine.canStartBreak(
            reason = "Need to check important bank SMS",
            isStrictModeActive = false
        )
        assertTrue("Break must be allowed with non-empty reason when strict mode is inactive", allowed)
    }

    @Test
    fun testCanStartBreakRejectsEmptyOrWhitespaceReason() {
        assertFalse("Empty reason must be rejected", engine.canStartBreak("", isStrictModeActive = false))
        assertFalse("Whitespace-only reason must be rejected", engine.canStartBreak("   \t\n  ", isStrictModeActive = false))
    }

    @Test
    fun testCanStartBreakRejectsWhenStrictModeActiveEvenWithReason() {
        val allowed = engine.canStartBreak(
            reason = "Urgent call",
            isStrictModeActive = true
        )
        assertFalse("Strict mode must override and reject break even if reason is provided", allowed)
    }

    @Test
    fun testCreateBreakSessionStoresTrimmedReason() {
        val session = engine.createBreakSession(
            currentTimeMs = 1_000_000L,
            durationMinutes = 15,
            reason = "   Emergency family call   "
        )
        assertEquals("Emergency family call", session.reason)
        assertEquals(15, session.durationMinutes)
        assertTrue(session.isActive)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCreateBreakSessionThrowsWhenReasonIsEmpty() {
        engine.createBreakSession(
            currentTimeMs = 1_000_000L,
            durationMinutes = 5,
            reason = ""
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testCreateBreakSessionThrowsWhenReasonIsWhitespace() {
        engine.createBreakSession(
            currentTimeMs = 1_000_000L,
            durationMinutes = 5,
            reason = "     "
        )
    }
}
