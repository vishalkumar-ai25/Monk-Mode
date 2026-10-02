package com.stayfocused.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.stayfocused.app.data.local.entities.StrictScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StrictScheduleDao {
    @Query("SELECT * FROM strict_schedules ORDER BY startMinuteOfDay ASC")
    fun getAllSchedules(): Flow<List<StrictScheduleEntity>>

    @Query("SELECT * FROM strict_schedules ORDER BY startMinuteOfDay ASC")
    suspend fun getAllSchedulesSync(): List<StrictScheduleEntity>

    @Query("SELECT * FROM strict_schedules WHERE isEnabled = 1 ORDER BY startMinuteOfDay ASC")
    suspend fun getEnabledSchedulesSync(): List<StrictScheduleEntity>

    @Query("SELECT * FROM strict_schedules WHERE id = :id")
    suspend fun getScheduleByIdSync(id: Long): StrictScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(entity: StrictScheduleEntity): Long

    @Update
    suspend fun updateSchedule(entity: StrictScheduleEntity)

    @Query("UPDATE strict_schedules SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun setScheduleEnabled(id: Long, isEnabled: Boolean)

    @Query("UPDATE strict_schedules SET dismissedUntilEpochMs = :untilEpochMs WHERE id = :id")
    suspend fun setDismissedUntil(id: Long, untilEpochMs: Long)

    @Query("DELETE FROM strict_schedules WHERE id = :id")
    suspend fun deleteSchedule(id: Long)
}
