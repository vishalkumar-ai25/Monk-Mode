package com.stayfocused.app.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pomodoro_settings",
    foreignKeys = [
        ForeignKey(
            entity = FocusProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedProfileId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["linkedProfileId"])
    ]
)
data class PomodoroSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    @ColumnInfo(name = "workMinutes")
    val workMinutes: Int = 25,
    @ColumnInfo(name = "shortBreakMinutes")
    val shortBreakMinutes: Int = 5,
    @ColumnInfo(name = "longBreakMinutes")
    val longBreakMinutes: Int = 15,
    @ColumnInfo(name = "longBreakInterval")
    val longBreakInterval: Int = 4,
    @ColumnInfo(name = "autoStartBreaks")
    val autoStartBreaks: Boolean = false,
    @ColumnInfo(name = "autoStartWork")
    val autoStartWork: Boolean = false,
    @ColumnInfo(name = "isDndEnabled")
    val isDndEnabled: Boolean = false,
    @ColumnInfo(name = "linkedProfileId")
    val linkedProfileId: Long? = null
)
