package com.stayfocused.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.stayfocused.app.data.local.entities.StrictSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StrictSessionDao {
    @Query("SELECT * FROM strict_sessions WHERE isActive = 1 ORDER BY startTime DESC LIMIT 1")
    fun getActiveStrictSession(): Flow<StrictSessionEntity?>

    @Query("SELECT * FROM strict_sessions WHERE isActive = 1 ORDER BY startTime DESC LIMIT 1")
    suspend fun getActiveStrictSessionSync(): StrictSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(entity: StrictSessionEntity): Long

    @Update
    suspend fun updateSession(entity: StrictSessionEntity)

    @Query("UPDATE strict_sessions SET isActive = 0 WHERE isActive = 1")
    suspend fun deactivateAllSessions()
}
