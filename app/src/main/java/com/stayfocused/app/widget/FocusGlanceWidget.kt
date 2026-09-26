package com.stayfocused.app.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.domain.FocusWidgetDataEngine
import com.stayfocused.app.domain.model.FocusWidgetData
import com.stayfocused.app.ui.MainActivity
import com.stayfocused.app.ui.ProgressTier
import com.stayfocused.app.ui.theme.MonkCard
import com.stayfocused.app.ui.theme.MonkDanger
import com.stayfocused.app.ui.theme.MonkEmber
import com.stayfocused.app.ui.theme.MonkMuted
import com.stayfocused.app.ui.theme.MonkSage
import com.stayfocused.app.ui.theme.MonkText

/**
 * Jetpack Glance Widget that mirrors the Dashboard circular dial and displays
 * today's remaining screen time minutes.
 * Read-only: tapping anywhere on the widget launches MainActivity.
 */
class FocusGlanceWidget(
    private val dataEngine: FocusWidgetDataEngine = FocusWidgetDataEngine(),
    private val dialRenderer: FocusWidgetDialRenderer = FocusWidgetDialRenderer()
) : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetData = loadWidgetData(context)
        val dialBitmap = dialRenderer.renderDialWithCenterText(widgetData, sizePx = 220)

        provideContent {
            GlanceTheme {
                WidgetContent(data = widgetData, dialBitmap = dialBitmap)
            }
        }
    }

    suspend fun loadWidgetData(context: Context, database: StayFocusedDatabase? = null): FocusWidgetData {
        return try {
            val db = database ?: StayFocusedDatabase.getInstance(context)
            val limits = db.appLimitDao().getAllAppLimitsSync()
            val totalUsedMinutes = (limits.sumOf { it.currentDayUsageMs } / (60 * 1000L)).toInt()
            dataEngine.computeWidgetData(usedMinutes = totalUsedMinutes, targetMinutes = 120)
        } catch (e: Exception) {
            dataEngine.computeWidgetData(usedMinutes = 0, targetMinutes = 120)
        }
    }

    @Composable
    fun WidgetContent(data: FocusWidgetData, dialBitmap: Bitmap) {
        val tierColor = when (data.tier) {
            ProgressTier.NORMAL -> MonkSage
            ProgressTier.WARNING -> MonkEmber
            ProgressTier.CRITICAL -> MonkDanger
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(MonkCard))
                .cornerRadius(16.dp)
                .clickable(actionStartActivity<MainActivity>())
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(dialBitmap),
                    contentDescription = "Screen Time Dial",
                    modifier = GlanceModifier.size(80.dp)
                )

                Spacer(modifier = GlanceModifier.width(12.dp))

                Column(
                    modifier = GlanceModifier.defaultWeight(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MONK MODE",
                        style = TextStyle(
                            color = ColorProvider(MonkMuted),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    Spacer(modifier = GlanceModifier.height(2.dp))

                    Text(
                        text = data.remainingFormatted,
                        style = TextStyle(
                            color = ColorProvider(MonkText),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )

                    Spacer(modifier = GlanceModifier.height(2.dp))

                    Text(
                        text = "${data.usedFormatted} of ${data.targetFormatted} target",
                        style = TextStyle(
                            color = ColorProvider(tierColor),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }
}
