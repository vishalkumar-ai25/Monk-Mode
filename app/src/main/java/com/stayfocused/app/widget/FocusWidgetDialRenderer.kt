package com.stayfocused.app.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.stayfocused.app.domain.model.FocusWidgetData
import com.stayfocused.app.ui.ProgressTier

/**
 * High-performance on-device Canvas renderer that renders the Monk Mode
 * circular usage dial to an anti-aliased [Bitmap] for consumption by Jetpack Glance
 * and Android AppWidget RemoteViews.
 */
class FocusWidgetDialRenderer {

    companion object {
        // Monk Mode Palette Tokens (ARGB Ints)
        const val COLOR_TRACK = 0xFF2C2A22.toInt()
        const val COLOR_SAGE = 0xFF8AA980.toInt()
        const val COLOR_EMBER = 0xFFD98E3F.toInt()
        const val COLOR_DANGER = 0xFFC1544B.toInt()
        const val COLOR_TEXT = 0xFFECE7DC.toInt()
        const val COLOR_MUTED = 0xFF9C978A.toInt()
    }

    /**
     * Renders a circular progress dial ring with rounded caps and a dark track.
     */
    fun renderDialBitmap(
        progress: Float,
        tier: ProgressTier,
        sizePx: Int = 200,
        strokeWidthPx: Float = (sizePx * 0.10f).coerceAtLeast(8f)
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val halfStroke = strokeWidthPx / 2f
        val oval = RectF(
            halfStroke,
            halfStroke,
            sizePx.toFloat() - halfStroke,
            sizePx.toFloat() - halfStroke
        )

        // 1. Draw circular background track
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeWidthPx
            color = COLOR_TRACK
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawArc(oval, 0f, 360f, false, trackPaint)

        // 2. Draw progress arc
        val clampedProgress = progress.coerceIn(0f, 1f)
        if (clampedProgress > 0f) {
            val progressColor = when (tier) {
                ProgressTier.NORMAL -> COLOR_SAGE
                ProgressTier.WARNING -> COLOR_EMBER
                ProgressTier.CRITICAL -> COLOR_DANGER
            }

            val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = strokeWidthPx
                color = progressColor
                strokeCap = Paint.Cap.ROUND
            }

            val sweepAngle = clampedProgress * 360f
            canvas.drawArc(oval, -90f, sweepAngle, false, progressPaint)
        }

        return bitmap
    }

    /**
     * Renders the circular progress ring along with typography centered in the dial.
     */
    fun renderDialWithCenterText(
        data: FocusWidgetData,
        sizePx: Int = 220
    ): Bitmap {
        val strokeWidth = (sizePx * 0.09f).coerceAtLeast(10f)
        val bitmap = renderDialBitmap(
            progress = data.progress,
            tier = data.tier,
            sizePx = sizePx,
            strokeWidthPx = strokeWidth
        )
        val canvas = Canvas(bitmap)

        val centerX = sizePx / 2f
        val centerY = sizePx / 2f

        val tierColor = when (data.tier) {
            ProgressTier.NORMAL -> COLOR_SAGE
            ProgressTier.WARNING -> COLOR_EMBER
            ProgressTier.CRITICAL -> COLOR_DANGER
        }

        // Remaining time text (or used text if exceeded)
        val mainText = if (data.isTargetExceeded) "0m" else data.usedFormatted
        val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT
            textSize = sizePx * 0.18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(mainText, centerX, centerY - (sizePx * 0.02f), mainPaint)

        // Subtitle: e.g. "of 2h"
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_MUTED
            textSize = sizePx * 0.09f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }
        val subText = "of ${data.targetFormatted}"
        canvas.drawText(subText, centerX, centerY + (sizePx * 0.12f), subPaint)

        // Percentage label
        val percentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tierColor
            textSize = sizePx * 0.08f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val percentText = "${(data.progress * 100).toInt()}% used"
        canvas.drawText(percentText, centerX, centerY + (sizePx * 0.23f), percentPaint)

        return bitmap
    }
}
