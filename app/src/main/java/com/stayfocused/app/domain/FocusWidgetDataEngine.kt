package com.stayfocused.app.domain

import com.stayfocused.app.domain.model.FocusWidgetData
import com.stayfocused.app.ui.UsageDialHelper

/**
 * Pure Kotlin decision engine for calculating and formatting the state shown
 * in the Home-Screen Glance Widget.
 */
class FocusWidgetDataEngine(
    private val defaultTargetMinutes: Int = 120
) {
    fun computeWidgetData(
        usedMinutes: Int,
        targetMinutes: Int = defaultTargetMinutes
    ): FocusWidgetData {
        val safeTarget = if (targetMinutes <= 0) defaultTargetMinutes else targetMinutes
        val safeUsed = maxOf(0, usedMinutes)
        val remaining = maxOf(0, safeTarget - safeUsed)
        val progress = UsageDialHelper.calculateProgress(safeUsed, safeTarget)
        val tier = UsageDialHelper.getProgressTier(progress)
        val isExceeded = safeUsed > safeTarget

        val remainingFormatted = if (isExceeded) {
            "0m left (Exceeded)"
        } else {
            "${UsageDialHelper.formatDuration(remaining)} left"
        }

        return FocusWidgetData(
            usedMinutes = safeUsed,
            targetMinutes = safeTarget,
            remainingMinutes = remaining,
            progress = progress,
            tier = tier,
            remainingFormatted = remainingFormatted,
            usedFormatted = UsageDialHelper.formatDuration(safeUsed),
            targetFormatted = UsageDialHelper.formatDuration(safeTarget),
            isTargetExceeded = isExceeded
        )
    }
}
