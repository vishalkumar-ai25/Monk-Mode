package com.stayfocused.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.data.local.entities.FocusProfileWithRules
import com.stayfocused.app.data.local.entities.ProfileBlockedDomainEntity
import com.stayfocused.app.data.local.entities.ProfileBlockedPackageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusProfileDao {

    @Query("SELECT * FROM focus_profiles")
    fun getAllProfiles(): Flow<List<FocusProfileEntity>>

    @Query("SELECT * FROM focus_profiles")
    suspend fun getAllProfilesSync(): List<FocusProfileEntity>

    @Transaction
    @Query("SELECT * FROM focus_profiles")
    fun getAllProfilesWithRules(): Flow<List<FocusProfileWithRules>>

    @Transaction
    @Query("SELECT * FROM focus_profiles")
    suspend fun getAllProfilesWithRulesSync(): List<FocusProfileWithRules>

    @Query("SELECT * FROM focus_profiles WHERE id = :id")
    fun getProfileById(id: Long): Flow<FocusProfileEntity?>

    @Transaction
    @Query("SELECT * FROM focus_profiles WHERE id = :id")
    fun getProfileWithRules(id: Long): Flow<FocusProfileWithRules?>

    @Query("SELECT * FROM focus_profiles WHERE isActive = 1")
    suspend fun getActiveProfilesSync(): List<FocusProfileEntity>

    @Query("SELECT * FROM focus_profiles WHERE isActive = 1")
    fun getActiveProfiles(): Flow<List<FocusProfileEntity>>

    // Hot path queries directly joining on indexed foreign keys (< 10ms budget)
    @Query("""
        SELECT DISTINCT p.packageName 
        FROM profile_blocked_packages p
        INNER JOIN focus_profiles f ON p.profileId = f.id
        WHERE f.isActive = 1
    """)
    suspend fun getActiveBlockedPackages(): List<String>

    @Query("""
        SELECT DISTINCT d.domain 
        FROM profile_blocked_domains d
        INNER JOIN focus_profiles f ON d.profileId = f.id
        WHERE f.isActive = 1
    """)
    suspend fun getActiveBlockedDomains(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(entity: FocusProfileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedPackages(packages: List<ProfileBlockedPackageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedDomains(domains: List<ProfileBlockedDomainEntity>)

    @Query("DELETE FROM profile_blocked_packages WHERE profileId = :profileId")
    suspend fun clearBlockedPackages(profileId: Long)

    @Query("DELETE FROM profile_blocked_domains WHERE profileId = :profileId")
    suspend fun clearBlockedDomains(profileId: Long)

    @Query("DELETE FROM focus_profiles WHERE id = :id")
    suspend fun deleteProfile(id: Long)

    @Query("UPDATE focus_profiles SET isActive = :isActive WHERE id = :id")
    suspend fun setProfileActive(id: Long, isActive: Boolean)

    @Query("UPDATE focus_profiles SET isActive = 0")
    suspend fun deactivateAllProfiles()

    @Query("SELECT * FROM focus_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProfileSync(): FocusProfileEntity?

    @Transaction
    suspend fun switchToProfile(targetId: Long) {
        deactivateAllProfiles()
        setProfileActive(targetId, true)
    }
}
