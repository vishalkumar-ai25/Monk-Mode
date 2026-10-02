package com.stayfocused.app.domain.model

/**
 * Data bundle representing today's focus metrics for generating a shareable summary card.
 */
data class DailyFocusSummaryData(
    val dateText: String,
    val usedMinutes: Int,
    val targetMinutes: Int,
    val activeLimitsCount: Int,
    val blockedDistractionsCount: Int,
    val activeProfileName: String? = null
) {
    /**
     * Percentage of focus budget retained today (0 to 100%).
     */
    val focusScorePercentage: Int
        get() = if (targetMinutes > 0) {
            val remaining = (targetMinutes - usedMinutes).coerceAtLeast(0)
            ((remaining.toDouble() / targetMinutes) * 100).toInt()
        } else 100

    val isOverBudget: Boolean
        get() = usedMinutes > targetMinutes
}
