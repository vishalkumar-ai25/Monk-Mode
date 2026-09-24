package com.stayfocused.app.tracker

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.data.local.dao.AppLimitDao
import com.stayfocused.app.data.local.entities.AppLimitEntity
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
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UsageStatsTrackerTest {

    private lateinit var context: Context
    private lateinit var db: StayFocusedDatabase
    private lateinit var appLimitDao: AppLimitDao

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, StayFocusedDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        appLimitDao = db.appLimitDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testHasUsageStatsPermissionDefensiveCheck() {
        val grantedTracker = UsageStatsTracker(
            context = context,
            appOpsChecker = { true }
        )
        assertTrue(grantedTracker.hasUsageStatsPermission())

        val deniedTracker = UsageStatsTracker(
            context = context,
            appOpsChecker = { false }
        )
        assertFalse(deniedTracker.hasUsageStatsPermission())
    }

    @Test
    fun testMidnightCalculation() {
        val tracker = UsageStatsTracker(context = context)
        val zoneId = ZoneId.of("UTC")
        // 2026-09-24 15:30:45 UTC
        val fixedDateTime = ZonedDateTime.of(2026, 9, 24, 15, 30, 45, 0, zoneId)
        val fixedNow = fixedDateTime.toInstant().toEpochMilli()

        val midnight = tracker.getStartOfToday(nowMs = fixedNow, zoneId = zoneId)
        val expectedMidnight = ZonedDateTime.of(2026, 9, 24, 0, 0, 0, 0, zoneId)
            .toInstant().toEpochMilli()

        assertEquals("Midnight should exactly match 00:00:00 UTC", expectedMidnight, midnight)
    }

    @Test
    fun testQueryForegroundUsageWithMockProvider() {
        val mockData = mapOf(
            "com.instagram.android" to 15 * 60 * 1000L,
            "com.twitter.android" to 25 * 60 * 1000L
        )

        val tracker = UsageStatsTracker(
            context = context,
            appOpsChecker = { true },
            usageStatsProvider = { _, _ -> mockData }
        )

        val result = tracker.queryForegroundUsage()
        assertEquals(2, result.size)
        assertEquals(15 * 60 * 1000L, result["com.instagram.android"])
        assertEquals(25 * 60 * 1000L, result["com.twitter.android"])
    }

    @Test
    fun testQueryForegroundUsageReturnsEmptyWhenPermissionDenied() {
        val tracker = UsageStatsTracker(
            context = context,
            appOpsChecker = { false },
            usageStatsProvider = { _, _ -> mapOf("com.instagram.android" to 60000L) }
        )

        val result = tracker.queryForegroundUsage()
        assertTrue("Must return empty map when permission is denied", result.isEmpty())
    }

    @Test
    fun testSyncUsageWithDatabaseUpdatesTrackedLimits() = runBlocking {
        // Given existing limits in Room DB
        appLimitDao.upsertAppLimit(
            AppLimitEntity(
                packageName = "com.instagram.android",
                appName = "Instagram",
                dailyTimeLimitMinutes = 30,
                dailyLaunchLimit = 10,
                currentDayUsageMs = 0L,
                currentDayLaunches = 2
            )
        )
        appLimitDao.upsertAppLimit(
            AppLimitEntity(
                packageName = "com.reddit.frontpage",
                appName = "Reddit",
                dailyTimeLimitMinutes = 20,
                dailyLaunchLimit = 5,
                currentDayUsageMs = 5000L,
                currentDayLaunches = 1
            )
        )

        // Mock current day usage stats
        val mockUsage = mapOf(
            "com.instagram.android" to 20 * 60 * 1000L, // changed from 0 to 20 mins
            "com.reddit.frontpage" to 5000L             // unchanged
        )

        val tracker = UsageStatsTracker(
            context = context,
            appOpsChecker = { true },
            usageStatsProvider = { _, _ -> mockUsage }
        )

        tracker.syncUsageWithDatabase(appLimitDao)

        val instagram = appLimitDao.getAppLimitSync("com.instagram.android")
        val reddit = appLimitDao.getAppLimitSync("com.reddit.frontpage")

        assertEquals(20 * 60 * 1000L, instagram?.currentDayUsageMs)
        assertEquals(2, instagram?.currentDayLaunches) // launches preserved

        assertEquals(5000L, reddit?.currentDayUsageMs)
        assertEquals(1, reddit?.currentDayLaunches)
    }

    @Test
    fun testRecordAppLaunchIncrementsCount() = runBlocking {
        appLimitDao.upsertAppLimit(
            AppLimitEntity(
                packageName = "com.tiktok.android",
                appName = "TikTok",
                dailyTimeLimitMinutes = 15,
                dailyLaunchLimit = 5,
                currentDayUsageMs = 12000L,
                currentDayLaunches = 0
            )
        )

        val tracker = UsageStatsTracker(context = context)

        tracker.recordAppLaunch("com.tiktok.android", appLimitDao)
        var tiktok = appLimitDao.getAppLimitSync("com.tiktok.android")
        assertEquals(1, tiktok?.currentDayLaunches)
        assertEquals(12000L, tiktok?.currentDayUsageMs)

        tracker.recordAppLaunch("com.tiktok.android", appLimitDao)
        tiktok = appLimitDao.getAppLimitSync("com.tiktok.android")
        assertEquals(2, tiktok?.currentDayLaunches)
    }
}
