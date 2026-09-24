package com.stayfocused.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recovery_codes")
data class RecoveryCodeEntity(
    @PrimaryKey val id: Int = 1,
    val passwordHash: String,
    val salt: String,
    val isConsumed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
