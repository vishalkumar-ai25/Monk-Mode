package com.stayfocused.app.domain

import com.stayfocused.app.domain.model.BlockReason

data class WindowSnapshot(
    val id: Int,
    val packageName: String?,
    val isFocused: Boolean,
    val isActive: Boolean,
    val isApplication: Boolean,
    val isInPictureInPicture: Boolean
)

sealed interface MultiWindowDecision {
    data object Allow : MultiWindowDecision
    data class EvictBlockedWindow(
        val packageName: String,
        val isPictureInPicture: Boolean,
        val reason: BlockReason
    ) : MultiWindowDecision
}

/**
 * Pure Kotlin evaluator for multi-window split-screen, floating bubbles, and Picture-in-Picture (PiP).
 * Detects when a restricted or blocked package is concurrently active alongside an allowed application
 * or playing in a background PiP window.
 */
class MultiWindowInspector(
    private val ruleEvaluator: (packageName: String) -> BlockReason?
) {
    fun evaluate(windows: List<WindowSnapshot>): MultiWindowDecision {
        val appWindows = windows.filter { it.isApplication && !it.packageName.isNullOrBlank() }
        if (appWindows.isEmpty()) return MultiWindowDecision.Allow

        // Multi-window or PiP scenario: multiple app windows present simultaneously or PiP window active
        val hasMultiAppWindows = appWindows.size > 1 || appWindows.any { it.isInPictureInPicture }
        if (!hasMultiAppWindows) return MultiWindowDecision.Allow

        for (win in appWindows) {
            val pkg = win.packageName!!
            val lower = pkg.lowercase()
            // Never evict self or system UI
            if (lower == "com.stayfocused.app" || lower == "com.android.systemui" || lower.endsWith(".systemui")) {
                continue
            }

            val blockReason = ruleEvaluator(pkg)
            if (blockReason != null) {
                return MultiWindowDecision.EvictBlockedWindow(
                    packageName = pkg,
                    isPictureInPicture = win.isInPictureInPicture,
                    reason = blockReason
                )
            }
        }
        return MultiWindowDecision.Allow
    }
}
