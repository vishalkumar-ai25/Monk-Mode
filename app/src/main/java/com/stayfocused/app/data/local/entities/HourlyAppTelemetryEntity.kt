package com.stayfocused.app.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

/**
 * Persists hourly per-application usage metrics (foreground active screen time and intercepted short-form feeds).
 * Composite primary key: (epochDay, hourOfDay, packageName).
 */
@Entity(
    tableName = "hourly_app_telemetry",
    primaryKeys = ["epochDay", "hourOfDay", "packageName"],
    indices = [
        Index(value = ["epochDay", "hourOfDay"], name = "index_hourly_app_telemetry_epochDay_hourOfDay")
    ]
)
data class HourlyAppTelemetryEntity(
    val epochDay: Long,
    val hourOfDay: Int, // 0..23
    @ColumnInfo(collate = ColumnInfo.NOCASE)
    val packageName: String,
    val foregroundMs: Long = 0L,
    val shortsBlockedCount: Int = 0
)
