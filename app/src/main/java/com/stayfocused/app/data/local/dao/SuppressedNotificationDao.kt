package com.stayfocused.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.stayfocused.app.data.local.entities.SuppressedNotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SuppressedNotificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: SuppressedNotificationEntity): Long

    @Query("SELECT * FROM suppressed_notifications ORDER BY postTimestamp DESC")
    fun getAll(): Flow<List<SuppressedNotificationEntity>>

    @Query("SELECT COUNT(*) FROM suppressed_notifications WHERE isViewed = 0")
    fun getUnviewedCount(): Flow<Int>

    @Query("UPDATE suppressed_notifications SET isViewed = 1 WHERE isViewed = 0")
    suspend fun markAllAsViewed()

    @Query("DELETE FROM suppressed_notifications WHERE postTimestamp < :thresholdTimestamp")
    suspend fun clearOlderThan(thresholdTimestamp: Long)

    @Query("DELETE FROM suppressed_notifications")
    suspend fun clearAll()
}
