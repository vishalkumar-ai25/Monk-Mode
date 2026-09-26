package com.stayfocused.app.domain

import com.stayfocused.app.data.local.entities.StrictSessionEntity
import com.stayfocused.app.domain.model.AppUsageDrop
import com.stayfocused.app.domain.model.WeeklyReflectionDigest
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure Kotlin decision and calculation engine for generating weekly focus digests.
 * Fully decoupled from Android framework for fast JVM test execution.
 */
class WeeklyReflectionEngine {

    /**
     * Aggregates weekly focus stats into a formatted digest.
     */
    fun calculateWeeklyDigest(
        totalBlockedAttempts: Int,
        usageThisWeek: Map<String, Long>,
        usageLastWeek: Map<String, Long>,
        packageToAppName: Map<String, String>,
        strictSessions: List<StrictSessionEntity>
    ): WeeklyReflectionDigest {
        val biggestDrop = calculateBiggestDrop(usageThisWeek, usageLastWeek, packageToAppName)
        val longestStrictStreak = calculateLongestStrictStreakHours(strictSessions)
        val summary = formatSummary(totalBlockedAttempts, biggestDrop, longestStrictStreak)

        return WeeklyReflectionDigest(
            totalBlockedAttempts = totalBlockedAttempts,
            biggestDrop = biggestDrop,
            longestStrictStreakHours = longestStrictStreak,
            formattedSummary = summary
        )
    }

    /**
     * Determines the application with the maximum screen time reduction in minutes.
     */
    fun calculateBiggestDrop(
        usageThisWeek: Map<String, Long>,
        usageLastWeek: Map<String, Long>,
        packageToAppName: Map<String, String>
    ): AppUsageDrop? {
        var bestDrop: AppUsageDrop? = null
        var maxDropMinutes = 0

        for ((pkg, lastWeekMs) in usageLastWeek) {
            val thisWeekMs = usageThisWeek[pkg] ?: 0L
            val dropMs = lastWeekMs - thisWeekMs
            val dropMinutes = (dropMs / (60 * 1000L)).toInt()

            if (dropMinutes > maxDropMinutes) {
                maxDropMinutes = dropMinutes
                val appName = packageToAppName[pkg] ?: pkg
                bestDrop = AppUsageDrop(
                    packageName = pkg,
                    appName = appName,
                    dropMinutes = dropMinutes
                )
            }
        }

        return bestDrop
    }

    /**
     * Calculates the longest individual Strict Mode session duration in hours.
     */
    fun calculateLongestStrictStreakHours(strictSessions: List<StrictSessionEntity>): Int {
        if (strictSessions.isEmpty()) return 0

        val maxDurationMs = strictSessions.maxOfOrNull { session ->
            (session.targetEndTime - session.startTime).coerceAtLeast(0L)
        } ?: 0L

        return (maxDurationMs / (3600 * 1000L)).toInt()
    }

    /**
     * Builds the concise summary text for notification display.
     */
    fun formatSummary(
        totalBlocked: Int,
        biggestDrop: AppUsageDrop?,
        longestStreakHours: Int
    ): String {
        val parts = mutableListOf<String>()

        parts.add("$totalBlocked distractions blocked")

        if (biggestDrop != null && biggestDrop.dropMinutes > 0) {
            parts.add("Biggest drop: ${biggestDrop.appName} (-${biggestDrop.dropMinutes}m)")
        }

        if (longestStreakHours > 0) {
            parts.add("${longestStreakHours}h Strict Mode")
        }

        return parts.joinToString(" • ")
    }

    /**
     * Computes the epoch millisecond of the next Sunday at 21:00 (9:00 PM) in the given time zone.
     */
    fun calculateNextSundayNightTimestamp(
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zoneId)
        val todayAt9pm = zdt.toLocalDate().atTime(21, 0, 0, 0).atZone(zoneId)

        return if (zdt.dayOfWeek == DayOfWeek.SUNDAY && zdt.isBefore(todayAt9pm)) {
            todayAt9pm.toInstant().toEpochMilli()
        } else {
            val daysUntilSunday = (DayOfWeek.SUNDAY.value - zdt.dayOfWeek.value + 7) % 7
            val targetDate = if (daysUntilSunday == 0) {
                zdt.toLocalDate().plusWeeks(1)
            } else {
                zdt.toLocalDate().plusDays(daysUntilSunday.toLong())
            }
            targetDate.atTime(21, 0, 0, 0).atZone(zoneId).toInstant().toEpochMilli()
        }
    }
}
