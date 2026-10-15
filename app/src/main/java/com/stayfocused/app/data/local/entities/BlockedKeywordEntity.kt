package com.stayfocused.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_keywords")
data class BlockedKeywordEntity(
    @PrimaryKey val keyword: String,
    val isBlocked: Boolean = true,
    val category: String = "custom", // "custom", "adult", "social"
    val createdAt: Long = System.currentTimeMillis()
)
