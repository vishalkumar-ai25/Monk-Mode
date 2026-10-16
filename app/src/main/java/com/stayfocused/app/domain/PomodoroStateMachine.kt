package com.stayfocused.app.domain

import com.stayfocused.app.domain.model.PomodoroConfig
import com.stayfocused.app.domain.model.PomodoroPhase
import com.stayfocused.app.domain.model.PomodoroState

/**
 * Pure Kotlin Pomodoro Domain State Machine.
 *
 * Guarantees:
 * 1. 100% JVM unit testable without Android framework mocks.
 * 2. Hardware-monotonic progress: tracks targetElapsedRealtimeMs and targetWallTimeMs.
 * 3. Reboot-immune: detects bootCount mismatch and falls back safely to wall time.
 * 4. Auto-start contract: if autoStartBreaks or autoStartWork is false, pauses at the boundary.
 */
class PomodoroStateMachine(
    initialState: PomodoroState = PomodoroState()
) {
    var state: PomodoroState = initialState
        private set

    fun startWork(
        config: PomodoroConfig,
        currentElapsedRealtimeMs: Long,
        currentWallTimeMs: Long,
        currentBootCount: Int,
        cycle: Int = 1
    ): PomodoroState {
        val durationMs = config.workDurationMs
        state = PomodoroState(
            phase = PomodoroPhase.WORK,
            currentCycle = cycle,
            totalCycles = config.longBreakInterval,
            remainingMs = durationMs,
            totalDurationMs = durationMs,
            isRunning = true,
            targetElapsedRealtimeMs = currentElapsedRealtimeMs + durationMs,
            targetWallTimeMs = currentWallTimeMs + durationMs,
            bootCount = currentBootCount,
            pausedPhase = null
        )
        return state
    }

    fun pause(
        currentElapsedRealtimeMs: Long,
        currentWallTimeMs: Long,
        currentBootCount: Int
    ): PomodoroState {
        if (!state.isRunning || state.phase == PomodoroPhase.IDLE || state.phase == PomodoroPhase.PAUSED) {
            return state
        }
        val remaining = calculateRemainingMs(state, currentElapsedRealtimeMs, currentWallTimeMs, currentBootCount)
        state = state.copy(
            phase = PomodoroPhase.PAUSED,
            pausedPhase = state.phase,
            isRunning = false,
            remainingMs = remaining,
            targetElapsedRealtimeMs = 0L,
            targetWallTimeMs = 0L
        )
        return state
    }

    fun resume(
        currentElapsedRealtimeMs: Long,
        currentWallTimeMs: Long,
        currentBootCount: Int
    ): PomodoroState {
        if (state.phase != PomodoroPhase.PAUSED) {
            return state
        }
        val targetPhase = state.pausedPhase ?: PomodoroPhase.WORK
        val remaining = state.remainingMs.coerceAtLeast(0L)
        state = state.copy(
            phase = targetPhase,
            isRunning = true,
            targetElapsedRealtimeMs = currentElapsedRealtimeMs + remaining,
            targetWallTimeMs = currentWallTimeMs + remaining,
            bootCount = currentBootCount,
            pausedPhase = null
        )
        return state
    }

    fun tick(
        currentElapsedRealtimeMs: Long,
        currentWallTimeMs: Long,
        currentBootCount: Int,
        config: PomodoroConfig
    ): PomodoroState {
        if (!state.isRunning || state.phase == PomodoroPhase.IDLE || state.phase == PomodoroPhase.PAUSED) {
            return state
        }
        val remaining = calculateRemainingMs(state, currentElapsedRealtimeMs, currentWallTimeMs, currentBootCount)
        return if (remaining <= 0L) {
            advancePhase(config, currentElapsedRealtimeMs, currentWallTimeMs, currentBootCount)
        } else {
            state = state.copy(remainingMs = remaining)
            state
        }
    }

    fun onAlarmElapsed(
        config: PomodoroConfig,
        currentElapsedRealtimeMs: Long,
        currentWallTimeMs: Long,
        currentBootCount: Int
    ): PomodoroState {
        if (!state.isRunning || state.phase == PomodoroPhase.IDLE) {
            return state
        }
        return advancePhase(config, currentElapsedRealtimeMs, currentWallTimeMs, currentBootCount)
    }

    fun skip(
        config: PomodoroConfig,
        currentElapsedRealtimeMs: Long,
        currentWallTimeMs: Long,
        currentBootCount: Int
    ): PomodoroState {
        if (state.phase == PomodoroPhase.IDLE) {
            return state
        }
        return advancePhase(config, currentElapsedRealtimeMs, currentWallTimeMs, currentBootCount, forceAutoStart = state.isRunning)
    }

    fun reset(totalCycles: Int = 4): PomodoroState {
        state = PomodoroState(
            phase = PomodoroPhase.IDLE,
            currentCycle = 1,
            totalCycles = totalCycles,
            remainingMs = 0L,
            totalDurationMs = 0L,
            isRunning = false,
            targetElapsedRealtimeMs = 0L,
            targetWallTimeMs = 0L,
            bootCount = 0,
            pausedPhase = null
        )
        return state
    }

    fun restoreSession(
        restoredState: PomodoroState,
        currentElapsedRealtimeMs: Long,
        currentWallTimeMs: Long,
        currentBootCount: Int,
        config: PomodoroConfig
    ): PomodoroState {
        state = if (restoredState.isRunning) {
            val remaining = calculateRemainingMs(restoredState, currentElapsedRealtimeMs, currentWallTimeMs, currentBootCount)
            if (remaining <= 0L) {
                // Interval elapsed while app was killed
                restoredState.copy(remainingMs = 0L)
            } else {
                restoredState.copy(
                    remainingMs = remaining,
                    targetElapsedRealtimeMs = currentElapsedRealtimeMs + remaining,
                    targetWallTimeMs = currentWallTimeMs + remaining,
                    bootCount = currentBootCount
                )
            }
        } else {
            restoredState
        }
        return state
    }

    private fun advancePhase(
        config: PomodoroConfig,
        currentElapsedRealtimeMs: Long,
        currentWallTimeMs: Long,
        currentBootCount: Int,
        forceAutoStart: Boolean = false
    ): PomodoroState {
        val activePhase = if (state.phase == PomodoroPhase.PAUSED) state.pausedPhase ?: PomodoroPhase.WORK else state.phase

        when (activePhase) {
            PomodoroPhase.WORK -> {
                val isLongBreak = state.currentCycle >= config.longBreakInterval
                val nextPhase = if (isLongBreak) PomodoroPhase.LONG_BREAK else PomodoroPhase.SHORT_BREAK
                val durationMs = if (isLongBreak) config.longBreakDurationMs else config.shortBreakDurationMs
                val shouldStart = forceAutoStart || config.autoStartBreaks

                state = if (shouldStart) {
                    PomodoroState(
                        phase = nextPhase,
                        currentCycle = state.currentCycle,
                        totalCycles = config.longBreakInterval,
                        remainingMs = durationMs,
                        totalDurationMs = durationMs,
                        isRunning = true,
                        targetElapsedRealtimeMs = currentElapsedRealtimeMs + durationMs,
                        targetWallTimeMs = currentWallTimeMs + durationMs,
                        bootCount = currentBootCount,
                        pausedPhase = null
                    )
                } else {
                    PomodoroState(
                        phase = PomodoroPhase.PAUSED,
                        currentCycle = state.currentCycle,
                        totalCycles = config.longBreakInterval,
                        remainingMs = durationMs,
                        totalDurationMs = durationMs,
                        isRunning = false,
                        targetElapsedRealtimeMs = 0L,
                        targetWallTimeMs = 0L,
                        bootCount = currentBootCount,
                        pausedPhase = nextPhase
                    )
                }
            }
            PomodoroPhase.SHORT_BREAK -> {
                val nextCycle = state.currentCycle + 1
                val durationMs = config.workDurationMs
                val shouldStart = forceAutoStart || config.autoStartWork

                state = if (shouldStart) {
                    PomodoroState(
                        phase = PomodoroPhase.WORK,
                        currentCycle = nextCycle,
                        totalCycles = config.longBreakInterval,
                        remainingMs = durationMs,
                        totalDurationMs = durationMs,
                        isRunning = true,
                        targetElapsedRealtimeMs = currentElapsedRealtimeMs + durationMs,
                        targetWallTimeMs = currentWallTimeMs + durationMs,
                        bootCount = currentBootCount,
                        pausedPhase = null
                    )
                } else {
                    PomodoroState(
                        phase = PomodoroPhase.PAUSED,
                        currentCycle = nextCycle,
                        totalCycles = config.longBreakInterval,
                        remainingMs = durationMs,
                        totalDurationMs = durationMs,
                        isRunning = false,
                        targetElapsedRealtimeMs = 0L,
                        targetWallTimeMs = 0L,
                        bootCount = currentBootCount,
                        pausedPhase = PomodoroPhase.WORK
                    )
                }
            }
            PomodoroPhase.LONG_BREAK -> {
                // Completed full set of cycles; reset to cycle 1
                val nextCycle = 1
                val durationMs = config.workDurationMs
                val shouldStart = forceAutoStart || config.autoStartWork

                state = if (shouldStart) {
                    PomodoroState(
                        phase = PomodoroPhase.WORK,
                        currentCycle = nextCycle,
                        totalCycles = config.longBreakInterval,
                        remainingMs = durationMs,
                        totalDurationMs = durationMs,
                        isRunning = true,
                        targetElapsedRealtimeMs = currentElapsedRealtimeMs + durationMs,
                        targetWallTimeMs = currentWallTimeMs + durationMs,
                        bootCount = currentBootCount,
                        pausedPhase = null
                    )
                } else {
                    PomodoroState(
                        phase = PomodoroPhase.PAUSED,
                        currentCycle = nextCycle,
                        totalCycles = config.longBreakInterval,
                        remainingMs = durationMs,
                        totalDurationMs = durationMs,
                        isRunning = false,
                        targetElapsedRealtimeMs = 0L,
                        targetWallTimeMs = 0L,
                        bootCount = currentBootCount,
                        pausedPhase = PomodoroPhase.WORK
                    )
                }
            }
            PomodoroPhase.IDLE, PomodoroPhase.PAUSED -> {
                // No-op
            }
        }
        return state
    }

    companion object {
        fun calculateRemainingMs(
            state: PomodoroState,
            currentElapsedRealtimeMs: Long,
            currentWallTimeMs: Long,
            currentBootCount: Int
        ): Long {
            if (!state.isRunning) return state.remainingMs

            return if (currentBootCount == state.bootCount && state.targetElapsedRealtimeMs > 0L) {
                maxOf(0L, state.targetElapsedRealtimeMs - currentElapsedRealtimeMs)
            } else if (state.targetWallTimeMs > 0L) {
                // Reboot occurred: fall back safely to wall clock
                maxOf(0L, state.targetWallTimeMs - currentWallTimeMs)
            } else {
                state.remainingMs
            }
        }
    }
}
