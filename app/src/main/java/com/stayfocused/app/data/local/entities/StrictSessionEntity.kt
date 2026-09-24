package com.stayfocused.app.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "strict_sessions",
    foreignKeys = [
        ForeignKey(
            entity = FocusProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profileId"])]
)
data class StrictSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val startTime: Long,
    val targetEndTime: Long,
    val delayedUnlockRequestTime: Long? = null,
    val delayedUnlockDurationMs: Long = 24 * 60 * 60 * 1000L,
    val isActive: Boolean = true
)
