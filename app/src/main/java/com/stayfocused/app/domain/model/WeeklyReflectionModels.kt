package com.stayfocused.app.domain.model

/**
 * Represents the app with the largest screen time reduction compared to previous week.
 */
data class AppUsageDrop(
    val packageName: String,
    val appName: String,
    val dropMinutes: Int
)

/**
 * Aggregate weekly reflection data for Sunday-night digest.
 */
data class WeeklyReflectionDigest(
    val totalBlockedAttempts: Int,
    val biggestDrop: AppUsageDrop?,
    val longestStrictStreakHours: Int,
    val formattedSummary: String
)
