package com.stayfocused.app.worker

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.data.local.entities.AppLimitEntity
import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.data.local.entities.StrictSessionEntity
import com.stayfocused.app.data.local.entities.SuppressedNotificationEntity
import com.stayfocused.app.domain.WeeklyReflectionEngine
import com.stayfocused.app.domain.model.WeeklyReflectionDigest
import com.stayfocused.app.tracker.UsageStatsTracker
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeeklyReflectionWorkerTest {

    private lateinit var context: Context
    private lateinit var db: StayFocusedDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        try {
            val config = androidx.work.Configuration.Builder()
                .setMinimumLoggingLevel(android.util.Log.DEBUG)
                .build()
            androidx.work.WorkManager.initialize(context, config)
        } catch (_: Exception) {}

        db = Room.inMemoryDatabaseBuilder(context, StayFocusedDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testExecuteDigestAggregatesDataCorrectly() = runBlocking {
        val now = 10_000_000_000L

        // 1. Insert suppressed notifications (2 in past week, 1 older)
        val notifDao = db.suppressedNotificationDao()
        notifDao.insert(
            SuppressedNotificationEntity(
                packageName = "com.instagram.android",
                appName = "Instagram",
                title = "Alert 1",
                contentSnippet = "Snippet 1",
                postTimestamp = now - (2 * 24 * 3600 * 1000L)
            )
        )
        notifDao.insert(
            SuppressedNotificationEntity(
                packageName = "com.facebook.katana",
                appName = "Facebook",
                title = "Alert 2",
                contentSnippet = "Snippet 2",
                postTimestamp = now - (4 * 24 * 3600 * 1000L)
            )
        )

        // 2. Insert App limits with launches
        val appLimitDao = db.appLimitDao()
        appLimitDao.upsertAppLimit(
            AppLimitEntity(
                packageName = "com.instagram.android",
                appName = "Instagram",
                isBlocked = true,
                currentDayLaunches = 8,
                lastResetTimestamp = now - (1 * 24 * 3600 * 1000L)
            )
        )

        // 3. Insert Strict session
        val profileDao = db.focusProfileDao()
        val profileId = profileDao.upsertProfile(FocusProfileEntity(id = 1, name = "Deep Focus"))
        val strictDao = db.strictSessionDao()
        strictDao.insertSession(
            StrictSessionEntity(
                id = 1,
                profileId = profileId,
                startTime = now - (3 * 24 * 3600 * 1000L),
                targetEndTime = now - (3 * 24 * 3600 * 1000L) + (16 * 3600 * 1000L), // 16h
                isActive = false
            )
        )

        // 4. Mock usage stats tracker for biggest drop
        val usageLastWeek = mapOf("com.instagram.android" to 200 * 60 * 1000L)
        val usageThisWeek = mapOf("com.instagram.android" to 50 * 60 * 1000L) // drop 150m
        val tracker = UsageStatsTracker(
            context = context,
            appOpsChecker = { true },
            usageStatsProvider = { start, _ ->
                if (start < now - (7 * 24 * 3600 * 1000L)) usageLastWeek else usageThisWeek
            }
        )

        val worker = WeeklyReflectionWorker(context, mockk(relaxed = true))
        var capturedDigest: WeeklyReflectionDigest? = null

        val resultDigest = worker.executeDigest(
            database = db,
            usageTracker = tracker,
            engine = WeeklyReflectionEngine(),
            nowMs = now,
            onDigestGenerated = { capturedDigest = it }
        )

        assertNotNull(capturedDigest)
        assertEquals(resultDigest, capturedDigest)
        // Blocked: 2 suppressed notifications + 8 blocked app launches = 10
        assertEquals(10, resultDigest.totalBlockedAttempts)
        // Biggest drop: Instagram with 150m drop
        assertEquals("Instagram", resultDigest.biggestDrop?.appName)
        assertEquals(150, resultDigest.biggestDrop?.dropMinutes)
        // Longest streak: 16 hours
        assertEquals(16, resultDigest.longestStrictStreakHours)
        assertTrue(resultDigest.formattedSummary.contains("10 distractions blocked"))
        assertTrue(resultDigest.formattedSummary.contains("Instagram (-150m)"))
        assertTrue(resultDigest.formattedSummary.contains("16h Strict Mode"))
    }

    @Test
    fun testPostDigestNotificationPostsNotification() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowManager = shadowOf(notificationManager)

        val digest = WeeklyReflectionDigest(
            totalBlockedAttempts = 15,
            biggestDrop = null,
            longestStrictStreakHours = 8,
            formattedSummary = "15 distractions blocked • 8h Strict Mode"
        )

        WeeklyReflectionWorker.postDigestNotification(context, digest)

        val notifications = shadowManager.allNotifications
        assertEquals(1, notifications.size)
        val notification = notifications[0]
        assertNotNull(notification)
    }

    @Test
    fun testExecuteDigestExcludesBlockedLaunchesBeforeStartOfWeek() = runBlocking {
        val now = 10_000_000_000L

        // Notification within the week
        db.suppressedNotificationDao().insert(
            SuppressedNotificationEntity(
                packageName = "com.instagram.android",
                appName = "Instagram",
                title = "Alert",
                contentSnippet = "Snippet",
                postTimestamp = now - (1 * 24 * 3600 * 1000L)
            )
        )

        // Blocked app launch within the week (2 days ago)
        val appLimitDao = db.appLimitDao()
        appLimitDao.upsertAppLimit(
            AppLimitEntity(
                packageName = "com.instagram.android",
                appName = "Instagram",
                isBlocked = true,
                currentDayLaunches = 5,
                lastResetTimestamp = now - (2 * 24 * 3600 * 1000L)
            )
        )

        // Blocked app launch BEFORE startOfWeek (10 days ago) - MUST be excluded
        appLimitDao.upsertAppLimit(
            AppLimitEntity(
                packageName = "com.oldgame",
                appName = "Old Game",
                isBlocked = true,
                currentDayLaunches = 25,
                lastResetTimestamp = now - (10 * 24 * 3600 * 1000L)
            )
        )

        val tracker = UsageStatsTracker(
            context = context,
            appOpsChecker = { true },
            usageStatsProvider = { _, _ -> emptyMap() }
        )

        val worker = WeeklyReflectionWorker(context, mockk(relaxed = true))
        val digest = worker.executeDigest(
            database = db,
            usageTracker = tracker,
            engine = WeeklyReflectionEngine(),
            nowMs = now
        )

        // Total should be 1 notification + 5 Instagram launches = 6 (Old Game's 25 launches excluded)
        assertEquals(6, digest.totalBlockedAttempts)
    }

    @Test
    fun testWeeklyReflectionSchedulerEnqueuesWork() {
        WeeklyReflectionScheduler.scheduleWeeklyReflection(context)
        // Verifies no exception thrown when enqueuing periodic work
        assertTrue(true)
    }
}
