package com.stayfocused.app.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "browsing_history",
    indices = [
        Index("timestampMs"),
        Index(value = ["domain", "timestampMs"])
    ]
)
data class BrowsingHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMs: Long,
    val browserPackage: String,
    val domain: String,
    val fullUrl: String,
    val dwellTimeMs: Long,
    val wasBlocked: Boolean,
    val blockReason: String? = null
)

data class DomainDwellSummary(
    val domain: String,
    val totalDwellTimeMs: Long,
    val visitCount: Int
)
