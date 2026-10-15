package com.stayfocused.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocked_url_paths")
data class BlockedUrlPathEntity(
    @PrimaryKey val pathPattern: String, // e.g. "/r/all" or "reddit.com/r/all"
    val isBlocked: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
