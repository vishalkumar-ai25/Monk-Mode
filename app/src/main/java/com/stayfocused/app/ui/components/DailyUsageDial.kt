package com.stayfocused.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stayfocused.app.ui.ProgressTier
import com.stayfocused.app.ui.UsageDialHelper
import com.stayfocused.app.ui.theme.MonkCardAlt
import com.stayfocused.app.ui.theme.MonkDanger
import com.stayfocused.app.ui.theme.MonkEmber
import com.stayfocused.app.ui.theme.MonkMuted
import com.stayfocused.app.ui.theme.MonkSage
import com.stayfocused.app.ui.theme.MonkText

@Composable
fun DailyUsageDial(
    usedMinutes: Int,
    targetMinutes: Int,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    strokeWidth: Dp = 16.dp
) {
    val progress = UsageDialHelper.calculateProgress(usedMinutes, targetMinutes)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 800),
        label = "progressAnimation"
    )

    // Monk Mode 3-tier color logic: sage (<70%) → ember (70–90%) → danger (>90%)
    val tier = UsageDialHelper.getProgressTier(progress)
    val targetColor = when (tier) {
        ProgressTier.NORMAL -> MonkSage
        ProgressTier.WARNING -> MonkEmber
        ProgressTier.CRITICAL -> MonkDanger
    }

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 400),
        label = "colorAnimation"
    )

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val canvasSize = size.toPx() - strokePx
            val topLeftOffset = strokePx / 2f

            // Track circle: cardAlt color (no shadow, flat aesthetic)
            drawArc(
                color = MonkCardAlt,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(topLeftOffset, topLeftOffset),
                size = androidx.compose.ui.geometry.Size(canvasSize, canvasSize),
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Animated progress arc
            drawArc(
                color = animatedColor,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(topLeftOffset, topLeftOffset),
                size = androidx.compose.ui.geometry.Size(canvasSize, canvasSize),
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = UsageDialHelper.formatDuration(usedMinutes),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = MonkText
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "of ${UsageDialHelper.formatDuration(targetMinutes)} target",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MonkMuted,
                    fontSize = 12.sp
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${(progress * 100).toInt()}% used",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = animatedColor,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}
