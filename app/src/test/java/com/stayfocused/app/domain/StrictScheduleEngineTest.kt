package com.stayfocused.app.domain

import com.stayfocused.app.data.local.entities.StrictScheduleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class StrictScheduleEngineTest {

    private val engine = StrictScheduleEngine()
    private val zoneId = ZoneId.of("UTC")

    // Helper to create ZonedDateTime
    private fun zdt(year: Int, month: Int, day: Int, hour: Int, minute: Int): ZonedDateTime {
        return ZonedDateTime.of(LocalDate.of(year, month, day), LocalTime.of(hour, minute), zoneId)
    }

    @Test
    fun isDayActive_matchesBitmaskCorrectly() {
        // Mon (1) + Wed (4) + Fri (16) = 21
        val mask = 1 or 4 or 16
        assertTrue(engine.isDayActive(mask, 1)) // Mon
        assertFalse(engine.isDayActive(mask, 2)) // Tue
        assertTrue(engine.isDayActive(mask, 3)) // Wed
        assertFalse(engine.isDayActive(mask, 4)) // Thu
        assertTrue(engine.isDayActive(mask, 5)) // Fri
        assertFalse(engine.isDayActive(mask, 6)) // Sat
        assertFalse(engine.isDayActive(mask, 7)) // Sun
    }

    @Test
    fun sameDaySchedule_evaluatesActiveAndInactiveRanges() {
        // Mon-Fri: 09:00 (540m) to 17:00 (1020m)
        val schedule = StrictScheduleEntity(
            id = 1,
            name = "Work Hours",
            daysOfWeekMask = 31, // Mon-Fri
            startMinuteOfDay = 540,
            endMinuteOfDay = 1020,
            profileId = 1
        )

        // Monday (2026-10-05) at 08:59 -> inactive
        val t1 = zdt(2026, 10, 5, 8, 59)
        assertFalse(engine.isScheduleActiveAt(schedule, t1, t1.toInstant().toEpochMilli()))

        // Monday at 09:00 -> active
        val t2 = zdt(2026, 10, 5, 9, 0)
        assertTrue(engine.isScheduleActiveAt(schedule, t2, t2.toInstant().toEpochMilli()))

        // Monday at 12:30 -> active
        val t3 = zdt(2026, 10, 5, 12, 30)
        assertTrue(engine.isScheduleActiveAt(schedule, t3, t3.toInstant().toEpochMilli()))

        // Monday at 17:00 -> inactive (exclusive end boundary)
        val t4 = zdt(2026, 10, 5, 17, 0)
        assertFalse(engine.isScheduleActiveAt(schedule, t4, t4.toInstant().toEpochMilli()))

        // Saturday (2026-10-10) at 12:00 -> inactive (weekend)
        val t5 = zdt(2026, 10, 10, 12, 0)
        assertFalse(engine.isScheduleActiveAt(schedule, t5, t5.toInstant().toEpochMilli()))
    }

    @Test
    fun crossMidnightSchedule_evaluatesAcrossMidnightProperly() {
        // Nightly 22:00 (1320m) to 06:00 (360m), active on Friday night (16)
        val schedule = StrictScheduleEntity(
            id = 1,
            name = "Night Focus",
            daysOfWeekMask = 16, // Friday night only
            startMinuteOfDay = 1320,
            endMinuteOfDay = 360,
            profileId = 1
        )

        // Friday (2026-10-09) at 21:59 -> inactive
        val t1 = zdt(2026, 10, 9, 21, 59)
        assertFalse(engine.isScheduleActiveAt(schedule, t1, t1.toInstant().toEpochMilli()))

        // Friday at 22:00 -> active
        val t2 = zdt(2026, 10, 9, 22, 0)
        assertTrue(engine.isScheduleActiveAt(schedule, t2, t2.toInstant().toEpochMilli()))

        // Friday at 23:59 -> active
        val t3 = zdt(2026, 10, 9, 23, 59)
        assertTrue(engine.isScheduleActiveAt(schedule, t3, t3.toInstant().toEpochMilli()))

        // Saturday (2026-10-10) at 00:01 -> active (part of Friday night's session)
        val t4 = zdt(2026, 10, 10, 0, 1)
        assertTrue(engine.isScheduleActiveAt(schedule, t4, t4.toInstant().toEpochMilli()))

        // Saturday at 05:59 -> active
        val t5 = zdt(2026, 10, 10, 5, 59)
        assertTrue(engine.isScheduleActiveAt(schedule, t5, t5.toInstant().toEpochMilli()))

        // Saturday at 06:00 -> inactive
        val t6 = zdt(2026, 10, 10, 6, 0)
        assertFalse(engine.isScheduleActiveAt(schedule, t6, t6.toInstant().toEpochMilli()))
    }

    @Test
    fun SundayToMonday_crossMidnight_wrapsCorrectly() {
        // Sunday night (64) 23:00 (1380m) to 04:00 (240m) Monday morning
        val schedule = StrictScheduleEntity(
            id = 1,
            name = "Sunday Night",
            daysOfWeekMask = 64, // Sunday
            startMinuteOfDay = 1380,
            endMinuteOfDay = 240,
            profileId = 1
        )

        // Monday (2026-10-12) at 02:00 -> active because yesterday was Sunday
        val mondayEarly = zdt(2026, 10, 12, 2, 0)
        assertTrue(engine.isScheduleActiveAt(schedule, mondayEarly, mondayEarly.toInstant().toEpochMilli()))

        // Tuesday (2026-10-13) at 02:00 -> inactive
        val tuesdayEarly = zdt(2026, 10, 13, 2, 0)
        assertFalse(engine.isScheduleActiveAt(schedule, tuesdayEarly, tuesdayEarly.toInstant().toEpochMilli()))
    }

    @Test
    fun dismissedUntilEpochMs_suppressesActivationDuringDismissedWindow() {
        val now = zdt(2026, 10, 5, 10, 0) // Monday 10:00
        val epochMs = now.toInstant().toEpochMilli()

        val schedule = StrictScheduleEntity(
            id = 1,
            name = "Work",
            daysOfWeekMask = 31,
            startMinuteOfDay = 540,
            endMinuteOfDay = 1020,
            profileId = 1,
            dismissedUntilEpochMs = epochMs + 3600_000L // dismissed for next 1 hour
        )

        // Normally active at 10:00, but dismissedUntilEpochMs is in the future
        assertFalse(engine.isScheduleActiveAt(schedule, now, epochMs))

        // After dismissed time passes
        val later = now.plusHours(2)
        val laterMs = later.toInstant().toEpochMilli()
        assertTrue(engine.isScheduleActiveAt(schedule, later, laterMs))
    }

    @Test
    fun disabledSchedule_isNeverActive() {
        val now = zdt(2026, 10, 5, 10, 0)
        val schedule = StrictScheduleEntity(
            id = 1,
            name = "Work",
            daysOfWeekMask = 31,
            startMinuteOfDay = 540,
            endMinuteOfDay = 1020,
            profileId = 1,
            isEnabled = false
        )
        assertFalse(engine.isScheduleActiveAt(schedule, now, now.toInstant().toEpochMilli()))
    }

    @Test
    fun strictestChallenge_prioritizesCorrectly() {
        assertEquals("EXPIRATION_ONLY", engine.getStrictestChallenge(listOf("RANDOM_TEXT", "EXPIRATION_ONLY", "COOL_DOWN")))
        assertEquals("COOL_DOWN", engine.getStrictestChallenge(listOf("RANDOM_TEXT", "COOL_DOWN")))
        assertEquals("RANDOM_TEXT", engine.getStrictestChallenge(listOf("RANDOM_TEXT")))
        assertEquals("EXPIRATION_ONLY", engine.getStrictestChallenge(emptyList()))
    }

    @Test
    fun calculateNextBoundaryEpochMs_clampsMinimumDelayToPreventLoops() {
        val schedule = StrictScheduleEntity(
            id = 1,
            name = "Next Boundary Test",
            daysOfWeekMask = 31, // Mon-Fri
            startMinuteOfDay = 540, // 09:00
            endMinuteOfDay = 1020, // 17:00
            profileId = 1
        )

        // At 08:59:58 (2 seconds before start)
        val now = zdt(2026, 10, 5, 8, 59).plusSeconds(58)
        val nowEpochMs = now.toInstant().toEpochMilli()

        val nextBoundary = engine.calculateNextBoundaryEpochMs(listOf(schedule), now, nowEpochMs)
        assertNotNull(nextBoundary)

        // Must be clamped to at least nowEpochMs + 5000L
        assertTrue(nextBoundary!! >= nowEpochMs + 5000L)
    }

    @Test
    fun calculateCurrentWindowEndTime_evaluatesCorrectly_beforeAndAfterMidnight() {
        // Friday night: 22:00 to 06:00 (Saturday morning)
        val schedule = StrictScheduleEntity(
            id = 1,
            name = "Overnight",
            daysOfWeekMask = 16, // Friday
            startMinuteOfDay = 1320, // 22:00
            endMinuteOfDay = 360, // 06:00
            profileId = 1
        )

        // Case 1: Evaluated on Friday at 23:00 (before midnight)
        val friday23 = zdt(2026, 10, 9, 23, 0)
        val expectedSaturdayEnd = zdt(2026, 10, 10, 6, 0).toInstant().toEpochMilli()
        assertEquals(expectedSaturdayEnd, engine.calculateCurrentWindowEndTime(schedule, friday23))

        // Case 2: Evaluated on Saturday at 02:00 (after midnight)
        val saturday02 = zdt(2026, 10, 10, 2, 0)
        assertEquals(expectedSaturdayEnd, engine.calculateCurrentWindowEndTime(schedule, saturday02))
    }

    @Test
    fun calculateNextBoundaryEpochMs_afterMidnight_findsUpcomingEndBoundary() {
        // Friday night: 22:00 to 06:00
        val schedule = StrictScheduleEntity(
            id = 1,
            name = "Overnight",
            daysOfWeekMask = 16, // Friday
            startMinuteOfDay = 1320, // 22:00
            endMinuteOfDay = 360, // 06:00
            profileId = 1
        )

        // Evaluated on Saturday at 02:00 (inside the Friday night cross-midnight window)
        val saturday02 = zdt(2026, 10, 10, 2, 0)
        val nowMs = saturday02.toInstant().toEpochMilli()

        val nextBoundary = engine.calculateNextBoundaryEpochMs(listOf(schedule), saturday02, nowMs)
        val expectedEndMs = zdt(2026, 10, 10, 6, 0).toInstant().toEpochMilli()

        assertNotNull(nextBoundary)
        assertEquals(expectedEndMs, nextBoundary)
    }
}

