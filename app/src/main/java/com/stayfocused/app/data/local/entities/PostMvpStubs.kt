package com.stayfocused.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "unlock_events")
data class UnlockEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val durationMs: Long
)

@Entity(tableName = "geofence_profiles")
data class GeofenceProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float
)

@Entity(tableName = "notification_block_rules")
data class NotificationBlockRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val filterRegex: String? = null,
    val blockAll: Boolean = true
)
