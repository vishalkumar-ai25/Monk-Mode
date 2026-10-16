package com.stayfocused.app.domain

import com.stayfocused.app.domain.model.PomodoroConfig
import com.stayfocused.app.domain.model.PomodoroPhase
import com.stayfocused.app.domain.model.PomodoroState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PomodoroStateMachineTest {

    private val defaultConfig = PomodoroConfig(
        workMinutes = 25,
        shortBreakMinutes = 5,
        longBreakMinutes = 15,
        longBreakInterval = 4,
        autoStartBreaks = true,
        autoStartWork = true
    )

    private val manualConfig = PomodoroConfig(
        workMinutes = 25,
        shortBreakMinutes = 5,
        longBreakMinutes = 15,
        longBreakInterval = 4,
        autoStartBreaks = false,
        autoStartWork = false
    )

    @Test
    fun testStartWorkInitializesWorkStateWithExactDuration() {
        val sm = PomodoroStateMachine()
        val tElapsed = 100_000L
        val tWall = 1_700_000_000_000L
        val bootCount = 1

        val state = sm.startWork(defaultConfig, tElapsed, tWall, bootCount, cycle = 1)

        assertEquals(PomodoroPhase.WORK, state.phase)
        assertEquals(1, state.currentCycle)
        assertEquals(4, state.totalCycles)
        assertEquals(25 * 60_000L, state.remainingMs)
        assertEquals(25 * 60_000L, state.totalDurationMs)
        assertTrue(state.isRunning)
        assertEquals(tElapsed + 25 * 60_000L, state.targetElapsedRealtimeMs)
        assertEquals(tWall + 25 * 60_000L, state.targetWallTimeMs)
        assertEquals(1, state.bootCount)
        assertNull(state.pausedPhase)
    }

    @Test
    fun testTickDecrementsRemainingTime() {
        val sm = PomodoroStateMachine()
        val tElapsed = 100_000L
        val tWall = 1_700_000_000_000L
        val bootCount = 1

        sm.startWork(defaultConfig, tElapsed, tWall, bootCount, cycle = 1)

        // 5 minutes later
        val state = sm.tick(tElapsed + 5 * 60_000L, tWall + 5 * 60_000L, bootCount, defaultConfig)

        assertEquals(PomodoroPhase.WORK, state.phase)
        assertEquals(20 * 60_000L, state.remainingMs)
        assertTrue(state.isRunning)
    }

    @Test
    fun testPausePreservesRemainingTimeAndZeroesTargets() {
        val sm = PomodoroStateMachine()
        val tElapsed = 100_000L
        val tWall = 1_700_000_000_000L
        val bootCount = 1

        sm.startWork(defaultConfig, tElapsed, tWall, bootCount, cycle = 1)

        // Pause after 10 minutes
        val paused = sm.pause(tElapsed + 10 * 60_000L, tWall + 10 * 60_000L, bootCount)

        assertEquals(PomodoroPhase.PAUSED, paused.phase)
        assertEquals(PomodoroPhase.WORK, paused.pausedPhase)
        assertEquals(15 * 60_000L, paused.remainingMs)
        assertFalse(paused.isRunning)
        assertEquals(0L, paused.targetElapsedRealtimeMs)
        assertEquals(0L, paused.targetWallTimeMs)

        // Ticking while paused does not change remaining time
        val afterTick = sm.tick(tElapsed + 20 * 60_000L, tWall + 20 * 60_000L, bootCount, defaultConfig)
        assertEquals(15 * 60_000L, afterTick.remainingMs)
        assertEquals(PomodoroPhase.PAUSED, afterTick.phase)
    }

    @Test
    fun testResumeRestoresRunningStateWithNewTargetTimes() {
        val sm = PomodoroStateMachine()
        val tElapsed = 100_000L
        val tWall = 1_700_000_000_000L
        val bootCount = 1

        sm.startWork(defaultConfig, tElapsed, tWall, bootCount, cycle = 1)
        sm.pause(tElapsed + 10 * 60_000L, tWall + 10 * 60_000L, bootCount) // 15 mins remaining

        // Resumed 30 minutes later
        val resumeElapsed = tElapsed + 40 * 60_000L
        val resumeWall = tWall + 40 * 60_000L
        val resumed = sm.resume(resumeElapsed, resumeWall, bootCount)

        assertEquals(PomodoroPhase.WORK, resumed.phase)
        assertTrue(resumed.isRunning)
        assertNull(resumed.pausedPhase)
        assertEquals(15 * 60_000L, resumed.remainingMs)
        assertEquals(resumeElapsed + 15 * 60_000L, resumed.targetElapsedRealtimeMs)
        assertEquals(resumeWall + 15 * 60_000L, resumed.targetWallTimeMs)
    }

    @Test
    fun testWorkPhaseTransitionsToShortBreakWhenAutoStartTrue() {
        val sm = PomodoroStateMachine()
        val tElapsed = 100_000L
        val tWall = 1_700_000_000_000L
        val bootCount = 1

        sm.startWork(defaultConfig, tElapsed, tWall, bootCount, cycle = 1)

        // 25 minutes elapsed
        val next = sm.tick(tElapsed + 25 * 60_000L, tWall + 25 * 60_000L, bootCount, defaultConfig)

        assertEquals(PomodoroPhase.SHORT_BREAK, next.phase)
        assertEquals(1, next.currentCycle)
        assertTrue(next.isRunning)
        assertEquals(5 * 60_000L, next.remainingMs)
        assertEquals(5 * 60_000L, next.totalDurationMs)
    }

    @Test
    fun testWorkPhaseTransitionsToPausedShortBreakWhenAutoStartFalse() {
        val sm = PomodoroStateMachine()
        val tElapsed = 100_000L
        val tWall = 1_700_000_000_000L
        val bootCount = 1

        sm.startWork(manualConfig, tElapsed, tWall, bootCount, cycle = 1)

        // 25 minutes elapsed
        val next = sm.tick(tElapsed + 25 * 60_000L, tWall + 25 * 60_000L, bootCount, manualConfig)

        assertEquals(PomodoroPhase.PAUSED, next.phase)
        assertEquals(PomodoroPhase.SHORT_BREAK, next.pausedPhase)
        assertFalse(next.isRunning)
        assertEquals(5 * 60_000L, next.remainingMs)
    }

    @Test
    fun testCycle4TransitionsToLongBreak() {
        val sm = PomodoroStateMachine()
        val tElapsed = 100_000L
        val tWall = 1_700_000_000_000L
        val bootCount = 1

        sm.startWork(defaultConfig, tElapsed, tWall, bootCount, cycle = 4)

        // 25 minutes elapsed on cycle 4
        val next = sm.tick(tElapsed + 25 * 60_000L, tWall + 25 * 60_000L, bootCount, defaultConfig)

        assertEquals(PomodoroPhase.LONG_BREAK, next.phase)
        assertEquals(4, next.currentCycle)
        assertTrue(next.isRunning)
        assertEquals(15 * 60_000L, next.remainingMs)
        assertEquals(15 * 60_000L, next.totalDurationMs)
    }

    @Test
    fun testLongBreakTransitionsBackToCycle1Work() {
        val sm = PomodoroStateMachine()
        val tElapsed = 100_000L
        val tWall = 1_700_000_000_000L
        val bootCount = 1

        sm.startWork(defaultConfig, tElapsed, tWall, bootCount, cycle = 4)
        val longBreak = sm.tick(tElapsed + 25 * 60_000L, tWall + 25 * 60_000L, bootCount, defaultConfig)
        assertEquals(PomodoroPhase.LONG_BREAK, longBreak.phase)

        // Long break finishes after 15 minutes
        val breakEndElapsed = tElapsed + 25 * 60_000L + 15 * 60_000L
        val breakEndWall = tWall + 25 * 60_000L + 15 * 60_000L
        val workCycle1 = sm.tick(breakEndElapsed, breakEndWall, bootCount, defaultConfig)

        assertEquals(PomodoroPhase.WORK, workCycle1.phase)
        assertEquals(1, workCycle1.currentCycle)
        assertTrue(workCycle1.isRunning)
        assertEquals(25 * 60_000L, workCycle1.remainingMs)
    }

    @Test
    fun testSkipAdvancesPhaseImmediately() {
        val sm = PomodoroStateMachine()
        val tElapsed = 100_000L
        val tWall = 1_700_000_000_000L
        val bootCount = 1

        sm.startWork(defaultConfig, tElapsed, tWall, bootCount, cycle = 2)

        val skipped = sm.skip(defaultConfig, tElapsed + 1000L, tWall + 1000L, bootCount)

        assertEquals(PomodoroPhase.SHORT_BREAK, skipped.phase)
        assertEquals(2, skipped.currentCycle)
        assertEquals(5 * 60_000L, skipped.remainingMs)
    }

    @Test
    fun testResetRestoresIdleState() {
        val sm = PomodoroStateMachine()
        val tElapsed = 100_000L
        val tWall = 1_700_000_000_000L
        val bootCount = 1

        sm.startWork(defaultConfig, tElapsed, tWall, bootCount, cycle = 2)
        val reset = sm.reset(totalCycles = 4)

        assertEquals(PomodoroPhase.IDLE, reset.phase)
        assertFalse(reset.isRunning)
        assertEquals(0L, reset.remainingMs)
        assertEquals(1, reset.currentCycle)
    }

    @Test
    fun testRebootRecoveryUsesWallClockFallback() {
        val sm = PomodoroStateMachine()
        val tElapsed = 100_000L
        val tWall = 1_700_000_000_000L
        val bootCount = 1

        val state = sm.startWork(defaultConfig, tElapsed, tWall, bootCount, cycle = 1)

        // Simulate reboot: bootCount becomes 2, elapsedRealtime resets to 5_000ms, wall clock advances 10 minutes
        val postRebootBootCount = 2
        val postRebootElapsed = 5_000L
        val postRebootWall = tWall + 10 * 60_000L // 10 minutes passed in real world

        val remaining = PomodoroStateMachine.calculateRemainingMs(
            state,
            postRebootElapsed,
            postRebootWall,
            postRebootBootCount
        )

        // Expected: 25 mins - 10 mins = 15 mins left
        assertEquals(15 * 60_000L, remaining)
    }

    @Test
    fun testFormatRemainingTime() {
        val state = PomodoroState(remainingMs = 25 * 60_000L + 5_000L) // 25:05
        assertEquals("25:05", state.formattedRemainingTime)

        val stateSeconds = PomodoroState(remainingMs = 45_000L) // 00:45
        assertEquals("00:45", stateSeconds.formattedRemainingTime)

        val zero = PomodoroState(remainingMs = 0L)
        assertEquals("00:00", zero.formattedRemainingTime)
    }
}
