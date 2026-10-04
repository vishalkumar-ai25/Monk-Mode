package com.stayfocused.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.stayfocused.app.data.local.entities.BlockedDomainEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedDomainDao {

    companion object {
        /**
         * Normalizes and validates a domain string at the DAO boundary.
         * - Strips trailing dots: 'google.com.' -> 'google.com'
         * - Converts to lowercase and trims whitespace
         * - Rejects empty strings, single-label domains ('localhost'), wildcards ('*'), spaces, or invalid labels
         * Returns normalized domain, or null if invalid.
         */
        fun normalizeDomain(domain: String): String? {
            val trimmed = domain.trim().trimEnd('.').lowercase()
            if (trimmed.isEmpty() || trimmed.contains('*') || trimmed.contains(' ')) {
                return null
            }
            val labels = trimmed.split('.')
            if (labels.size < 2 || labels.any { it.isEmpty() }) {
                return null
            }
            return trimmed
        }

        fun isValidDomain(domain: String): Boolean = normalizeDomain(domain) != null
    }

    @Query("SELECT * FROM blocked_domains")
    fun getAllBlockedDomains(): Flow<List<BlockedDomainEntity>>

    @Query("SELECT * FROM blocked_domains")
    fun getAllBlockedDomainsSync(): List<BlockedDomainEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_domains WHERE domain = :domain AND isBlocked = 1)")
    suspend fun isDomainBlockedInternal(domain: String): Boolean

    suspend fun isDomainBlocked(domain: String): Boolean {
        val normalized = normalizeDomain(domain) ?: return false
        return isDomainBlockedInternal(normalized)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBlockedDomainInternal(entity: BlockedDomainEntity)

    suspend fun upsertBlockedDomain(entity: BlockedDomainEntity) {
        val normalized = normalizeDomain(entity.domain)
            ?: throw IllegalArgumentException("Invalid domain: '${entity.domain}' (must be a multi-label domain without trailing dots or '*')")
        upsertBlockedDomainInternal(entity.copy(domain = normalized))
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBlockedDomainsInternal(entities: List<BlockedDomainEntity>)

    suspend fun upsertBlockedDomains(entities: List<BlockedDomainEntity>) {
        val validEntities = entities.mapNotNull { entity ->
            val normalized = normalizeDomain(entity.domain) ?: return@mapNotNull null
            entity.copy(domain = normalized)
        }
        if (validEntities.isNotEmpty()) {
            upsertBlockedDomainsInternal(validEntities)
        }
    }

    @Query("DELETE FROM blocked_domains WHERE domain = :domain")
    suspend fun deleteBlockedDomainInternal(domain: String)

    suspend fun deleteBlockedDomain(domain: String) {
        val normalized = normalizeDomain(domain) ?: domain.trim().trimEnd('.').lowercase()
        deleteBlockedDomainInternal(normalized)
    }
}
