package com.stayfocused.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_profiles")
data class FocusProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isActive: Boolean = false,
    val isStrictMode: Boolean = false,
    val scheduleStartTime: String? = null,
    val scheduleEndTime: String? = null,
    val activeDaysMask: Int = 0
)
