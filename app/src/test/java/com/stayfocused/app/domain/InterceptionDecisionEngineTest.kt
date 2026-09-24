package com.stayfocused.app.domain

import com.stayfocused.app.domain.model.AppLimitSnapshot
import com.stayfocused.app.domain.model.BlockReason
import com.stayfocused.app.domain.model.FocusProfileRule
import com.stayfocused.app.domain.model.InterceptionContext
import com.stayfocused.app.domain.model.InterceptionResult
import com.stayfocused.app.domain.model.LimitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class InterceptionDecisionEngineTest {

    private lateinit var engine: InterceptionDecisionEngine
    private val zoneId = ZoneId.of("UTC")

    @Before
    fun setUp() {
        engine = InterceptionDecisionEngine()
    }

    private fun createTimestamp(dayOfWeek: DayOfWeek, hour: Int, minute: Int): Long {
        val date = ZonedDateTime.of(2026, 9, 21, hour, minute, 0, 0, zoneId) // 2026-09-21 is Monday
        val offsetDays = (dayOfWeek.value - DayOfWeek.MONDAY.value).toLong()
        return date.plusDays(offsetDays).toInstant().toEpochMilli()
    }

    @Test
    fun testSelfAppAlwaysAllowed() {
        val context = InterceptionContext(
            targetPackageName = "com.stayfocused.app",
            currentTimeMillis = System.currentTimeMillis(),
            isStrictModeActive = true,
            antiTamperEnabled = true,
            isSettingsOrInstaller = false,
            isGracePeriodActive = false
        )
        val result = engine.evaluate(context)
        assertTrue("Self app must always be allowed", result is InterceptionResult.Allow)
    }

    @Test
    fun testSettingsAllowedWhenAntiTamperDisabled() {
        val context = InterceptionContext(
            targetPackageName = "com.android.settings",
            currentTimeMillis = System.currentTimeMillis(),
            isStrictModeActive = true,
            antiTamperEnabled = false, // debug build
            isSettingsOrInstaller = true,
            isGracePeriodActive = false
        )
        val result = engine.evaluate(context)
        assertTrue("Settings must be allowed in debug build", result is InterceptionResult.Allow)
    }

    @Test
    fun testSettingsBlockedInStrictModeWhenAntiTamperEnabled() {
        val context = InterceptionContext(
            targetPackageName = "com.android.settings",
            currentTimeMillis = System.currentTimeMillis(),
            isStrictModeActive = true,
            antiTamperEnabled = true, // release build
            isSettingsOrInstaller = true,
            isGracePeriodActive = false
        )
        val result = engine.evaluate(context)
        assertTrue("Settings must be blocked during Strict Mode in release", result is InterceptionResult.Block)
        val block = result as InterceptionResult.Block
        assertTrue(block.reason is BlockReason.SettingsTamper)
    }

    @Test
    fun testSettingsAllowedDuringBootGracePeriod() {
        val context = InterceptionContext(
            targetPackageName = "com.android.settings",
            currentTimeMillis = System.currentTimeMillis(),
            isStrictModeActive = true,
            antiTamperEnabled = true,
            isSettingsOrInstaller = true,
            isGracePeriodActive = true // 5-minute reboot window
        )
        val result = engine.evaluate(context)
        assertTrue("Settings must be allowed during boot grace period to fix crashes", result is InterceptionResult.Allow)
    }

    @Test
    fun testAppBlocksRemainArmedDuringBootGracePeriod() {
        // Spec rule: A reboot must NEVER provide a loophole for unrestricted access to blocked apps
        val appLimit = AppLimitSnapshot(
            packageName = "com.instagram.android",
            appName = "Instagram",
            dailyTimeLimitMinutes = 30,
            dailyLaunchLimit = 0,
            currentDayUsageMs = 30 * 60 * 1000L, // limit exhausted
            currentDayLaunches = 5,
            isBlocked = false
        )
        val context = InterceptionContext(
            targetPackageName = "com.instagram.android",
            currentTimeMillis = System.currentTimeMillis(),
            appLimit = appLimit,
            isStrictModeActive = true,
            antiTamperEnabled = true,
            isSettingsOrInstaller = false,
            isGracePeriodActive = true // Grace period active!
        )
        val result = engine.evaluate(context)
        assertTrue("App blocks must remain armed during boot grace period", result is InterceptionResult.Block)
        val block = result as InterceptionResult.Block
        assertTrue(block.reason is BlockReason.LimitReached)
        assertEquals(LimitType.TIME_LIMIT, (block.reason as BlockReason.LimitReached).limitType)
    }

    @Test
    fun testAppBlockedWhenManuallyBlocked() {
        val appLimit = AppLimitSnapshot(
            packageName = "com.twitter.android",
            appName = "X",
            isBlocked = true
        )
        val context = InterceptionContext(
            targetPackageName = "com.twitter.android",
            currentTimeMillis = System.currentTimeMillis(),
            appLimit = appLimit
        )
        val result = engine.evaluate(context)
        assertTrue(result is InterceptionResult.Block)
        val block = result as InterceptionResult.Block
        assertTrue(block.reason is BlockReason.ManuallyBlocked)
    }

    @Test
    fun testAppBlockedWhenTimeLimitReached() {
        val appLimit = AppLimitSnapshot(
            packageName = "com.reddit.frontpage",
            appName = "Reddit",
            dailyTimeLimitMinutes = 15,
            currentDayUsageMs = 15 * 60 * 1000L // 15 mins used
        )
        val context = InterceptionContext(
            targetPackageName = "com.reddit.frontpage",
            currentTimeMillis = System.currentTimeMillis(),
            appLimit = appLimit
        )
        val result = engine.evaluate(context)
        assertTrue(result is InterceptionResult.Block)
        val block = result as InterceptionResult.Block
        assertTrue(block.reason is BlockReason.LimitReached)
        assertEquals(LimitType.TIME_LIMIT, (block.reason as BlockReason.LimitReached).limitType)
    }

    @Test
    fun testAppAllowedWhenTimeLimitNotReached() {
        val appLimit = AppLimitSnapshot(
            packageName = "com.reddit.frontpage",
            appName = "Reddit",
            dailyTimeLimitMinutes = 15,
            currentDayUsageMs = 10 * 60 * 1000L // 10 mins used
        )
        val context = InterceptionContext(
            targetPackageName = "com.reddit.frontpage",
            currentTimeMillis = System.currentTimeMillis(),
            appLimit = appLimit
        )
        val result = engine.evaluate(context)
        assertTrue(result is InterceptionResult.Allow)
    }

    @Test
    fun testAppBlockedWhenLaunchLimitReached() {
        val appLimit = AppLimitSnapshot(
            packageName = "com.zhiliaoapp.musically",
            appName = "TikTok",
            dailyLaunchLimit = 5,
            currentDayLaunches = 5
        )
        val context = InterceptionContext(
            targetPackageName = "com.zhiliaoapp.musically",
            currentTimeMillis = System.currentTimeMillis(),
            appLimit = appLimit
        )
        val result = engine.evaluate(context)
        assertTrue(result is InterceptionResult.Block)
        val block = result as InterceptionResult.Block
        assertTrue(block.reason is BlockReason.LimitReached)
        assertEquals(LimitType.LAUNCH_LIMIT, (block.reason as BlockReason.LimitReached).limitType)
    }

    @Test
    fun testAppBlockedWhenFocusProfileActive() {
        val profile = FocusProfileRule(
            profileId = 10L,
            name = "Deep Work",
            isActive = true,
            isStrictMode = false,
            blockedPackages = setOf("com.google.android.youtube")
        )
        val context = InterceptionContext(
            targetPackageName = "com.google.android.youtube",
            currentTimeMillis = System.currentTimeMillis(),
            activeProfiles = listOf(profile)
        )
        val result = engine.evaluate(context)
        assertTrue(result is InterceptionResult.Block)
        val block = result as InterceptionResult.Block
        assertTrue(block.reason is BlockReason.ProfileActive)
        assertEquals("Deep Work", (block.reason as BlockReason.ProfileActive).profileName)
    }

    @Test
    fun testScheduledProfileDayAndTimeWindow() {
        // Monday (bit 0 = 1), Tuesday (bit 1 = 2) -> mask = 3
        val profile = FocusProfileRule(
            profileId = 20L,
            name = "Morning Focus",
            isActive = true,
            scheduleStartTime = "09:00",
            scheduleEndTime = "12:00",
            activeDaysMask = 3, // Mon & Tue
            blockedPackages = setOf("com.netflix.mediaclient")
        )

        // Monday 10:30 AM -> inside window
        val mondayActiveTime = createTimestamp(DayOfWeek.MONDAY, 10, 30)
        val result1 = engine.evaluate(
            InterceptionContext(
                targetPackageName = "com.netflix.mediaclient",
                currentTimeMillis = mondayActiveTime,
                activeProfiles = listOf(profile),
                zoneId = zoneId
            )
        )
        assertTrue("Should be blocked on Monday 10:30", result1 is InterceptionResult.Block)

        // Monday 1:00 PM -> outside window
        val mondayInactiveTime = createTimestamp(DayOfWeek.MONDAY, 13, 0)
        val result2 = engine.evaluate(
            InterceptionContext(
                targetPackageName = "com.netflix.mediaclient",
                currentTimeMillis = mondayInactiveTime,
                activeProfiles = listOf(profile),
                zoneId = zoneId
            )
        )
        assertTrue("Should be allowed on Monday 13:00", result2 is InterceptionResult.Allow)

        // Wednesday 10:30 AM -> not an active day
        val wednesdayTime = createTimestamp(DayOfWeek.WEDNESDAY, 10, 30)
        val result3 = engine.evaluate(
            InterceptionContext(
                targetPackageName = "com.netflix.mediaclient",
                currentTimeMillis = wednesdayTime,
                activeProfiles = listOf(profile),
                zoneId = zoneId
            )
        )
        assertTrue("Should be allowed on Wednesday (not in mask)", result3 is InterceptionResult.Allow)
    }

    @Test
    fun testOvernightScheduleWindow() {
        // Schedule from 22:00 to 06:00 every day (mask = 127)
        val profile = FocusProfileRule(
            profileId = 30L,
            name = "Sleep Mode",
            isActive = true,
            scheduleStartTime = "22:00",
            scheduleEndTime = "06:00",
            activeDaysMask = 127, // All 7 days
            blockedPackages = setOf("com.facebook.katana")
        )

        // 23:30 (before midnight)
        val lateNight = createTimestamp(DayOfWeek.MONDAY, 23, 30)
        val result1 = engine.evaluate(
            InterceptionContext(
                targetPackageName = "com.facebook.katana",
                currentTimeMillis = lateNight,
                activeProfiles = listOf(profile),
                zoneId = zoneId
            )
        )
        assertTrue("Should be blocked at 23:30", result1 is InterceptionResult.Block)

        // 03:00 (after midnight, before 06:00)
        val earlyMorning = createTimestamp(DayOfWeek.TUESDAY, 3, 0)
        val result2 = engine.evaluate(
            InterceptionContext(
                targetPackageName = "com.facebook.katana",
                currentTimeMillis = earlyMorning,
                activeProfiles = listOf(profile),
                zoneId = zoneId
            )
        )
        assertTrue("Should be blocked at 03:00", result2 is InterceptionResult.Block)

        // 14:00 (afternoon)
        val afternoon = createTimestamp(DayOfWeek.TUESDAY, 14, 0)
        val result3 = engine.evaluate(
            InterceptionContext(
                targetPackageName = "com.facebook.katana",
                currentTimeMillis = afternoon,
                activeProfiles = listOf(profile),
                zoneId = zoneId
            )
        )
        assertTrue("Should be allowed at 14:00", result3 is InterceptionResult.Allow)
    }

    @Test
    fun testBreakSessionAllowsNormallyBlockedApp() {
        val appLimit = AppLimitSnapshot(
            packageName = "com.instagram.android",
            appName = "Instagram",
            isBlocked = true
        )
        val context = InterceptionContext(
            targetPackageName = "com.instagram.android",
            currentTimeMillis = System.currentTimeMillis(),
            appLimit = appLimit,
            isBreakActive = true, // Break active!
            isStrictModeActive = false
        )
        val result = engine.evaluate(context)
        assertTrue("Break session must allow blocked app", result is InterceptionResult.Allow)
    }

    @Test
    fun testBreakSessionIgnoredWhenStrictModeActive() {
        val appLimit = AppLimitSnapshot(
            packageName = "com.instagram.android",
            appName = "Instagram",
            isBlocked = true
        )
        val context = InterceptionContext(
            targetPackageName = "com.instagram.android",
            currentTimeMillis = System.currentTimeMillis(),
            appLimit = appLimit,
            isBreakActive = true, // Break active!
            isStrictModeActive = true // But strict mode is active!
        )
        val result = engine.evaluate(context)
        assertTrue("Break must be ignored when strict mode is active", result is InterceptionResult.Block)
    }
}
