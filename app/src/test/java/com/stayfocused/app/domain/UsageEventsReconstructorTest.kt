package com.stayfocused.app.domain

import com.stayfocused.app.tracker.UsageEventRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class UsageEventsReconstructorTest {

    private val zoneUtc = ZoneId.of("UTC")
    private val zoneIst = ZoneId.of("Asia/Kolkata")

    @Test
    fun testIntraHourSessionSlicing() {
        val reconstructor = UsageEventsReconstructor(zoneId = zoneUtc)
        // 2026-10-16 14:10:00 to 14:35:00 UTC (25 minutes)
        val start = ZonedDateTime.of(2026, 10, 16, 14, 10, 0, 0, zoneUtc).toInstant().toEpochMilli()
        val end = ZonedDateTime.of(2026, 10, 16, 14, 35, 0, 0, zoneUtc).toInstant().toEpochMilli()

        val slices = reconstructor.sliceSessionAcrossHours("com.google.android.youtube", start, end)

        assertEquals(1, slices.size)
        val slice = slices[0]
        assertEquals(LocalDate.of(2026, 10, 16).toEpochDay(), slice.epochDay)
        assertEquals(14, slice.hourOfDay)
        assertEquals("com.google.android.youtube", slice.packageName)
        assertEquals(25 * 60 * 1000L, slice.durationMs)
    }

    @Test
    fun testMultiHourBoundarySessionSlicing() {
        val reconstructor = UsageEventsReconstructor(zoneId = zoneUtc)
        // 2026-10-16 13:45:00 to 15:10:00 UTC (15m in h13, 60m in h14, 10m in h15)
        val start = ZonedDateTime.of(2026, 10, 16, 13, 45, 0, 0, zoneUtc).toInstant().toEpochMilli()
        val end = ZonedDateTime.of(2026, 10, 16, 15, 10, 0, 0, zoneUtc).toInstant().toEpochMilli()

        val slices = reconstructor.sliceSessionAcrossHours("com.instagram.android", start, end)

        assertEquals(3, slices.size)
        val expectedEpochDay = LocalDate.of(2026, 10, 16).toEpochDay()

        assertEquals(expectedEpochDay, slices[0].epochDay)
        assertEquals(13, slices[0].hourOfDay)
        assertEquals(15 * 60 * 1000L, slices[0].durationMs)

        assertEquals(expectedEpochDay, slices[1].epochDay)
        assertEquals(14, slices[1].hourOfDay)
        assertEquals(60 * 60 * 1000L, slices[1].durationMs)

        assertEquals(expectedEpochDay, slices[2].epochDay)
        assertEquals(15, slices[2].hourOfDay)
        assertEquals(10 * 60 * 1000L, slices[2].durationMs)

        assertEquals(85 * 60 * 1000L, slices.sumOf { it.durationMs })
    }

    @Test
    fun testMidnightRolloverSlicing() {
        val reconstructor = UsageEventsReconstructor(zoneId = zoneUtc)
        // 2026-10-16 23:50:00 to 2026-10-17 00:30:00 UTC (10m on Day D, 30m on Day D+1)
        val start = ZonedDateTime.of(2026, 10, 16, 23, 50, 0, 0, zoneUtc).toInstant().toEpochMilli()
        val end = ZonedDateTime.of(2026, 10, 17, 0, 30, 0, 0, zoneUtc).toInstant().toEpochMilli()

        val slices = reconstructor.sliceSessionAcrossHours("com.reddit.frontpage", start, end)

        assertEquals(2, slices.size)
        val dayD = LocalDate.of(2026, 10, 16).toEpochDay()
        val dayD1 = LocalDate.of(2026, 10, 17).toEpochDay()

        assertEquals(dayD, slices[0].epochDay)
        assertEquals(23, slices[0].hourOfDay)
        assertEquals(10 * 60 * 1000L, slices[0].durationMs)

        assertEquals(dayD1, slices[1].epochDay)
        assertEquals(0, slices[1].hourOfDay)
        assertEquals(30 * 60 * 1000L, slices[1].durationMs)

        assertEquals(dayD + 1, dayD1)
    }

    @Test
    fun testIndiaStandardTimeSubHourOffsetSlicing() {
        val reconstructor = UsageEventsReconstructor(zoneId = zoneIst)
        // 2026-10-16 14:15:00 IST to 15:45:00 IST (45m in h14, 45m in h15)
        val start = ZonedDateTime.of(2026, 10, 16, 14, 15, 0, 0, zoneIst).toInstant().toEpochMilli()
        val end = ZonedDateTime.of(2026, 10, 16, 15, 45, 0, 0, zoneIst).toInstant().toEpochMilli()

        val slices = reconstructor.sliceSessionAcrossHours("com.whatsapp", start, end)

        assertEquals(2, slices.size)
        val epochDay = LocalDate.of(2026, 10, 16).toEpochDay()

        assertEquals(epochDay, slices[0].epochDay)
        assertEquals(14, slices[0].hourOfDay)
        assertEquals(45 * 60 * 1000L, slices[0].durationMs)

        assertEquals(epochDay, slices[1].epochDay)
        assertEquals(15, slices[1].hourOfDay)
        assertEquals(45 * 60 * 1000L, slices[1].durationMs)
    }

    @Test
    fun testScreenOffClampingNoGhostUsage() {
        val reconstructor = UsageEventsReconstructor(zoneId = zoneUtc)
        val t0 = ZonedDateTime.of(2026, 10, 16, 14, 0, 0, 0, zoneUtc).toInstant().toEpochMilli()
        val tScreenOff = t0 + 15 * 60 * 1000L // 14:15
        val tNextWake = t0 + 120 * 60 * 1000L // 16:00 (2 hours later)

        val events = sequenceOf(
            UsageEventRecord("com.google.android.youtube", null, UsageEventTypes.ACTIVITY_RESUMED, t0),
            UsageEventRecord(null, null, UsageEventTypes.SCREEN_NON_INTERACTIVE, tScreenOff),
            UsageEventRecord(null, null, UsageEventTypes.SCREEN_INTERACTIVE, tNextWake),
            UsageEventRecord(null, null, UsageEventTypes.KEYGUARD_HIDDEN, tNextWake + 1000),
            UsageEventRecord("com.android.chrome", null, UsageEventTypes.ACTIVITY_RESUMED, tNextWake + 2000),
            UsageEventRecord("com.android.chrome", null, UsageEventTypes.ACTIVITY_PAUSED, tNextWake + 12_000)
        )

        val result = reconstructor.reconstruct(events, t0, tNextWake + 30_000)

        // YouTube should only have 15 minutes in hour 14, NOT 2 hours!
        val ytSlice = result.appSlices.find { it.packageName == "com.google.android.youtube" && it.hourOfDay == 14 }
        assertEquals(15 * 60 * 1000L, ytSlice?.durationMs)

        // Chrome has 10 seconds in hour 16
        val chromeSlice = result.appSlices.find { it.packageName == "com.android.chrome" && it.hourOfDay == 16 }
        assertEquals(10_000L, chromeSlice?.durationMs)
    }

    @Test
    fun testSafetyCapClampingAt2HoursForUnclosedSession() {
        val reconstructor = UsageEventsReconstructor(zoneId = zoneUtc)
        val start = ZonedDateTime.of(2026, 10, 16, 10, 0, 0, 0, zoneUtc).toInstant().toEpochMilli()
        // Simulated unclosed session spanning 5 hours without pause event before next app
        val tNextApp = start + 5 * 3600 * 1000L

        val events = sequenceOf(
            UsageEventRecord("com.gaming.app", null, UsageEventTypes.ACTIVITY_RESUMED, start),
            UsageEventRecord("com.other.app", null, UsageEventTypes.ACTIVITY_RESUMED, tNextApp),
            UsageEventRecord("com.other.app", null, UsageEventTypes.ACTIVITY_PAUSED, tNextApp + 60_000)
        )

        val result = reconstructor.reconstruct(events, start, tNextApp + 60_000)

        // Gaming app had no pause event for 5 hours; clamped to 2 hours max
        val gamingSlices = result.appSlices.filter { it.packageName == "com.gaming.app" }
        val totalGamingMs = gamingSlices.sumOf { it.durationMs }
        assertEquals(2 * 3600 * 1000L, totalGamingMs)
    }

    @Test
    fun testSecureDevicePickupsStrictKeyguardHidden() {
        val reconstructor = UsageEventsReconstructor(zoneId = zoneUtc, isKeyguardSecure = true)
        val t0 = ZonedDateTime.of(2026, 10, 16, 9, 0, 0, 0, zoneUtc).toInstant().toEpochMilli()

        val events = sequenceOf(
            // Interaction 1: Glance only (screen on then screen off without keyguard hidden)
            UsageEventRecord(null, null, UsageEventTypes.SCREEN_INTERACTIVE, t0 + 10_000),
            UsageEventRecord(null, null, UsageEventTypes.SCREEN_NON_INTERACTIVE, t0 + 20_000),

            // Interaction 2: Actual unlock
            UsageEventRecord(null, null, UsageEventTypes.SCREEN_INTERACTIVE, t0 + 40_000),
            UsageEventRecord(null, null, UsageEventTypes.KEYGUARD_HIDDEN, t0 + 42_000),
            UsageEventRecord("com.whatsapp", null, UsageEventTypes.ACTIVITY_RESUMED, t0 + 43_000),
            UsageEventRecord("com.whatsapp", null, UsageEventTypes.ACTIVITY_PAUSED, t0 + 60_000),
            UsageEventRecord(null, null, UsageEventTypes.SCREEN_NON_INTERACTIVE, t0 + 65_000),

            // Interaction 3: Actual unlock in hour 10
            UsageEventRecord(null, null, UsageEventTypes.SCREEN_INTERACTIVE, t0 + 3600_000 + 10_000),
            UsageEventRecord(null, null, UsageEventTypes.KEYGUARD_HIDDEN, t0 + 3600_000 + 12_000),
            UsageEventRecord(null, null, UsageEventTypes.SCREEN_NON_INTERACTIVE, t0 + 3600_000 + 30_000)
        )

        val result = reconstructor.reconstruct(events, t0, t0 + 7200_000)

        val h9 = result.deviceMetrics.find { it.hourOfDay == 9 }
        val h10 = result.deviceMetrics.find { it.hourOfDay == 10 }

        // Hour 9 had 1 glance and 1 unlock -> exactly 1 pickup
        assertEquals(1, h9?.pickupCount)
        // Hour 10 had 1 unlock -> exactly 1 pickup
        assertEquals(1, h10?.pickupCount)
    }

    @Test
    fun testIncomingCallOrAlarmDoesNotTriggerPickup() {
        val reconstructor = UsageEventsReconstructor(zoneId = zoneUtc, isKeyguardSecure = true)
        val t0 = ZonedDateTime.of(2026, 10, 16, 8, 0, 0, 0, zoneUtc).toInstant().toEpochMilli()

        val events = sequenceOf(
            // Incoming phone call rings over keyguard: screen turns on, dialer resumes, screen turns off
            UsageEventRecord(null, null, UsageEventTypes.SCREEN_INTERACTIVE, t0 + 5000),
            UsageEventRecord("com.google.android.dialer", null, UsageEventTypes.ACTIVITY_RESUMED, t0 + 6000),
            UsageEventRecord("com.google.android.dialer", null, UsageEventTypes.ACTIVITY_PAUSED, t0 + 25_000),
            UsageEventRecord(null, null, UsageEventTypes.SCREEN_NON_INTERACTIVE, t0 + 30_000)
        )

        val result = reconstructor.reconstruct(events, t0, t0 + 60_000)

        val h8 = result.deviceMetrics.find { it.hourOfDay == 8 }
        // Keyguard was never hidden -> 0 pickups!
        assertEquals(0, h8?.pickupCount ?: 0)
    }

    @Test
    fun testBiometricUnlockSimultaneousEvents() {
        val reconstructor = UsageEventsReconstructor(zoneId = zoneUtc, isKeyguardSecure = true)
        val t0 = ZonedDateTime.of(2026, 10, 16, 11, 0, 0, 0, zoneUtc).toInstant().toEpochMilli()

        val events = sequenceOf(
            // Fingerprint unlock on Realme UI: KEYGUARD_HIDDEN followed immediately by SCREEN_INTERACTIVE
            UsageEventRecord(null, null, UsageEventTypes.KEYGUARD_HIDDEN, t0 + 100),
            UsageEventRecord(null, null, UsageEventTypes.SCREEN_INTERACTIVE, t0 + 120),
            UsageEventRecord("com.android.launcher", null, UsageEventTypes.ACTIVITY_RESUMED, t0 + 200),
            UsageEventRecord(null, null, UsageEventTypes.SCREEN_NON_INTERACTIVE, t0 + 10_000)
        )

        val result = reconstructor.reconstruct(events, t0, t0 + 20_000)

        val h11 = result.deviceMetrics.find { it.hourOfDay == 11 }
        assertEquals(1, h11?.pickupCount)
    }

    @Test
    fun testInsecureDevicePickupsWithDebounce() {
        val reconstructor = UsageEventsReconstructor(zoneId = zoneUtc, isKeyguardSecure = false)
        val t0 = ZonedDateTime.of(2026, 10, 16, 12, 0, 0, 0, zoneUtc).toInstant().toEpochMilli()

        val events = sequenceOf(
            // Unlock 1
            UsageEventRecord(null, null, UsageEventTypes.SCREEN_INTERACTIVE, t0 + 5000),
            UsageEventRecord("com.instagram.android", null, UsageEventTypes.ACTIVITY_RESUMED, t0 + 6000),
            UsageEventRecord(null, null, UsageEventTypes.SCREEN_NON_INTERACTIVE, t0 + 15_000),

            // Attempted rapid wake within 10s (should be debounced)
            UsageEventRecord(null, null, UsageEventTypes.SCREEN_INTERACTIVE, t0 + 20_000),
            UsageEventRecord("com.instagram.android", null, UsageEventTypes.ACTIVITY_RESUMED, t0 + 21_000),
            UsageEventRecord(null, null, UsageEventTypes.SCREEN_NON_INTERACTIVE, t0 + 25_000),

            // Unlock 2 after debounce (> 30s)
            UsageEventRecord(null, null, UsageEventTypes.SCREEN_INTERACTIVE, t0 + 60_000),
            UsageEventRecord("com.instagram.android", null, UsageEventTypes.ACTIVITY_RESUMED, t0 + 61_000),
            UsageEventRecord(null, null, UsageEventTypes.SCREEN_NON_INTERACTIVE, t0 + 70_000)
        )

        val result = reconstructor.reconstruct(events, t0, t0 + 100_000)

        val h12 = result.deviceMetrics.find { it.hourOfDay == 12 }
        assertEquals(2, h12?.pickupCount)
    }
}
