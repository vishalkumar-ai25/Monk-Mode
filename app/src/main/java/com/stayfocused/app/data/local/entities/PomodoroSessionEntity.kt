package com.stayfocused.app.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pomodoro_session")
data class PomodoroSessionEntity(
    @PrimaryKey
    val id: Int = 1,
    @ColumnInfo(name = "phaseName")
    val phaseName: String = "IDLE",
    @ColumnInfo(name = "currentCycle")
    val currentCycle: Int = 1,
    @ColumnInfo(name = "targetElapsedRealtimeMs")
    val targetElapsedRealtimeMs: Long = 0L,
    @ColumnInfo(name = "targetWallTimeMs")
    val targetWallTimeMs: Long = 0L,
    @ColumnInfo(name = "totalDurationMs")
    val totalDurationMs: Long = 0L,
    @ColumnInfo(name = "remainingMs")
    val remainingMs: Long = 0L,
    @ColumnInfo(name = "bootCount")
    val bootCount: Int = 0,
    @ColumnInfo(name = "isRunning")
    val isRunning: Boolean = false,
    @ColumnInfo(name = "originalDndFilter")
    val originalDndFilter: Int = 1
)
