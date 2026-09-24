package com.stayfocused.app.worker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.data.local.entities.AppLimitEntity
import com.stayfocused.app.tracker.UsageStatsTracker
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WatchdogWorkerTest {

    private lateinit var context: Context
    private lateinit var db: StayFocusedDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, StayFocusedDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testWatchdogReconcilesUsageWhenServiceAlive() = runBlocking {
        val appLimitDao = db.appLimitDao()
        appLimitDao.upsertAppLimit(
            AppLimitEntity(
                packageName = "com.instagram.android",
                appName = "Instagram",
                dailyTimeLimitMinutes = 30,
                dailyLaunchLimit = 10,
                currentDayUsageMs = 1000L,
                currentDayLaunches = 2,
                lastResetTimestamp = System.currentTimeMillis()
            )
        )

        val mockUsage = mapOf("com.instagram.android" to 15000L)
        val tracker = UsageStatsTracker(
            context = context,
            appOpsChecker = { true },
            usageStatsProvider = { _, _ -> mockUsage }
        )

        var notificationPosted = false
        val worker = WatchdogWorker(context, mockk(relaxed = true))

        val result = worker.executeWatchdog(
            database = db,
            usageTracker = tracker,
            isServiceAlive = true,
            onServiceDead = { notificationPosted = true }
        )

        assertEquals(ListenableWorker.Result.success(), result)
        assertFalse("Notification should not be posted when service is alive", notificationPosted)

        val updated = appLimitDao.getAppLimitSync("com.instagram.android")
        assertEquals(15000L, updated?.currentDayUsageMs)
    }

    @Test
    fun testWatchdogAlertsWhenAccessibilityServiceDisabled() = runBlocking {
        var notificationPosted = false
        val worker = WatchdogWorker(context, mockk(relaxed = true))

        val result = worker.executeWatchdog(
            database = db,
            usageTracker = UsageStatsTracker(context, appOpsChecker = { true }),
            isServiceAlive = false,
            onServiceDead = { notificationPosted = true }
        )

        assertEquals(ListenableWorker.Result.success(), result)
        assertTrue("Notification must be posted when accessibility service is killed/disabled", notificationPosted)
    }

    @Test
    fun testWatchdogPerformsFailsafeMidnightResetIfMissed() = runBlocking {
        val appLimitDao = db.appLimitDao()
        val yesterday = System.currentTimeMillis() - 86400000L
        appLimitDao.upsertAppLimit(
            AppLimitEntity(
                packageName = "com.twitter.android",
                appName = "X",
                dailyTimeLimitMinutes = 20,
                dailyLaunchLimit = 5,
                currentDayUsageMs = 50000L,
                currentDayLaunches = 10,
                lastResetTimestamp = yesterday // Missed reset!
            )
        )

        val tracker = UsageStatsTracker(
            context = context,
            appOpsChecker = { true },
            usageStatsProvider = { _, _ -> emptyMap() }
        )

        val worker = WatchdogWorker(context, mockk(relaxed = true))

        val result = worker.executeWatchdog(
            database = db,
            usageTracker = tracker,
            isServiceAlive = true,
            onServiceDead = {}
        )

        assertEquals(ListenableWorker.Result.success(), result)

        val updated = appLimitDao.getAppLimitSync("com.twitter.android")
        assertEquals("Failsafe should reset usage counters if last reset was yesterday", 0L, updated?.currentDayUsageMs)
        assertEquals("Failsafe should reset launch counters if last reset was yesterday", 0, updated?.currentDayLaunches)
    }
}
