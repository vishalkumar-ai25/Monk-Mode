package com.stayfocused.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.stayfocused.app.data.local.entities.AppLimitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLimitDao {
    @Query("SELECT * FROM app_limits")
    fun getAllAppLimits(): Flow<List<AppLimitEntity>>

    @Query("SELECT * FROM app_limits")
    suspend fun getAllAppLimitsSync(): List<AppLimitEntity>

    @Query("SELECT * FROM app_limits WHERE packageName = :packageName")
    fun getAppLimit(packageName: String): Flow<AppLimitEntity?>

    @Query("SELECT * FROM app_limits WHERE packageName = :packageName")
    fun getAppLimitSync(packageName: String): AppLimitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppLimit(entity: AppLimitEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppLimits(entities: List<AppLimitEntity>)

    @Query("DELETE FROM app_limits WHERE packageName = :packageName")
    suspend fun deleteAppLimit(packageName: String)

    @Query("UPDATE app_limits SET currentDayUsageMs = 0, currentDayLaunches = 0, lastResetTimestamp = :timestamp")
    suspend fun resetDailyUsage(timestamp: Long)

    @Query("UPDATE app_limits SET currentDayUsageMs = :usageMs, currentDayLaunches = :launches WHERE packageName = :packageName")
    suspend fun updateUsageAndLaunches(packageName: String, usageMs: Long, launches: Int)

    @Query("SELECT COALESCE(SUM(currentDayLaunches), 0) FROM app_limits WHERE lastResetTimestamp >= :since AND (isBlocked = 1 OR (dailyLaunchLimit > 0 AND currentDayLaunches >= dailyLaunchLimit) OR (dailyTimeLimitMinutes > 0 AND currentDayUsageMs >= (dailyTimeLimitMinutes * 60000)))")
    suspend fun getBlockedAppLaunchesCountSince(since: Long): Int
}
