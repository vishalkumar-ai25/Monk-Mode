package com.stayfocused.app.data.local.entities

import androidx.room.Entity
import androidx.room.Index

/**
 * Persists hourly device-level wellbeing metrics (total screen time in ms and pickup count).
 * Composite primary key: (epochDay, hourOfDay).
 */
@Entity(
    tableName = "hourly_device_telemetry",
    primaryKeys = ["epochDay", "hourOfDay"],
    indices = [
        Index(value = ["epochDay"], name = "index_hourly_device_telemetry_epochDay")
    ]
)
data class HourlyDeviceTelemetryEntity(
    val epochDay: Long,
    val hourOfDay: Int, // 0..23
    val pickupCount: Int = 0,
    val totalScreenTimeMs: Long = 0L
)
