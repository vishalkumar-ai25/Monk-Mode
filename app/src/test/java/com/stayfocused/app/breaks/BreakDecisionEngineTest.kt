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
            durationMinutes = 10
        )
        assertEquals(1L, session.id)
        assertEquals(1_000_000L, session.startTime)
        assertEquals(1_000_000L + (10 * 60 * 1000L), session.endTime)
        assertEquals(10, session.durationMinutes)
        assertTrue(session.isActive)
    }
}
