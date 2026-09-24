package com.stayfocused.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_domains")
data class BlockedDomainEntity(
    @PrimaryKey val domain: String,
    val isBlocked: Boolean = true,
    val category: String = "general",
    val createdAt: Long = System.currentTimeMillis()
)
