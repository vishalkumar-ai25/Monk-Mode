package com.stayfocused.app.domain.model

import com.stayfocused.app.ui.ProgressTier

/**
 * Immutable data snapshot representing the state shown on the Home-Screen Glance Widget.
 */
data class FocusWidgetData(
    val usedMinutes: Int,
    val targetMinutes: Int,
    val remainingMinutes: Int,
    val progress: Float,
    val tier: ProgressTier,
    val remainingFormatted: String,
    val usedFormatted: String,
    val targetFormatted: String,
    val isTargetExceeded: Boolean
)
