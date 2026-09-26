package com.stayfocused.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Enumerated failsafe event triggers for anti-tamper audit logging.
 */
enum class FailsafeEventType {
    DELAY_REQUESTED,
    DELAY_CANCELLED,
    DELAY_FINALIZED,
    RECOVERY_CODE_ENTERED,
    BOOT_GRACE_WINDOW_USED
}

/**
 * Immutable append-only audit record of emergency unlocks, delay requests,
 * recovery code verification attempts, and boot grace period activations.
 */
@Entity(tableName = "failsafe_logs")
data class FailsafeLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,
    val details: String,
    val success: Boolean = true
)
