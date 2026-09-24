package com.stayfocused.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageDialHelperTest {

    @Test
    fun `calculateProgress returns correct percentage ratio`() {
        assertEquals(0.5f, UsageDialHelper.calculateProgress(usedMinutes = 30, targetMinutes = 60), 0.001f)
        assertEquals(1.0f, UsageDialHelper.calculateProgress(usedMinutes = 60, targetMinutes = 60), 0.001f)
        assertEquals(1.0f, UsageDialHelper.calculateProgress(usedMinutes = 90, targetMinutes = 60), 0.001f) // capped at 1.0
        assertEquals(0.0f, UsageDialHelper.calculateProgress(usedMinutes = 0, targetMinutes = 60), 0.001f)
    }

    @Test
    fun `calculateProgress handles zero target safely`() {
        assertEquals(0.0f, UsageDialHelper.calculateProgress(usedMinutes = 10, targetMinutes = 0), 0.001f)
    }

    @Test
    fun `formatDuration returns human readable string`() {
        assertEquals("0m", UsageDialHelper.formatDuration(0))
        assertEquals("45m", UsageDialHelper.formatDuration(45))
        assertEquals("1h 0m", UsageDialHelper.formatDuration(60))
        assertEquals("2h 15m", UsageDialHelper.formatDuration(135))
    }

    @Test
    fun `getProgressColorCategory returns safe thresholds`() {
        assertEquals(ProgressTier.NORMAL, UsageDialHelper.getProgressTier(0.5f))
        assertEquals(ProgressTier.WARNING, UsageDialHelper.getProgressTier(0.75f))
        assertEquals(ProgressTier.CRITICAL, UsageDialHelper.getProgressTier(0.95f))
    }
}
