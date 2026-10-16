package com.stayfocused.app.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.stayfocused.app.data.local.entities.HourlyAppTelemetryEntity
import com.stayfocused.app.data.local.entities.HourlyDeviceTelemetryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HourlyTelemetryDao {
    // 1. App Telemetry: Update foreground time while preserving shortsBlockedCount
    @Query("""
        INSERT INTO hourly_app_telemetry (epochDay, hourOfDay, packageName, foregroundMs, shortsBlockedCount)
        VALUES (:epochDay, :hourOfDay, :packageName, :foregroundMs, 0)
        ON CONFLICT(epochDay, hourOfDay, packageName)
        DO UPDATE SET foregroundMs = excluded.foregroundMs
    """)
    suspend fun upsertAppForegroundUsage(epochDay: Long, hourOfDay: Int, packageName: String, foregroundMs: Long)

    // 2. App Telemetry: Increment shortsBlockedCount atomically (creates row if none exists)
    @Query("""
        INSERT INTO hourly_app_telemetry (epochDay, hourOfDay, packageName, foregroundMs, shortsBlockedCount)
        VALUES (:epochDay, :hourOfDay, :packageName, 0, 1)
        ON CONFLICT(epochDay, hourOfDay, packageName)
        DO UPDATE SET shortsBlockedCount = hourly_app_telemetry.shortsBlockedCount + 1
    """)
    suspend fun incrementShortsBlocked(epochDay: Long, hourOfDay: Int, packageName: String)

    // 3. Device Telemetry: Atomic upsert of pickups and total screen time
    @Query("""
        INSERT INTO hourly_device_telemetry (epochDay, hourOfDay, pickupCount, totalScreenTimeMs)
        VALUES (:epochDay, :hourOfDay, :pickupCount, :totalScreenTimeMs)
        ON CONFLICT(epochDay, hourOfDay)
        DO UPDATE SET pickupCount = excluded.pickupCount, totalScreenTimeMs = excluded.totalScreenTimeMs
    """)
    suspend fun upsertDeviceTelemetry(epochDay: Long, hourOfDay: Int, pickupCount: Int, totalScreenTimeMs: Long)

    // Queries for UI & Analytics
    @Query("SELECT * FROM hourly_device_telemetry WHERE epochDay = :epochDay ORDER BY hourOfDay ASC")
    fun getHourlyDeviceTelemetryForDay(epochDay: Long): Flow<List<HourlyDeviceTelemetryEntity>>

    @Query("SELECT * FROM hourly_device_telemetry WHERE epochDay = :epochDay ORDER BY hourOfDay ASC")
    suspend fun getHourlyDeviceTelemetryForDaySync(epochDay: Long): List<HourlyDeviceTelemetryEntity>

    @Query("SELECT * FROM hourly_app_telemetry WHERE epochDay = :epochDay ORDER BY hourOfDay ASC, foregroundMs DESC")
    fun getHourlyAppTelemetryForDay(epochDay: Long): Flow<List<HourlyAppTelemetryEntity>>

    @Query("SELECT * FROM hourly_app_telemetry WHERE epochDay = :epochDay ORDER BY hourOfDay ASC, foregroundMs DESC")
    suspend fun getHourlyAppTelemetryForDaySync(epochDay: Long): List<HourlyAppTelemetryEntity>

    @Query("SELECT * FROM hourly_app_telemetry WHERE epochDay = :epochDay AND hourOfDay = :hourOfDay ORDER BY foregroundMs DESC")
    suspend fun getTopAppsForHour(epochDay: Long, hourOfDay: Int): List<HourlyAppTelemetryEntity>

    // 30-day retention prune (strictly accepts epochDay, NOT milliseconds!)
    @Query("DELETE FROM hourly_device_telemetry WHERE epochDay < :cutoffEpochDay")
    suspend fun pruneOldDeviceTelemetry(cutoffEpochDay: Long)

    @Query("DELETE FROM hourly_app_telemetry WHERE epochDay < :cutoffEpochDay")
    suspend fun pruneOldAppTelemetry(cutoffEpochDay: Long)
}
