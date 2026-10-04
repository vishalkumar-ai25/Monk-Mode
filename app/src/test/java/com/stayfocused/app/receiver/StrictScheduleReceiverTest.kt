package com.stayfocused.app.receiver

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.data.local.entities.FocusProfileEntity
import com.stayfocused.app.data.local.entities.StrictScheduleEntity
import com.stayfocused.app.data.local.entities.StrictSessionEntity
import com.stayfocused.app.domain.StrictScheduleEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
class StrictScheduleReceiverTest {

    private lateinit var db: StayFocusedDatabase
    private lateinit var context: Context
    private val receiver = StrictScheduleReceiver()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, StayFocusedDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        runBlocking {
            db.focusProfileDao().upsertProfile(
                FocusProfileEntity(
                    id = 1,
                    name = "Work Focus",
                    isActive = false,
                    isStrictMode = true,
                    activeDaysMask = 127
                )
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun reconcileSchedules_armsStrictSessionAndSwitchesProfileWhenInWindow() = runBlocking {
        // Schedule Mon-Fri 09:00 to 17:00
        val schedule = StrictScheduleEntity(
            id = 1,
            name = "Workday",
            daysOfWeekMask = 31, // Mon-Fri
            startMinuteOfDay = 540, // 09:00
            endMinuteOfDay = 1020, // 17:00
            profileId = 1,
            deactivationChallenge = "RANDOM_TEXT"
        )
        db.strictScheduleDao().insertSchedule(schedule)

        // Monday at 10:00 (inside window)
        val monday10am = ZonedDateTime.of(
            LocalDate.of(2026, 10, 5),
            LocalTime.of(10, 0),
            ZoneId.of("UTC")
        )
        val epochMs = monday10am.toInstant().toEpochMilli()

        receiver.reconcileSchedules(
            context = context,
            database = db,
            nowEpochMs = epochMs,
            nowZdt = monday10am
        )

        val activeSession = db.strictSessionDao().getActiveStrictSessionSync()
        assertNotNull(activeSession)
        assertTrue(activeSession?.isActive == true)
        assertEquals(1L, activeSession?.profileId)
        assertEquals("RANDOM_TEXT", activeSession?.deactivationChallenge)

        // Profile must also be active in Room!
        val activeProfile = db.focusProfileDao().getActiveProfileSync()
        assertEquals(1L, activeProfile?.id)
    }

    @Test
    fun reconcileSchedules_deactivatesSessionWhenWindowElapsed() = runBlocking {
        // Schedule Mon-Fri 09:00 to 17:00
        val schedule = StrictScheduleEntity(
            id = 1,
            name = "Workday",
            daysOfWeekMask = 31,
            startMinuteOfDay = 540,
            endMinuteOfDay = 1020,
            profileId = 1
        )
        db.strictScheduleDao().insertSchedule(schedule)

        // 1. Arm at 10:00 with startElapsedRealtime
        val startElapsed = 10_000L
        val monday10am = ZonedDateTime.of(
            LocalDate.of(2026, 10, 5),
            LocalTime.of(10, 0),
            ZoneId.of("UTC")
        )
        receiver.reconcileSchedules(
            context = context,
            database = db,
            nowEpochMs = monday10am.toInstant().toEpochMilli(),
            nowZdt = monday10am,
            nowElapsedRealtime = startElapsed
        )
        assertNotNull(db.strictSessionDao().getActiveStrictSessionSync())

        // 2. Evaluate at 17:05 (outside window, past targetEndTime, and hardware monotonic clock also advanced > 7 hours)
        val monday505pm = ZonedDateTime.of(
            LocalDate.of(2026, 10, 5),
            LocalTime.of(17, 5),
            ZoneId.of("UTC")
        )
        val elapsedMs = 8 * 3600_000L // 8 hours elapsed on hardware clock
        receiver.reconcileSchedules(
            context = context,
            database = db,
            nowEpochMs = monday505pm.toInstant().toEpochMilli(),
            nowZdt = monday505pm,
            nowElapsedRealtime = startElapsed + elapsedMs
        )

        val activeSessionAfter = db.strictSessionDao().getActiveStrictSessionSync()
        assertNull(activeSessionAfter)
    }

    @Test
    fun reconcileSchedules_detectsClockTampering_suppressesDeactivation() = runBlocking {
        // Active session started at 10:00, target end 11:00 (duration: 3600_000 ms)
        val startTime = 1000_000L
        val targetEndTime = startTime + 3600_000L
        val startElapsedRealtime = 50_000L

        db.strictSessionDao().insertSession(
            StrictSessionEntity(
                id = 1,
                profileId = 1,
                startTime = startTime,
                targetEndTime = targetEndTime,
                startElapsedRealtime = startElapsedRealtime,
                deactivationChallenge = "EXPIRATION_ONLY",
                isActive = true
            )
        )

        // User rolled wall clock forward to 11:05 (nowEpochMs = startTime + 3900_000L)
        // BUT only 60 seconds elapsed on hardware clock (nowElapsedRealtime = 110_000L, actualElapsed = 60_000L)
        val spoofedNowEpoch = startTime + 3900_000L
        val nowElapsed = startElapsedRealtime + 60_000L

        val spoofedZdt = ZonedDateTime.of(
            LocalDate.of(2026, 10, 5),
            LocalTime.of(11, 5),
            ZoneId.of("UTC")
        )

        receiver.reconcileSchedules(
            context = context,
            database = db,
            nowEpochMs = spoofedNowEpoch,
            nowZdt = spoofedZdt,
            nowElapsedRealtime = nowElapsed
        )

        // Session must STILL be active because hardware clock did not elapse
        val sessionStillActive = db.strictSessionDao().getActiveStrictSessionSync()
        assertNotNull(sessionStillActive)
        assertTrue(sessionStillActive?.isActive == true)

        // A CLOCK_TAMPER_DETECTED failsafe log must have been generated
        val logs = db.failsafeLogDao().getAllLogsSync()
        assertTrue(logs.any { it.eventType == "CLOCK_TAMPER_DETECTED" })
    }

    @Test
    fun reconcileSchedules_overlappingSchedule_upgradesToStricterChallenge() = runBlocking {
        // Active session armed with RANDOM_TEXT ending at 12:00
        val monday10am = ZonedDateTime.of(LocalDate.of(2026, 10, 5), LocalTime.of(10, 0), ZoneId.of("UTC"))
        val monday12pm = ZonedDateTime.of(LocalDate.of(2026, 10, 5), LocalTime.of(12, 0), ZoneId.of("UTC"))
        val monday2pm = ZonedDateTime.of(LocalDate.of(2026, 10, 5), LocalTime.of(14, 0), ZoneId.of("UTC"))

        db.strictSessionDao().insertSession(
            StrictSessionEntity(
                id = 1,
                profileId = 1,
                startTime = monday10am.toInstant().toEpochMilli(),
                targetEndTime = monday12pm.toInstant().toEpochMilli(),
                startElapsedRealtime = 100_000L,
                deactivationChallenge = "RANDOM_TEXT",
                isActive = true
            )
        )

        // An overlapping schedule from 11:00 to 14:00 with EXPIRATION_ONLY is enabled
        val overlappingSchedule = StrictScheduleEntity(
            id = 2,
            name = "Overlapping Deep Work",
            daysOfWeekMask = 31,
            startMinuteOfDay = 660, // 11:00
            endMinuteOfDay = 840, // 14:00
            profileId = 1,
            deactivationChallenge = "EXPIRATION_ONLY"
        )
        db.strictScheduleDao().insertSchedule(overlappingSchedule)

        // Reconcile at 11:30 (inside both windows)
        val monday1130am = ZonedDateTime.of(LocalDate.of(2026, 10, 5), LocalTime.of(11, 30), ZoneId.of("UTC"))
        receiver.reconcileSchedules(
            context = context,
            database = db,
            nowEpochMs = monday1130am.toInstant().toEpochMilli(),
            nowZdt = monday1130am,
            nowElapsedRealtime = 190_000L
        )

        val updatedSession = db.strictSessionDao().getActiveStrictSessionSync()
        assertNotNull(updatedSession)
        assertEquals(monday2pm.toInstant().toEpochMilli(), updatedSession?.targetEndTime)
        assertEquals("EXPIRATION_ONLY", updatedSession?.deactivationChallenge)
    }

    @Test
    fun reconcileSchedules_afterReboot_clockForward_sessionRemainsActive() = runBlocking {
        // Active multi-day session created before reboot (7 days duration)
        val startTime = 1000_000L
        val durationMs = 7 * 24 * 3600_000L
        val targetEndTime = startTime + durationMs
        val startElapsedRealtime = 50_000L

        db.strictSessionDao().insertSession(
            StrictSessionEntity(
                id = 1,
                profileId = 1,
                startTime = startTime,
                targetEndTime = targetEndTime,
                startElapsedRealtime = startElapsedRealtime,
                deactivationChallenge = "EXPIRATION_ONLY",
                isActive = true,
                accumulatedMonotonicMs = 0L,
                lastElapsedRealtime = startElapsedRealtime,
                lastWallTime = startTime,
                bootCount = 1
            )
        )

        // Wall-clock reaches target end time 7 days later, but phone has only been awake for 2 hours on this boot
        val spoofedEpochMs = targetEndTime + 60_000L
        val nowElapsed = 2 * 3600_000L
        val spoofedZdt = ZonedDateTime.of(
            LocalDate.of(2026, 10, 12),
            LocalTime.of(10, 1),
            ZoneId.of("UTC")
        )

        receiver.reconcileSchedules(
            context = context,
            database = db,
            nowEpochMs = spoofedEpochMs,
            nowZdt = spoofedZdt,
            nowElapsedRealtime = nowElapsed
        )

        // Session must STILL be active because only 2 hours elapsed out of 7 days!
        val activeSessionAfter = db.strictSessionDao().getActiveStrictSessionSync()
        assertNotNull("Session must remain active despite 7-day wall-clock jump", activeSessionAfter)
        assertTrue(activeSessionAfter?.isActive == true)

        // Now advance monotonic clock to full 7 days + 1s from previous checkpoint
        val fullElapsed = nowElapsed + durationMs + 1000L
        receiver.reconcileSchedules(
            context = context,
            database = db,
            nowEpochMs = spoofedEpochMs + durationMs,
            nowZdt = spoofedZdt.plusDays(7),
            nowElapsedRealtime = fullElapsed
        )

        // Now that full monotonic duration has elapsed, session concludes
        val activeSessionFinal = db.strictSessionDao().getActiveStrictSessionSync()
        assertNull("Session must deactivate once full duration has elapsed", activeSessionFinal)

        val logs = db.failsafeLogDao().getAllLogsSync()
        assertTrue(logs.any { it.eventType == "STRICT_SESSION_EXPIRED" })
    }
}
