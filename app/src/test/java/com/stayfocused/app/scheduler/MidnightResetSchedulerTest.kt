package com.stayfocused.app.scheduler

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.data.local.entities.AppLimitEntity
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
import org.robolectric.shadows.ShadowAlarmManager
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@Suppress("DEPRECATION")
class MidnightResetSchedulerTest {

    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private lateinit var shadowAlarmManager: ShadowAlarmManager
    private lateinit var db: StayFocusedDatabase
    private lateinit var scheduler: MidnightResetScheduler

    private val zoneId = ZoneId.of("UTC")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        shadowAlarmManager = shadowOf(alarmManager)

        db = Room.inMemoryDatabaseBuilder(context, StayFocusedDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        scheduler = MidnightResetScheduler(
            context = context,
            alarmManager = alarmManager,
            exactAlarmPermissionChecker = { true }
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testGetNextMidnightCalculation() {
        // Given 2026-09-24 15:45:00 UTC
        val fixedDateTime = ZonedDateTime.of(2026, 9, 24, 15, 45, 0, 0, zoneId)
        val fixedNow = fixedDateTime.toInstant().toEpochMilli()

        val nextMidnight = scheduler.getNextMidnightTimestamp(nowMs = fixedNow, zoneId = zoneId)
        val expectedMidnight = ZonedDateTime.of(2026, 9, 25, 0, 0, 0, 0, zoneId)
            .toInstant().toEpochMilli()

        assertEquals("Next midnight should be 2026-09-25 00:00:00 UTC", expectedMidnight, nextMidnight)
    }

    @Test
    fun testScheduleNextMidnightResetArming() {
        scheduler.scheduleNextMidnightReset()

        val nextAlarm = shadowAlarmManager.nextScheduledAlarm
        assertNotNull("An alarm should be scheduled", nextAlarm)
        assertEquals(AlarmManager.RTC_WAKEUP, nextAlarm.type)
        assertTrue("Alarm time must be in the future", nextAlarm.triggerAtTime > System.currentTimeMillis())
    }

    @Test
    fun testResetDailyUsageInDatabase() = runBlocking {
        val appLimitDao = db.appLimitDao()
        appLimitDao.upsertAppLimit(
            AppLimitEntity(
                packageName = "com.instagram.android",
                appName = "Instagram",
                dailyTimeLimitMinutes = 30,
                dailyLaunchLimit = 10,
                currentDayUsageMs = 45000L,
                currentDayLaunches = 8,
                lastResetTimestamp = 1000L
            )
        )

        val resetTime = 2000000L
        appLimitDao.resetDailyUsage(resetTime)

        val updated = appLimitDao.getAppLimitSync("com.instagram.android")
        assertNotNull(updated)
        assertEquals(0L, updated?.currentDayUsageMs)
        assertEquals(0, updated?.currentDayLaunches)
        assertEquals(resetTime, updated?.lastResetTimestamp)
    }

    @Test
    fun testMidnightResetReceiverHandlesAction() = runBlocking {
        val appLimitDao = db.appLimitDao()
        appLimitDao.upsertAppLimit(
            AppLimitEntity(
                packageName = "com.twitter.android",
                appName = "X",
                dailyTimeLimitMinutes = 15,
                dailyLaunchLimit = 5,
                currentDayUsageMs = 120000L,
                currentDayLaunches = 12,
                lastResetTimestamp = 1000L
            )
        )

        val receiver = MidnightResetReceiver()
        val intent = Intent(MidnightResetReceiver.ACTION_MIDNIGHT_RESET)

        // Execute receiver logic directly with our db
        receiver.executeReset(db, scheduler)

        val updated = appLimitDao.getAppLimitSync("com.twitter.android")
        assertNotNull(updated)
        assertEquals(0L, updated?.currentDayUsageMs)
        assertEquals(0, updated?.currentDayLaunches)

        val nextAlarm = shadowAlarmManager.nextScheduledAlarm
        assertNotNull("Next alarm should be scheduled after reset", nextAlarm)
    }
}
