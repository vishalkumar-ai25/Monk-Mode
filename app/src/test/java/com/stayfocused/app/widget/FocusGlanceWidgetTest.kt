package com.stayfocused.app.widget

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.data.local.entities.AppLimitEntity
import com.stayfocused.app.ui.ProgressTier
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FocusGlanceWidgetTest {

    private lateinit var context: Context
    private lateinit var database: StayFocusedDatabase
    private lateinit var widget: FocusGlanceWidget

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, StayFocusedDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        widget = FocusGlanceWidget()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun widgetReceiver_providesGlanceWidgetInstance() {
        val receiver = FocusGlanceWidgetReceiver()
        assertNotNull(receiver.glanceAppWidget)
        assertEquals(FocusGlanceWidget::class.java, receiver.glanceAppWidget::class.java)
    }

    @Test
    fun loadWidgetData_withUsageLimits_computesAccurateSnapshot() = runBlocking {
        // Given 60 minutes (3,600,000 ms) of recorded usage across limits
        val limit1 = AppLimitEntity(
            packageName = "com.test.app1",
            appName = "App 1",
            dailyTimeLimitMinutes = 60,
            currentDayUsageMs = 20 * 60 * 1000L,
            lastResetTimestamp = System.currentTimeMillis()
        )
        val limit2 = AppLimitEntity(
            packageName = "com.test.app2",
            appName = "App 2",
            dailyTimeLimitMinutes = 60,
            currentDayUsageMs = 40 * 60 * 1000L,
            lastResetTimestamp = System.currentTimeMillis()
        )
        database.appLimitDao().upsertAppLimit(limit1)
        database.appLimitDao().upsertAppLimit(limit2)

        // When
        val data = widget.loadWidgetData(context, database)

        // Then
        assertNotNull(data)
        assertEquals(60, data.usedMinutes)
        assertEquals(120, data.targetMinutes)
        assertEquals(60, data.remainingMinutes)
        assertEquals(0.5f, data.progress, 0.001f)
        assertEquals(ProgressTier.NORMAL, data.tier)
        assertEquals("1h 0m left", data.remainingFormatted)
        assertEquals("1h 0m", data.usedFormatted)
    }

    @Test
    fun loadWidgetData_emptyDatabase_returnsZeroUsageFallback() = runBlocking {
        val data = widget.loadWidgetData(context, database)
        assertNotNull(data)
        assertEquals(0, data.usedMinutes)
        assertEquals(120, data.targetMinutes)
        assertEquals(120, data.remainingMinutes)
        assertEquals(0.0f, data.progress, 0.001f)
        assertEquals(ProgressTier.NORMAL, data.tier)
        assertEquals("2h 0m left", data.remainingFormatted)
    }
}
