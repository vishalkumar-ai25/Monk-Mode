package com.stayfocused.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.stayfocused.app.data.local.entities.FailsafeLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * Append-only Data Access Object for failsafe integrity audit logs.
 * Intentionally contains ZERO delete or update operations to guarantee an
 * immutable, tamper-evident audit trail under Strict Mode.
 */
@Dao
interface FailsafeLogDao {

    @Insert
    suspend fun insertLog(log: FailsafeLogEntity): Long

    @Query("SELECT * FROM failsafe_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<FailsafeLogEntity>>

    @Query("SELECT * FROM failsafe_logs ORDER BY timestamp DESC")
    suspend fun getAllLogsSync(): List<FailsafeLogEntity>

    @Query("SELECT COUNT(*) FROM failsafe_logs")
    suspend fun getLogCount(): Int
}
