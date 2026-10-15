package com.stayfocused.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.stayfocused.app.data.local.entities.BlockedUrlPathEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedUrlPathDao {

    @Query("SELECT * FROM blocked_url_paths WHERE isBlocked = 1")
    fun getAllBlockedUrlPaths(): Flow<List<BlockedUrlPathEntity>>

    @Query("SELECT * FROM blocked_url_paths ORDER BY createdAt DESC")
    fun getAllUrlPaths(): Flow<List<BlockedUrlPathEntity>>

    @Query("SELECT * FROM blocked_url_paths WHERE isBlocked = 1")
    suspend fun getAllBlockedUrlPathsSync(): List<BlockedUrlPathEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_url_paths WHERE pathPattern = :pathPattern AND isBlocked = 1)")
    suspend fun isUrlPathBlocked(pathPattern: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUrlPath(entity: BlockedUrlPathEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUrlPaths(entities: List<BlockedUrlPathEntity>)

    @Query("DELETE FROM blocked_url_paths WHERE pathPattern = :pathPattern")
    suspend fun deleteUrlPath(pathPattern: String)

    @Query("DELETE FROM blocked_url_paths")
    suspend fun clearAll()
}
