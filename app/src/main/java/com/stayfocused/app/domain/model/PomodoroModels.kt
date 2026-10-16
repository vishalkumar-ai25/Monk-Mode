package com.stayfocused.app.domain.model

enum class PomodoroPhase {
    IDLE,
    WORK,
    SHORT_BREAK,
    LONG_BREAK,
    PAUSED
}

data class PomodoroConfig(
    val workMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val longBreakInterval: Int = 4,
    val autoStartBreaks: Boolean = false,
    val autoStartWork: Boolean = false,
    val isDndEnabled: Boolean = false,
    val linkedProfileId: Long? = null
) {
    val workDurationMs: Long get() = workMinutes * 60_000L
    val shortBreakDurationMs: Long get() = shortBreakMinutes * 60_000L
    val longBreakDurationMs: Long get() = longBreakMinutes * 60_000L
}

data class PomodoroState(
    val phase: PomodoroPhase = PomodoroPhase.IDLE,
    val currentCycle: Int = 1,
    val totalCycles: Int = 4,
    val remainingMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val isRunning: Boolean = false,
    val targetElapsedRealtimeMs: Long = 0L,
    val targetWallTimeMs: Long = 0L,
    val bootCount: Int = 0,
    val pausedPhase: PomodoroPhase? = null
) {
    val progressFraction: Float
        get() = if (totalDurationMs > 0) {
            ((totalDurationMs - remainingMs).toFloat() / totalDurationMs).coerceIn(0f, 1f)
        } else 0f

    val formattedRemainingTime: String
        get() {
            val totalSeconds = (remainingMs / 1000L).coerceAtLeast(0L)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
}
