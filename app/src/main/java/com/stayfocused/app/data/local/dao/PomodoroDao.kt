package com.stayfocused.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.stayfocused.app.data.local.entities.PomodoroSessionEntity
import com.stayfocused.app.data.local.entities.PomodoroSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PomodoroDao {
    @Query("SELECT * FROM pomodoro_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<PomodoroSettingsEntity?>

    @Query("SELECT * FROM pomodoro_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): PomodoroSettingsEntity?

    @Query("SELECT * FROM pomodoro_settings WHERE id = 1 LIMIT 1")
    fun getSettingsSync(): PomodoroSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: PomodoroSettingsEntity)

    @Query("SELECT * FROM pomodoro_session WHERE id = 1 LIMIT 1")
    fun getSessionFlow(): Flow<PomodoroSessionEntity?>

    @Query("SELECT * FROM pomodoro_session WHERE id = 1 LIMIT 1")
    suspend fun getSession(): PomodoroSessionEntity?

    @Query("SELECT * FROM pomodoro_session WHERE id = 1 LIMIT 1")
    fun getSessionSync(): PomodoroSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSession(session: PomodoroSessionEntity)

    @Query("UPDATE pomodoro_session SET isRunning = :isRunning, remainingMs = :remainingMs WHERE id = 1")
    suspend fun updateSessionProgress(isRunning: Boolean, remainingMs: Long)

    @Query("DELETE FROM pomodoro_session WHERE id = 1")
    suspend fun clearSession()
}
