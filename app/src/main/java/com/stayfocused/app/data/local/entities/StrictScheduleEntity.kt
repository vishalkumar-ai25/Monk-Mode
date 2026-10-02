package com.stayfocused.app.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a recurring strict mode schedule window.
 *
 * Anti-tamper rules:
 * - Cannot be modified, disabled, or deleted while any Strict Session is active.
 * - Days of week encoded as bitmask: Mon=1, Tue=2, Wed=4, Thu=8, Fri=16, Sat=32, Sun=64.
 * - [dismissedUntilEpochMs]: set when an emergency recovery code or valid challenge disarms
 *   the session during this window, preventing Watchdog/Boot receiver infinite re-arm loops.
 */
@Entity(
    tableName = "strict_schedules",
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
data class StrictScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val daysOfWeekMask: Int, // Bit 0 = Mon (1) ... Bit 6 = Sun (64)
    val startMinuteOfDay: Int, // 0..1439
    val endMinuteOfDay: Int,   // 0..1439
    val profileId: Long,
    val deactivationChallenge: String = "EXPIRATION_ONLY", // EXPIRATION_ONLY, COOL_DOWN, RANDOM_TEXT
    val isEnabled: Boolean = true,
    val dismissedUntilEpochMs: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
