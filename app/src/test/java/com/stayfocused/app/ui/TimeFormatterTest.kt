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
    fun `formatTimeAgo formats recent timestamps cleanly`() {
        val now = 1_000_000_000L
        assertEquals("Just now", TimeFormatter.formatTimeAgo(now - 10_000L, now))
        assertEquals("5m ago", TimeFormatter.formatTimeAgo(now - 5 * 60_000L, now))
        assertEquals("2h ago", TimeFormatter.formatTimeAgo(now - 2 * 3600_000L, now))
        assertEquals("1d ago", TimeFormatter.formatTimeAgo(now - 24 * 3600_000L, now))
    }
}
