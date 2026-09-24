package com.stayfocused.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.stayfocused.app.data.local.entities.RecoveryCodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecoveryCodeDao {
    @Query("SELECT * FROM recovery_codes WHERE id = 1")
    fun getRecoveryCode(): Flow<RecoveryCodeEntity?>

    @Query("SELECT * FROM recovery_codes WHERE id = 1")
    suspend fun getRecoveryCodeSync(): RecoveryCodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecoveryCode(entity: RecoveryCodeEntity)

    @Query("UPDATE recovery_codes SET isConsumed = 1 WHERE id = 1")
    suspend fun markConsumed()
}
