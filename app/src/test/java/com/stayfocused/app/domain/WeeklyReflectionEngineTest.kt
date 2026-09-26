package com.stayfocused.app.domain

import com.stayfocused.app.data.local.entities.StrictSessionEntity
import com.stayfocused.app.domain.model.AppUsageDrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class WeeklyReflectionEngineTest {

    private lateinit var engine: WeeklyReflectionEngine
    private val zoneId = ZoneId.of("UTC")

    @Before
    fun setUp() {
        engine = WeeklyReflectionEngine()
    }

    @Test
    fun testCalculateBiggestDropFindsMaxReduction() {
        val usageLastWeek = mapOf(
            "com.instagram.android" to 300 * 60 * 1000L, // 300m
            "com.google.android.youtube" to 150 * 60 * 1000L, // 150m
            "com.twitter.android" to 60 * 60 * 1000L // 60m
        )
        val usageThisWeek = mapOf(
            "com.instagram.android" to 100 * 60 * 1000L, // drop = 200m
            "com.google.android.youtube" to 50 * 60 * 1000L, // drop = 100m
            "com.twitter.android" to 120 * 60 * 1000L // increased
        )
        val packageToAppName = mapOf(
            "com.instagram.android" to "Instagram",
            "com.google.android.youtube" to "YouTube",
            "com.twitter.android" to "X"
        )

        val drop = engine.calculateBiggestDrop(usageThisWeek, usageLastWeek, packageToAppName)
        assertNotNull(drop)
        assertEquals("com.instagram.android", drop?.packageName)
        assertEquals("Instagram", drop?.appName)
        assertEquals(200, drop?.dropMinutes)
    }

    @Test
    fun testCalculateBiggestDropReturnsNullWhenNoDrops() {
        val usageLastWeek = mapOf(
            "com.instagram.android" to 50 * 60 * 1000L
        )
        val usageThisWeek = mapOf(
            "com.instagram.android" to 100 * 60 * 1000L // Increased!
        )
        val drop = engine.calculateBiggestDrop(usageThisWeek, usageLastWeek, mapOf("com.instagram.android" to "Instagram"))
        assertNull(drop)
    }

    @Test
    fun testCalculateLongestStrictStreakHours() {
        val now = 10_000_000L
        val sessions = listOf(
            StrictSessionEntity(
                id = 1,
                profileId = 1,
                startTime = now,
                targetEndTime = now + (4 * 3600 * 1000L), // 4h
                isActive = false
            ),
            StrictSessionEntity(
                id = 2,
                profileId = 1,
                startTime = now + 100_000L,
                targetEndTime = now + 100_000L + (24 * 3600 * 1000L), // 24h
                isActive = false
            ),
            StrictSessionEntity(
                id = 3,
                profileId = 1,
                startTime = now + 500_000L,
                targetEndTime = now + 500_000L + (12 * 3600 * 1000L), // 12h
                isActive = true
            )
        )

        val longest = engine.calculateLongestStrictStreakHours(sessions)
        assertEquals(24, longest)
    }

    @Test
    fun testCalculateWeeklyDigestSummaryFormatting() {
        val digest = engine.calculateWeeklyDigest(
            totalBlockedAttempts = 42,
            usageThisWeek = mapOf("com.instagram.android" to 60 * 60 * 1000L),
            usageLastWeek = mapOf("com.instagram.android" to 180 * 60 * 1000L),
            packageToAppName = mapOf("com.instagram.android" to "Instagram"),
            strictSessions = listOf(
                StrictSessionEntity(
                    id = 1,
                    profileId = 1,
                    startTime = 0L,
                    targetEndTime = 18 * 3600 * 1000L,
                    isActive = false
                )
            )
        )

        assertEquals(42, digest.totalBlockedAttempts)
        assertEquals("Instagram", digest.biggestDrop?.appName)
        assertEquals(120, digest.biggestDrop?.dropMinutes)
        assertEquals(18, digest.longestStrictStreakHours)
        assertTrue(digest.formattedSummary.contains("42 distractions blocked"))
        assertTrue(digest.formattedSummary.contains("Instagram (-120m)"))
        assertTrue(digest.formattedSummary.contains("18h Strict Mode"))
    }

    @Test
    fun testCalculateNextSundayNightTimestamp() {
        // Wednesday at 14:00 UTC
        val wednesday = ZonedDateTime.of(2026, 9, 23, 14, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val nextSunday = engine.calculateNextSundayNightTimestamp(wednesday, zoneId)
        val resultZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nextSunday), zoneId)

        assertEquals(DayOfWeek.SUNDAY, resultZdt.dayOfWeek)
        assertEquals(21, resultZdt.hour)
        assertEquals(0, resultZdt.minute)
        assertEquals(27, resultZdt.dayOfMonth) // Sept 27, 2026 is Sunday

        // Sunday before 21:00 UTC (e.g., Sunday 18:00)
        val sundayAfternoon = ZonedDateTime.of(2026, 9, 27, 18, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val sameSunday = engine.calculateNextSundayNightTimestamp(sundayAfternoon, zoneId)
        val sameSundayZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(sameSunday), zoneId)

        assertEquals(27, sameSundayZdt.dayOfMonth)
        assertEquals(21, sameSundayZdt.hour)

        // Sunday after 21:00 UTC (e.g., Sunday 22:00)
        val sundayNight = ZonedDateTime.of(2026, 9, 27, 22, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val nextWeekSunday = engine.calculateNextSundayNightTimestamp(sundayNight, zoneId)
        val nextWeekSundayZdt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nextWeekSunday), zoneId)

        assertEquals(DayOfWeek.SUNDAY, nextWeekSundayZdt.dayOfWeek)
        assertEquals(4, nextWeekSundayZdt.dayOfMonth) // Oct 4, 2026
        assertEquals(21, nextWeekSundayZdt.hour)
    }
}
