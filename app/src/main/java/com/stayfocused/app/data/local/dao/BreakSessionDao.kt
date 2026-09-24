package com.stayfocused.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.stayfocused.app.data.local.entities.BreakSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BreakSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBreak(session: BreakSessionEntity)

    @Query("SELECT * FROM break_sessions WHERE id = 1 AND isActive = 1 LIMIT 1")
    fun getActiveBreak(): Flow<BreakSessionEntity?>

    @Query("SELECT * FROM break_sessions WHERE id = 1 AND isActive = 1 LIMIT 1")
    suspend fun getActiveBreakSync(): BreakSessionEntity?

    @Query("UPDATE break_sessions SET isActive = 0 WHERE id = 1")
    suspend fun deactivateBreak()
}
