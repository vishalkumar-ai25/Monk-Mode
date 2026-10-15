package com.stayfocused.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a granular short-form video feed rule (e.g. YouTube Shorts, Instagram Reels).
 * Supports both instant eviction on open (dailyScrollLimit == 0) and scroll quota limits (dailyScrollLimit > 0).
 */
@Entity(tableName = "short_form_rules")
data class ShortFormRuleEntity(
    @PrimaryKey
    val id: String,
    val packageName: String,
    val displayName: String,
    val isBlocked: Boolean = false,
    val dailyScrollLimit: Int = 0, // 0 = instant eviction upon entry if isBlocked; >0 = allow up to N scrolls
    val currentDayScrolls: Int = 0,
    val lastEvictionTimestamp: Long = 0L
)
