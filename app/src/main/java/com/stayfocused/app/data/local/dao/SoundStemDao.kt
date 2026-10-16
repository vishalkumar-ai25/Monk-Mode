package com.stayfocused.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.stayfocused.app.data.local.entities.SoundStemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SoundStemDao {
    @Query("SELECT * FROM sound_stems ORDER BY title ASC")
    fun getAllSoundStemsFlow(): Flow<List<SoundStemEntity>>

    @Query("SELECT * FROM sound_stems ORDER BY title ASC")
    suspend fun getAllSoundStems(): List<SoundStemEntity>

    @Query("SELECT * FROM sound_stems ORDER BY title ASC")
    fun getAllSoundStemsSync(): List<SoundStemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stems: List<SoundStemEntity>)

    @Query("UPDATE sound_stems SET volume = :volume, isEnabled = :isEnabled WHERE stemId = :stemId")
    suspend fun updateStemState(stemId: String, volume: Float, isEnabled: Boolean)

    @Query("UPDATE sound_stems SET volume = :volume WHERE stemId = :stemId")
    suspend fun updateStemVolume(stemId: String, volume: Float)

    @Query("UPDATE sound_stems SET isEnabled = :isEnabled WHERE stemId = :stemId")
    suspend fun setStemEnabled(stemId: String, isEnabled: Boolean)

    @Query("UPDATE sound_stems SET volume = 0.0, isEnabled = 0")
    suspend fun resetAllStems()
}
