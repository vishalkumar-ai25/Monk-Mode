package com.stayfocused.app.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.stayfocused.app.domain.model.DailyFocusSummaryData

/**
 * High-performance on-device renderer generating a shareable focus summary image.
 * Uses Monk Mode's dark visual identity and renders directly to an Android Bitmap.
 */
class FocusSummaryBitmapGenerator(
    private val width: Int = 1080,
    private val height: Int = 1350
) {

    companion object {
        private const val COLOR_INK = 0xFF0E0E10.toInt()
        private const val COLOR_CARD = 0xFF16161A.toInt()
        private const val COLOR_CARD_ALT = 0xFF1C1C22.toInt()
        private const val COLOR_LINE = 0xFF2C2C35.toInt()
        private const val COLOR_TEXT = 0xFFF0ECE1.toInt()
        private const val COLOR_MUTED = 0xFF8E8E93.toInt()
        private const val COLOR_EMBER = 0xFFD48852.toInt()
        private const val COLOR_SAGE = 0xFF87A987.toInt()
        private const val COLOR_DANGER = 0xFFE05D52.toInt()
        private const val COLOR_TRACK = 0xFF22222B.toInt()
    }

    /**
     * Renders a [DailyFocusSummaryData] into a 1080x1350 ARGB_8888 Bitmap card.
     */
    fun generateSummaryBitmap(data: DailyFocusSummaryData): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Full Canvas Background
        canvas.drawColor(COLOR_INK)

        // 2. Main Card Panel
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_CARD
            style = Paint.Style.FILL
        }
        val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_LINE
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val cardRect = RectF(60f, 60f, width - 60f, height - 60f)
        canvas.drawRoundRect(cardRect, 40f, 40f, cardPaint)
        canvas.drawRoundRect(cardRect, 40f, 40f, cardBorderPaint)

        // 3. Header
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT
            textSize = 52f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("MONK MODE", width / 2f, 150f, titlePaint)

        // Accent divider line
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_EMBER
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(width / 2f - 60f, 175f, width / 2f + 60f, 175f, accentPaint)

        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_MUTED
            textSize = 30f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${data.dateText}  •  Daily Focus Summary", width / 2f, 225f, subtitlePaint)

        // 4. Center Dial (Dial Ring + Usage Stats)
        val dialCenterX = width / 2f
        val dialCenterY = 510f
        val dialRadius = 180f
        val dialStrokeWidth = 34f
        val dialRect = RectF(
            dialCenterX - dialRadius,
            dialCenterY - dialRadius,
            dialCenterX + dialRadius,
            dialCenterY + dialRadius
        )

        // Background Track
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TRACK
            style = Paint.Style.STROKE
            strokeWidth = dialStrokeWidth
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawArc(dialRect, 0f, 360f, false, trackPaint)

        // Progress Arc
        val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (data.isOverBudget) COLOR_DANGER else COLOR_EMBER
            style = Paint.Style.STROKE
            strokeWidth = dialStrokeWidth
            strokeCap = Paint.Cap.ROUND
        }
        val ratio = if (data.targetMinutes > 0) {
            (data.usedMinutes.toFloat() / data.targetMinutes.toFloat()).coerceIn(0f, 1f)
        } else 1f
        val sweepAngle = ratio * 360f
        if (sweepAngle > 0f) {
            canvas.drawArc(dialRect, -90f, sweepAngle, false, progressPaint)
        }

        // Inside Dial Text
        val usedTimePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT
            textSize = 76f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${data.usedMinutes}m", dialCenterX, dialCenterY - 10f, usedTimePaint)

        val targetTimePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_MUTED
            textSize = 28f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("of ${data.targetMinutes}m target", dialCenterX, dialCenterY + 36f, targetTimePaint)

        val scoreBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (data.isOverBudget) COLOR_DANGER else COLOR_SAGE
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val scoreLabel = if (data.isOverBudget) "Limit Exceeded" else "${data.focusScorePercentage}% Focus Retained"
        canvas.drawText(scoreLabel, dialCenterX, dialCenterY + 86f, scoreBadgePaint)

        // 5. Stat Metric Boxes
        val statBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_CARD_ALT
            style = Paint.Style.FILL
        }
        val statBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_LINE
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        // Left Stat Box: Distractions Blocked
        val leftBoxRect = RectF(100f, 780f, width / 2f - 15f, 950f)
        canvas.drawRoundRect(leftBoxRect, 20f, 20f, statBoxPaint)
        canvas.drawRoundRect(leftBoxRect, 20f, 20f, statBorderPaint)

        val statValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_EMBER
            textSize = 62f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${data.blockedDistractionsCount}", leftBoxRect.centerX(), 855f, statValuePaint)

        val statLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_MUTED
            textSize = 24f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Distractions Blocked", leftBoxRect.centerX(), 905f, statLabelPaint)

        // Right Stat Box: App Limits Monitored
        val rightBoxRect = RectF(width / 2f + 15f, 780f, width - 100f, 950f)
        canvas.drawRoundRect(rightBoxRect, 20f, 20f, statBoxPaint)
        canvas.drawRoundRect(rightBoxRect, 20f, 20f, statBorderPaint)

        val rightValPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT
            textSize = 62f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("${data.activeLimitsCount}", rightBoxRect.centerX(), 855f, rightValPaint)
        canvas.drawText("App Limits Active", rightBoxRect.centerX(), 905f, statLabelPaint)

        // Bottom Banner: Active Profile
        val profileBannerRect = RectF(100f, 980f, width - 100f, 1100f)
        canvas.drawRoundRect(profileBannerRect, 20f, 20f, statBoxPaint)
        canvas.drawRoundRect(profileBannerRect, 20f, 20f, statBorderPaint)

        val profileDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_SAGE
            style = Paint.Style.FILL
        }
        canvas.drawCircle(150f, 1040f, 12f, profileDotPaint)

        val profileTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        val profileName = data.activeProfileName ?: "Shields Armed"
        canvas.drawText("Focus Mode: $profileName", 185f, 1050f, profileTextPaint)

        // 6. Footer
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_MUTED
            textSize = 24f
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Stay Focused  •  Monk Mode Anti-Tamper Shield", width / 2f, 1220f, footerPaint)

        return bitmap
    }
}
