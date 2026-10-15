package com.stayfocused.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.stayfocused.app.data.local.entities.BrowsingHistoryEntity
import com.stayfocused.app.data.local.entities.DomainDwellSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowsingHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: BrowsingHistoryEntity): Long

    @Query("SELECT * FROM browsing_history ORDER BY timestampMs DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 100): Flow<List<BrowsingHistoryEntity>>

    @Query("SELECT * FROM browsing_history ORDER BY timestampMs DESC LIMIT :limit")
    suspend fun getRecentHistorySync(limit: Int = 100): List<BrowsingHistoryEntity>

    @Query(
        """
        SELECT domain, SUM(dwellTimeMs) as totalDwellTimeMs, COUNT(*) as visitCount
        FROM browsing_history
        WHERE timestampMs >= :startOfDayMs AND wasBlocked = 0
        GROUP BY domain
        ORDER BY totalDwellTimeMs DESC
        """
    )
    fun getDomainDwellSummaryToday(startOfDayMs: Long): Flow<List<DomainDwellSummary>>

    @Query("DELETE FROM browsing_history WHERE timestampMs < :cutoffMs")
    suspend fun deleteOlderThan(cutoffMs: Long): Int

    @Query("DELETE FROM browsing_history")
    suspend fun clearAll()
}
