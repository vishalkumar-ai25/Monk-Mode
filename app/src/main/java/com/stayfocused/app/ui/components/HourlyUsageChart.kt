package com.stayfocused.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import com.stayfocused.app.data.local.entities.HourlyAppTelemetryEntity
import com.stayfocused.app.data.local.entities.HourlyDeviceTelemetryEntity
import com.stayfocused.app.ui.screens.AppIconImage
import com.stayfocused.app.ui.theme.MonkCard
import com.stayfocused.app.ui.theme.MonkCardAlt
import com.stayfocused.app.ui.theme.MonkEmber
import com.stayfocused.app.ui.theme.MonkInk
import com.stayfocused.app.ui.theme.MonkLine
import com.stayfocused.app.ui.theme.MonkMuted
import com.stayfocused.app.ui.theme.MonkSage
import com.stayfocused.app.ui.theme.MonkText
import java.time.ZonedDateTime

enum class ChartMetricMode {
    SCREEN_TIME,
    PICKUPS
}

/**
 * Interactive 24-hour bar chart displaying hourly screen time distribution and device pickups
 * with active hour inspection and top application breakdown.
 */
@Composable
fun HourlyUsageChart(
    deviceTelemetry: List<HourlyDeviceTelemetryEntity>,
    appTelemetry: List<HourlyAppTelemetryEntity>,
    modifier: Modifier = Modifier,
    initialSelectedHour: Int = ZonedDateTime.now().hour
) {
    var selectedMetric by remember { mutableStateOf(ChartMetricMode.SCREEN_TIME) }
    var selectedHour by remember { mutableIntStateOf(initialSelectedHour.coerceIn(0, 23)) }

    val context = LocalContext.current
    val pm = remember(context) { context.packageManager }

    // Map 0..23 hours
    val deviceByHour = remember(deviceTelemetry) {
        (0..23).associateWith { hour ->
            deviceTelemetry.find { it.hourOfDay == hour }
        }
    }

    val appUsageByHour = remember(appTelemetry) {
        (0..23).associateWith { h ->
            appTelemetry.filter { it.hourOfDay == h }.sumOf { it.foregroundMs }
        }
    }

    val maxScreenTimeMinutes = remember(deviceTelemetry, appUsageByHour) {
        val maxDevice = deviceTelemetry.maxOfOrNull { it.totalScreenTimeMs / 60_000L } ?: 0L
        val maxApp = (0..23).maxOfOrNull { h ->
            (appUsageByHour[h] ?: 0L) / 60_000L
        } ?: 0L
        maxOf(maxDevice, maxApp, 15L).toFloat()
    }

    val maxPickups = remember(deviceTelemetry) {
        val maxP = deviceTelemetry.maxOfOrNull { it.pickupCount } ?: 0
        maxOf(maxP, 5).toFloat()
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MonkCard),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MonkLine, RoundedCornerShape(18.dp))
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize()
        ) {
            // Header Row with Metric Mode Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "24-Hour Telemetry",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MonkText,
                            fontFamily = FontFamily.Serif
                        )
                    )
                    Text(
                        text = if (selectedMetric == ChartMetricMode.SCREEN_TIME)
                            "Screen time per clock hour"
                        else
                            "Device pickups / unlocks per hour",
                        fontSize = 11.sp,
                        color = MonkMuted
                    )
                }

                // Segmented Button Toggle
                Row(
                    modifier = Modifier
                        .background(MonkCardAlt, RoundedCornerShape(8.dp))
                        .border(1.dp, MonkLine, RoundedCornerShape(8.dp))
                        .padding(2.dp)
                ) {
                    val isTime = selectedMetric == ChartMetricMode.SCREEN_TIME
                    Box(
                        modifier = Modifier
                            .background(if (isTime) MonkEmber else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable { selectedMetric = ChartMetricMode.SCREEN_TIME }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Time",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isTime) MonkInk else MonkText
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(if (!isTime) MonkSage else Color.Transparent, RoundedCornerShape(6.dp))
                            .clickable { selectedMetric = ChartMetricMode.PICKUPS }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Pickups",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!isTime) MonkInk else MonkText
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 24-Hour Bar Chart View (Scrollable horizontally)
            val chartScrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(chartScrollState)
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                for (hour in 0..23) {
                    val device = deviceByHour[hour]
                    val appUsageThisHourMs = appUsageByHour[hour] ?: 0L
                    val totalScreenMs = maxOf(device?.totalScreenTimeMs ?: 0L, appUsageThisHourMs)
                    val screenMins = (totalScreenMs / 60_000L).toInt()
                    val pickups = device?.pickupCount ?: 0

                    val fraction = if (selectedMetric == ChartMetricMode.SCREEN_TIME) {
                        (screenMins.toFloat() / maxScreenTimeMinutes).coerceIn(0.05f, 1f)
                    } else {
                        (pickups.toFloat() / maxPickups).coerceIn(0.05f, 1f)
                    }

                    val isSelected = hour == selectedHour
                    val hasData = if (selectedMetric == ChartMetricMode.SCREEN_TIME) screenMins > 0 else pickups > 0

                    Column(
                        modifier = Modifier
                            .clickable { selectedHour = hour }
                            .padding(horizontal = 2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Metric Value Hint
                        Text(
                            text = if (hasData) {
                                if (selectedMetric == ChartMetricMode.SCREEN_TIME) "${screenMins}m" else "$pickups"
                            } else "",
                            fontSize = 9.sp,
                            color = if (isSelected) MonkEmber else MonkMuted,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.height(12.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Bar
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(90.dp)
                                .background(MonkCardAlt, RoundedCornerShape(4.dp)),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            val barColor = if (selectedMetric == ChartMetricMode.SCREEN_TIME) {
                                if (isSelected) MonkEmber else if (hasData) MonkEmber.copy(alpha = 0.65f) else Color.Transparent
                            } else {
                                if (isSelected) MonkSage else if (hasData) MonkSage.copy(alpha = 0.65f) else Color.Transparent
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(fraction = if (hasData) fraction else 0f)
                                    .background(barColor, RoundedCornerShape(4.dp))
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.dp,
                                        color = if (isSelected) Color.White else Color.Transparent,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Hour Label
                        Text(
                            text = String.format(Locale.getDefault(), "%02d", hour),
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MonkEmber else MonkMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MonkLine, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Active Hour Inspector Breakdown Card
            val activeDevice = deviceByHour[selectedHour]
            val activeAppsThisHour = appTelemetry.filter { it.hourOfDay == selectedHour && it.foregroundMs > 0 }
                .sortedByDescending { it.foregroundMs }

            val totalHourMins = (maxOf(activeDevice?.totalScreenTimeMs ?: 0L, activeAppsThisHour.sumOf { it.foregroundMs }) / 60_000L).toInt()
            val totalPickups = activeDevice?.pickupCount ?: 0
            val totalShortsBlocked = activeAppsThisHour.sumOf { it.shortsBlockedCount }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format(Locale.getDefault(), "Hour %02d:00 – %02d:59 Overview", selectedHour, selectedHour),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MonkText
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "$totalHourMins min",
                        color = MonkEmber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text("•", color = MonkMuted, fontSize = 12.sp)
                    Text(
                        text = "$totalPickups pickups",
                        color = MonkSage,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    if (totalShortsBlocked > 0) {
                        Text("•", color = MonkMuted, fontSize = 12.sp)
                        Text(
                            text = "$totalShortsBlocked shorts blocked",
                            color = Color(0xFFFF5252),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (activeAppsThisHour.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No application activity recorded in this hour.",
                        fontSize = 11.sp,
                        color = MonkMuted
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    activeAppsThisHour.take(5).forEach { appItem ->
                        val mins = appItem.foregroundMs / 60_000L
                        val secs = (appItem.foregroundMs % 60_000L) / 1000L
                        val displayTime = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"

                        val appLabel = remember(appItem.packageName) {
                            try {
                                val appInfo = pm.getApplicationInfo(appItem.packageName, 0)
                                pm.getApplicationLabel(appInfo).toString()
                            } catch (_: Exception) {
                                val lastSegment = appItem.packageName.substringAfterLast('.')
                                if (lastSegment.equals("android", ignoreCase = true) && appItem.packageName.contains('.')) {
                                    val parts = appItem.packageName.split('.')
                                    if (parts.size >= 2) parts[parts.size - 2].replaceFirstChar { it.uppercase() }
                                    else lastSegment.replaceFirstChar { it.uppercase() }
                                } else {
                                    lastSegment.replaceFirstChar { it.uppercase() }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MonkCardAlt, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                AppIconImage(packageName = appItem.packageName, size = 24)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = appLabel,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MonkText
                                )
                                if (appItem.shortsBlockedCount > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFFFF5252).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${appItem.shortsBlockedCount} shorts blocked",
                                            fontSize = 9.sp,
                                            color = Color(0xFFFF5252),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Text(
                                text = displayTime,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MonkEmber
                            )
                        }
                    }
                }
            }
        }
    }
}
