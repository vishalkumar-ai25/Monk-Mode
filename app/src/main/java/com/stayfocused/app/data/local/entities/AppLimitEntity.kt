package com.stayfocused.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_limits")
data class AppLimitEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val dailyTimeLimitMinutes: Int = 0,
    val dailyLaunchLimit: Int = 0,
    val isBlocked: Boolean = false,
    val currentDayUsageMs: Long = 0L,
    val currentDayLaunches: Int = 0,
    val lastResetTimestamp: Long = System.currentTimeMillis()
)
