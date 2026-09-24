package com.stayfocused.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.stayfocused.app.data.local.entities.FocusProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusProfileDao {
    @Query("SELECT * FROM focus_profiles")
    fun getAllProfiles(): Flow<List<FocusProfileEntity>>

    @Query("SELECT * FROM focus_profiles WHERE id = :id")
    fun getProfileById(id: Long): Flow<FocusProfileEntity?>

    @Query("SELECT * FROM focus_profiles WHERE isActive = 1")
    suspend fun getActiveProfilesSync(): List<FocusProfileEntity>

    @Query("SELECT * FROM focus_profiles WHERE isActive = 1")
    fun getActiveProfiles(): Flow<List<FocusProfileEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(entity: FocusProfileEntity): Long

    @Query("DELETE FROM focus_profiles WHERE id = :id")
    suspend fun deleteProfile(id: Long)

    @Query("UPDATE focus_profiles SET isActive = :isActive WHERE id = :id")
    suspend fun setProfileActive(id: Long, isActive: Boolean)
}
