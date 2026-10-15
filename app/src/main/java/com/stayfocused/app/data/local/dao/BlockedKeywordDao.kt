package com.stayfocused.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.stayfocused.app.data.local.entities.BlockedKeywordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedKeywordDao {

    @Query("SELECT * FROM blocked_keywords WHERE isBlocked = 1")
    fun getAllBlockedKeywords(): Flow<List<BlockedKeywordEntity>>

    @Query("SELECT * FROM blocked_keywords ORDER BY createdAt DESC")
    fun getAllKeywords(): Flow<List<BlockedKeywordEntity>>

    @Query("SELECT * FROM blocked_keywords WHERE isBlocked = 1")
    suspend fun getAllBlockedKeywordsSync(): List<BlockedKeywordEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_keywords WHERE keyword = :keyword AND isBlocked = 1)")
    suspend fun isKeywordBlocked(keyword: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeyword(entity: BlockedKeywordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeywords(entities: List<BlockedKeywordEntity>)

    @Query("DELETE FROM blocked_keywords WHERE keyword = :keyword")
    suspend fun deleteKeyword(keyword: String)

    @Query("DELETE FROM blocked_keywords")
    suspend fun clearAll()
}
