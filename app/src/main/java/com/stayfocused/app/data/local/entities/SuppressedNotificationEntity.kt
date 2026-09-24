package com.stayfocused.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "suppressed_notifications")
data class SuppressedNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val title: String?,
    val contentSnippet: String?,
    val postTimestamp: Long,
    val isViewed: Boolean = false
)
