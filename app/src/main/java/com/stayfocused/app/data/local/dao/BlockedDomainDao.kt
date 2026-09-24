package com.stayfocused.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.stayfocused.app.data.local.entities.BlockedDomainEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedDomainDao {
    @Query("SELECT * FROM blocked_domains")
    fun getAllBlockedDomains(): Flow<List<BlockedDomainEntity>>

    @Query("SELECT * FROM blocked_domains")
    fun getAllBlockedDomainsSync(): List<BlockedDomainEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_domains WHERE domain = :domain AND isBlocked = 1)")
    suspend fun isDomainBlocked(domain: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBlockedDomain(entity: BlockedDomainEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBlockedDomains(entities: List<BlockedDomainEntity>)

    @Query("DELETE FROM blocked_domains WHERE domain = :domain")
    suspend fun deleteBlockedDomain(domain: String)
}
