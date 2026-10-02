package com.stayfocused.app.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * AppWidget receiver for the Monk Mode Home-Screen Glance Widget.
 */
class FocusGlanceWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = FocusGlanceWidget()

    companion object {
        suspend fun updateAll(context: Context) {
            FocusGlanceWidget().updateAll(context)
        }

        fun triggerUpdateAsync(context: Context, scope: CoroutineScope = CoroutineScope(Dispatchers.IO)) {
            scope.launch {
                try {
                    updateAll(context)
                } catch (e: Exception) {
                    android.util.Log.w("FocusGlanceWidget", "Failed to update widget", e)
                }
            }
        }
    }
}
