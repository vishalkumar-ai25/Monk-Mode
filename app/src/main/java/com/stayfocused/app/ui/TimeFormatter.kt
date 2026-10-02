package com.stayfocused.app.ui

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeFormatter {

    fun formatRemainingTime(remainingMs: Long): String {
        if (remainingMs <= 0) return "00:00"
        val totalSeconds = remainingMs / 1000
        val totalMinutes = totalSeconds / 60
        val totalHours = totalMinutes / 60
        val totalDays = totalHours / 24

        return when {
            totalHours < 1 -> {
                String.format(Locale.US, "%02d:%02d", totalMinutes, totalSeconds % 60)
            }
            totalDays < 1 -> {
                String.format(Locale.US, "%02d:%02d:%02d", totalHours, totalMinutes % 60, totalSeconds % 60)
            }
            else -> {
                String.format(Locale.US, "%dd %02dh %02dm", totalDays, totalHours % 24, totalMinutes % 60)
            }
        }
    }

    fun formatExactDateTime(epochMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val zdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(epochMs), zoneId)
        val formatter = DateTimeFormatter.ofPattern("EEE, MMM d, hh:mm a", Locale.US)
        return zdt.format(formatter)
    }

    fun formatUsageDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000).coerceAtLeast(0L)
        val totalMinutes = totalSeconds / 60
        val totalHours = totalMinutes / 60

        return if (totalHours > 0) {
            val hourLabel = if (totalHours == 1L) "hr" else "hrs"
            String.format(Locale.US, "%02d %s %02d mins", totalHours, hourLabel, totalMinutes % 60)
        } else {
            String.format(Locale.US, "%02d mins %02d sec", totalMinutes, totalSeconds % 60)
        }
    }

    fun formatTimeAgo(timestamp: Long, now: Long = System.currentTimeMillis()): String {
        val diff = (now - timestamp).coerceAtLeast(0L)
        val minutes = diff / (60 * 1000L)
        val hours = diff / (3600 * 1000L)
        val days = diff / (24 * 3600 * 1000L)

        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            else -> "${days}d ago"
        }
    }
}
