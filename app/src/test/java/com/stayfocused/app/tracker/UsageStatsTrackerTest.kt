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
    fun testSyncUsageWithDatabaseResetsLaunchesOnNewDay() = runBlocking {
        val yesterdayMs = System.currentTimeMillis() - 25 * 3600 * 1000L
        appLimitDao.upsertAppLimit(
            AppLimitEntity(
                packageName = "com.google.android.youtube",
                appName = "YouTube",
                dailyTimeLimitMinutes = 30,
                currentDayUsageMs = 45 * 60 * 1000L,
                currentDayLaunches = 10,
                lastResetTimestamp = yesterdayMs
            )
        )

        val mockUsage = mapOf(
            "com.google.android.youtube" to 5 * 60 * 1000L // 5m today
        )
        val tracker = UsageStatsTracker(
            context = context,
            appOpsChecker = { true },
            usageStatsProvider = { _, _ -> mockUsage }
        )

        tracker.syncUsageWithDatabase(appLimitDao)

        val youtube = appLimitDao.getAppLimitSync("com.google.android.youtube")
        assertEquals(5 * 60 * 1000L, youtube?.currentDayUsageMs)
        assertEquals(0, youtube?.currentDayLaunches) // reset to 0 for new day
        assertTrue((youtube?.lastResetTimestamp ?: 0L) >= tracker.getStartOfToday())
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

    @Test
    fun testQueryTotalDeviceScreenTimeMsAggregatesAllApps() {
        val mockData = mapOf(
            "com.google.android.youtube" to (2 * 3600 + 5 * 60) * 1000L, // 2h 5m
            "com.stayfocused" to (4 * 60 + 50) * 1000L,                   // 4m 50s
            "com.android.settings" to (2 * 60 + 48) * 1000L               // 2m 48s
        )
        val expectedTotal = mockData.values.sum()

        val tracker = UsageStatsTracker(
            context = context,
            appOpsChecker = { true },
            usageStatsProvider = { _, _ -> mockData }
        )

        val totalMs = tracker.queryTotalDeviceScreenTimeMs()
        assertEquals(expectedTotal, totalMs)
    }

    @Test
    fun testQueryTopUsedAppsOrdersDescendingAndResolvesLabels() {
        val mockData = mapOf(
            "com.stayfocused" to (4 * 60) * 1000L,
            "com.google.android.youtube" to (120 * 60) * 1000L,
            "com.android.chrome" to (15 * 60) * 1000L
        )

        val tracker = UsageStatsTracker(
            context = context,
            appOpsChecker = { true },
            usageStatsProvider = { _, _ -> mockData }
        )

        val topApps = tracker.queryTopUsedApps(limit = 2)
        assertEquals(2, topApps.size)
        assertEquals("com.google.android.youtube", topApps[0].packageName)
        assertEquals((120 * 60) * 1000L, topApps[0].foregroundTimeMs)
        assertEquals("com.android.chrome", topApps[1].packageName)
        assertEquals((15 * 60) * 1000L, topApps[1].foregroundTimeMs)
    }

    @Test
    fun testProcessEventStreamIgnoresEventsEntirelyBeforeWindowStart() {
        val tracker = UsageStatsTracker(context = context)
        val midnight = 1727913600000L // 00:00:00
        val windowEnd = midnight + 12 * 3600 * 1000L // 12:00:00

        // Yesterday event from 16:00 to 17:30
        val yesterdayStart = midnight - 8 * 3600 * 1000L
        val yesterdayEnd = midnight - 6 * 3600 * 1000L - 30 * 60 * 1000L

        val events = sequenceOf(
            UsageEventRecord("com.google.android.youtube", "MainActivity", UsageStatsTracker.EVENT_ACTIVITY_RESUMED, yesterdayStart),
            UsageEventRecord("com.google.android.youtube", "MainActivity", UsageStatsTracker.EVENT_ACTIVITY_PAUSED, yesterdayEnd)
        )

        val result = tracker.processEventStream(events, windowStart = midnight, windowEnd = windowEnd)
        assertTrue("Events entirely before midnight must yield 0 ms", result.isEmpty())
    }

    @Test
    fun testProcessEventStreamClampsSessionCrossingMidnight() {
        val tracker = UsageStatsTracker(context = context)
        val midnight = 1727913600000L // 00:00:00
        val windowEnd = midnight + 4 * 3600 * 1000L

        // YouTube opened at 23:45 yesterday (15m before midnight) and paused at 00:15 today (15m after midnight)
        val sessionStart = midnight - 15 * 60 * 1000L
        val sessionEnd = midnight + 15 * 60 * 1000L

        val events = sequenceOf(
            UsageEventRecord("com.google.android.youtube", "WatchActivity", UsageStatsTracker.EVENT_ACTIVITY_RESUMED, sessionStart),
            UsageEventRecord("com.google.android.youtube", "WatchActivity", UsageStatsTracker.EVENT_ACTIVITY_PAUSED, sessionEnd)
        )

        val result = tracker.processEventStream(events, windowStart = midnight, windowEnd = windowEnd)
        assertEquals("Should only credit 15 minutes that occurred after midnight", 15 * 60 * 1000L, result["com.google.android.youtube"])
    }

    @Test
    fun testProcessEventStreamCalculatesSessionInsideWindow() {
        val tracker = UsageStatsTracker(context = context)
        val midnight = 1727913600000L
        val windowEnd = midnight + 12 * 3600 * 1000L

        val start = midnight + 2 * 3600 * 1000L // 02:00
        val end = midnight + 4 * 3600 * 1000L + 5 * 60 * 1000L // 04:05 (2h 5m)

        val events = sequenceOf(
            UsageEventRecord("com.google.android.youtube", "MainActivity", UsageStatsTracker.EVENT_ACTIVITY_RESUMED, start),
            UsageEventRecord("com.google.android.youtube", "MainActivity", UsageStatsTracker.EVENT_ACTIVITY_PAUSED, end)
        )

        val result = tracker.processEventStream(events, windowStart = midnight, windowEnd = windowEnd)
        val expectedMs = (2 * 3600 + 5 * 60) * 1000L
        assertEquals("Should credit exactly 2 hrs 05 mins", expectedMs, result["com.google.android.youtube"])
    }

    @Test
    fun testProcessEventStreamPausesOnScreenNonInteractiveAndKeyguard() {
        val tracker = UsageStatsTracker(context = context)
        val midnight = 1727913600000L
        val windowEnd = midnight + 12 * 3600 * 1000L

        val t1 = midnight + 3600 * 1000L // 01:00
        val t2 = t1 + 10 * 60 * 1000L    // 01:10 (Screen locks)
        val t3 = t2 + 20 * 60 * 1000L    // 01:30 (Screen unlocks & resumes)
        val t4 = t3 + 15 * 60 * 1000L    // 01:45 (User exits app)

        val events = sequenceOf(
            UsageEventRecord("com.stayfocused", null, UsageStatsTracker.EVENT_ACTIVITY_RESUMED, t1),
            UsageEventRecord(null, null, UsageStatsTracker.EVENT_KEYGUARD_SHOWN, t2),
            UsageEventRecord("com.stayfocused", null, UsageStatsTracker.EVENT_ACTIVITY_RESUMED, t3),
            UsageEventRecord("com.stayfocused", null, UsageStatsTracker.EVENT_ACTIVITY_PAUSED, t4)
        )

        val result = tracker.processEventStream(events, windowStart = midnight, windowEnd = windowEnd)
        // 10m + 15m = 25m total (locked 20m excluded)
        val expectedMs = 25 * 60 * 1000L
        assertEquals("Screen locked time must not be counted", expectedMs, result["com.stayfocused"])
    }

    @Test
    fun testProcessEventStreamHandlesActiveAppAtWindowEnd() {
        val tracker = UsageStatsTracker(context = context)
        val midnight = 1727913600000L
        val windowEnd = midnight + 30 * 60 * 1000L // 00:30

        val start = midnight + 10 * 60 * 1000L // 00:10

        val events = sequenceOf(
            UsageEventRecord("com.android.chrome", null, UsageStatsTracker.EVENT_ACTIVITY_RESUMED, start)
            // No pause event, still foreground at windowEnd
        )

        val result = tracker.processEventStream(events, windowStart = midnight, windowEnd = windowEnd)
        assertEquals("App active at windowEnd must be credited up to windowEnd", 20 * 60 * 1000L, result["com.android.chrome"])
    }

    @Test
    fun testProcessEventStreamHandlesActivitySwitchWithinSamePackage() {
        val tracker = UsageStatsTracker(context = context)
        val midnight = 1727913600000L
        val windowEnd = midnight + 2 * 3600 * 1000L

        val t1 = midnight + 10 * 60 * 1000L // 00:10 Main starts
        val t2 = midnight + 15 * 60 * 1000L // 00:15 Detail starts
        val t2OldPause = t2 + 50            // 00:15.050 Main pauses (out of order / transition)
        val t3 = midnight + 30 * 60 * 1000L // 00:30 Detail pauses

        val events = sequenceOf(
            UsageEventRecord("com.google.android.youtube", "MainActivity", UsageStatsTracker.EVENT_ACTIVITY_RESUMED, t1),
            UsageEventRecord("com.google.android.youtube", "WatchActivity", UsageStatsTracker.EVENT_ACTIVITY_RESUMED, t2),
            UsageEventRecord("com.google.android.youtube", "MainActivity", UsageStatsTracker.EVENT_ACTIVITY_PAUSED, t2OldPause),
            UsageEventRecord("com.google.android.youtube", "WatchActivity", UsageStatsTracker.EVENT_ACTIVITY_PAUSED, t3)
        )

        val result = tracker.processEventStream(events, windowStart = midnight, windowEnd = windowEnd)
        // 00:10 to 00:30 = 20 minutes
        assertEquals("Internal activity transitions must not prematurely terminate active session", 20 * 60 * 1000L, result["com.google.android.youtube"])
    }

    @Test
    fun testQueryForegroundUsageWithEventStreamProvider() {
        val midnight = 1727913600000L
        val windowEnd = midnight + 3600 * 1000L

        val events = sequenceOf(
            UsageEventRecord("com.google.android.youtube", null, UsageStatsTracker.EVENT_ACTIVITY_RESUMED, midnight + 1000L),
            UsageEventRecord("com.google.android.youtube", null, UsageStatsTracker.EVENT_ACTIVITY_PAUSED, midnight + 61000L)
        )

        val tracker = UsageStatsTracker(
            context = context,
            appOpsChecker = { true },
            eventStreamProvider = { _, _ -> events }
        )

        val result = tracker.queryForegroundUsage(startTime = midnight, endTime = windowEnd)
        assertEquals(60000L, result["com.google.android.youtube"])
    }

    @Test
    fun testQueryPackageUsageTodayReturnsSpecificAppUsage() {
        val mockData = mapOf(
            "com.google.android.youtube" to 500000L,
            "com.android.chrome" to 100000L
        )

        val tracker = UsageStatsTracker(
            context = context,
            appOpsChecker = { true },
            usageStatsProvider = { _, _ -> mockData }
        )

        assertEquals(500000L, tracker.queryPackageUsageToday("com.google.android.youtube"))
        assertEquals(0L, tracker.queryPackageUsageToday("com.unknown.app"))
    }
}
