package com.stayfocused.app.ui

enum class ProgressTier {
    NORMAL,
    WARNING,
    CRITICAL
}

object UsageDialHelper {

    fun calculateProgress(usedMinutes: Int, targetMinutes: Int): Float {
        if (targetMinutes <= 0) return 0f
        val ratio = usedMinutes.toFloat() / targetMinutes.toFloat()
        return ratio.coerceIn(0f, 1f)
    }

    fun formatDuration(minutes: Int): String {
        if (minutes <= 0) return "0m"
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        return if (hours > 0) {
            "${hours}h ${remainingMinutes}m"
        } else {
            "${remainingMinutes}m"
        }
    }

    fun getProgressTier(progress: Float): ProgressTier {
        return when {
            progress < 0.70f -> ProgressTier.NORMAL
            progress < 0.90f -> ProgressTier.WARNING
            else -> ProgressTier.CRITICAL
        }
    }
}
