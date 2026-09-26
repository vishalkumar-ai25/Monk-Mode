package com.stayfocused.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stayfocused.app.data.local.entities.AppLimitEntity
import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.data.local.entities.StrictSessionEntity
import com.stayfocused.app.data.local.entities.SuppressedNotificationEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WeeklyReflectionDaoTest {

    private lateinit var db: StayFocusedDatabase
    private lateinit var context: Context

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
    fun testSuppressedNotificationCountSince() = runBlocking {
        val dao = db.suppressedNotificationDao()
        val now = 1_000_000L

        // Insert notification from 5 days ago (within week)
        dao.insert(
            SuppressedNotificationEntity(
                packageName = "com.instagram.android",
                appName = "Instagram",
                title = "New follower",
                contentSnippet = "Alice followed you",
                postTimestamp = now - (5 * 24 * 3600 * 1000L)
            )
        )

        // Insert notification from 10 days ago (older than week)
        dao.insert(
            SuppressedNotificationEntity(
                packageName = "com.facebook.katana",
                appName = "Facebook",
                title = "Friend request",
                contentSnippet = "Bob sent a request",
                postTimestamp = now - (10 * 24 * 3600 * 1000L)
            )
        )

        val weekStart = now - (7 * 24 * 3600 * 1000L)
        val count = dao.getSuppressedCountSince(weekStart)
        assertEquals(1, count)
    }

    @Test
    fun testStrictSessionsSince() = runBlocking {
        val profileDao = db.focusProfileDao()
        val profileId = profileDao.upsertProfile(
            FocusProfileEntity(id = 1L, name = "Deep Work")
        )

        val strictDao = db.strictSessionDao()
        val now = 2_000_000L

        // Session from 2 days ago
        strictDao.insertSession(
            StrictSessionEntity(
                id = 10,
                profileId = profileId,
                startTime = now - (2 * 24 * 3600 * 1000L),
                targetEndTime = now - (2 * 24 * 3600 * 1000L) + (4 * 3600 * 1000L),
                isActive = false
            )
        )

        // Session from 12 days ago
        strictDao.insertSession(
            StrictSessionEntity(
                id = 11,
                profileId = profileId,
                startTime = now - (12 * 24 * 3600 * 1000L),
                targetEndTime = now - (12 * 24 * 3600 * 1000L) + (8 * 3600 * 1000L),
                isActive = false
            )
        )

        val weekStart = now - (7 * 24 * 3600 * 1000L)
        val sessions = strictDao.getSessionsSince(weekStart)
        assertEquals(1, sessions.size)
        assertEquals(10L, sessions[0].id)
    }

    @Test
    fun testBlockedAppLaunchesCountSince() = runBlocking {
        val appLimitDao = db.appLimitDao()
        val now = 1_000_000L
        val weekStart = now - (7 * 24 * 3600 * 1000L)

        // Blocked app with launches within current week
        appLimitDao.upsertAppLimit(
            AppLimitEntity(
                packageName = "com.instagram.android",
                appName = "Instagram",
                isBlocked = true,
                currentDayLaunches = 12,
                lastResetTimestamp = now - (2 * 24 * 3600 * 1000L)
            )
        )

        // Allowed app with launches under limit within current week
        appLimitDao.upsertAppLimit(
            AppLimitEntity(
                packageName = "com.slack",
                appName = "Slack",
                dailyLaunchLimit = 20,
                isBlocked = false,
                currentDayLaunches = 5,
                lastResetTimestamp = now - (1 * 24 * 3600 * 1000L)
            )
        )

        // App exceeded launch limit within current week
        appLimitDao.upsertAppLimit(
            AppLimitEntity(
                packageName = "com.tiktok",
                appName = "TikTok",
                dailyLaunchLimit = 10,
                isBlocked = false,
                currentDayLaunches = 15,
                lastResetTimestamp = now - (3 * 24 * 3600 * 1000L)
            )
        )

        // Blocked app from BEFORE current week (should be excluded)
        appLimitDao.upsertAppLimit(
            AppLimitEntity(
                packageName = "com.oldgame",
                appName = "Old Game",
                isBlocked = true,
                currentDayLaunches = 20,
                lastResetTimestamp = now - (10 * 24 * 3600 * 1000L)
            )
        )

        val blockedLaunches = appLimitDao.getBlockedAppLaunchesCountSince(weekStart)
        assertEquals(27, blockedLaunches) // 12 (Instagram) + 15 (TikTok), excluding Old Game
    }
}
