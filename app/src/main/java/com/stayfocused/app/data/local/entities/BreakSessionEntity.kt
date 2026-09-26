package com.stayfocused.app.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "break_sessions")
data class BreakSessionEntity(
    @PrimaryKey val id: Long = 1L, // Singleton record
    val startTime: Long,
    val endTime: Long,
    val durationMinutes: Int,
    val isActive: Boolean,
    @ColumnInfo(name = "reason") val reason: String = ""
)
