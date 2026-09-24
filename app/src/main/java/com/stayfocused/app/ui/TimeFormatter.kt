package com.stayfocused.app.ui

import java.util.Locale

object TimeFormatter {

    fun formatRemainingTime(remainingMs: Long): String {
        if (remainingMs <= 0) return "00:00"
        val totalSeconds = remainingMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
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
