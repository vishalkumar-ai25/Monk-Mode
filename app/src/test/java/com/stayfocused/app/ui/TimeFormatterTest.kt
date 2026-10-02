package com.stayfocused.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatterTest {

    @Test
    fun `formatRemainingTime formats mm ss correctly`() {
        assertEquals("05:00", TimeFormatter.formatRemainingTime(5 * 60 * 1000L))
        assertEquals("00:45", TimeFormatter.formatRemainingTime(45 * 1000L))
        assertEquals("10:15", TimeFormatter.formatRemainingTime(615 * 1000L))
        assertEquals("00:00", TimeFormatter.formatRemainingTime(0L))
        assertEquals("00:00", TimeFormatter.formatRemainingTime(-1000L))
    }

    @Test
    fun `formatRemainingTime formats hours and days when large`() {
        // Less than 1 hour remains mm:ss
        assertEquals("05:00", TimeFormatter.formatRemainingTime(5 * 60 * 1000L))
        assertEquals("59:59", TimeFormatter.formatRemainingTime((59 * 60 + 59) * 1000L))
        
        // 1 hour to 24 hours
        assertEquals("01:00:00", TimeFormatter.formatRemainingTime(3600 * 1000L))
        assertEquals("02:15:30", TimeFormatter.formatRemainingTime((2 * 3600 + 15 * 60 + 30) * 1000L))

        // 1 day or more
        assertEquals("1d 00h 00m", TimeFormatter.formatRemainingTime(24 * 3600 * 1000L))
        assertEquals("7d 12h 30m", TimeFormatter.formatRemainingTime((7 * 24 * 3600 + 12 * 3600 + 30 * 60) * 1000L))
        assertEquals("90d 00h 00m", TimeFormatter.formatRemainingTime(90L * 24 * 3600 * 1000L))
    }

    @Test
    fun `formatExactDateTime formats epoch cleanly`() {
        val zone = java.time.ZoneId.of("UTC")
        // 2026-10-03 02:21:00 UTC
        val zdt = java.time.ZonedDateTime.of(2026, 10, 3, 2, 21, 0, 0, zone)
        val epochMs = zdt.toInstant().toEpochMilli()
        val formatted = TimeFormatter.formatExactDateTime(epochMs, zone)
        assertEquals("Sat, Oct 3, 02:21 AM", formatted)
    }

    @Test
    fun `formatUsageDuration formats human readable hours and minutes`() {
        assertEquals("02 hrs 05 mins", TimeFormatter.formatUsageDuration((2 * 3600 + 5 * 60) * 1000L))
        assertEquals("04 mins 50 sec", TimeFormatter.formatUsageDuration((4 * 60 + 50) * 1000L))
        assertEquals("00 mins 45 sec", TimeFormatter.formatUsageDuration(45 * 1000L))
        assertEquals("01 hr 00 mins", TimeFormatter.formatUsageDuration(3600 * 1000L))
    }
}
