package com.stayfocused.app.domain

import com.stayfocused.app.ui.ProgressTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FocusWidgetDataEngineTest {

    private lateinit var engine: FocusWidgetDataEngine

    @Before
    fun setUp() {
        engine = FocusWidgetDataEngine(defaultTargetMinutes = 120)
    }

    @Test
    fun computeWidgetData_zeroUsage_returnsFullRemainingAndNormalTier() {
        val result = engine.computeWidgetData(usedMinutes = 0, targetMinutes = 120)

        assertEquals(0, result.usedMinutes)
        assertEquals(120, result.targetMinutes)
        assertEquals(120, result.remainingMinutes)
        assertEquals(0.0f, result.progress, 0.001f)
        assertEquals(ProgressTier.NORMAL, result.tier)
        assertEquals("2h 0m left", result.remainingFormatted)
        assertEquals("0m", result.usedFormatted)
        assertEquals("2h 0m", result.targetFormatted)
        assertFalse(result.isTargetExceeded)
    }

    @Test
    fun computeWidgetData_partialUsage_normalTier() {
        val result = engine.computeWidgetData(usedMinutes = 45, targetMinutes = 120)

        assertEquals(45, result.usedMinutes)
        assertEquals(120, result.targetMinutes)
        assertEquals(75, result.remainingMinutes)
        assertEquals(45f / 120f, result.progress, 0.001f)
        assertEquals(ProgressTier.NORMAL, result.tier)
        assertEquals("1h 15m left", result.remainingFormatted)
        assertEquals("45m", result.usedFormatted)
        assertFalse(result.isTargetExceeded)
    }

    @Test
    fun computeWidgetData_warningTier() {
        val result = engine.computeWidgetData(usedMinutes = 90, targetMinutes = 120)

        assertEquals(90, result.usedMinutes)
        assertEquals(30, result.remainingMinutes)
        assertEquals(0.75f, result.progress, 0.001f)
        assertEquals(ProgressTier.WARNING, result.tier)
        assertEquals("30m left", result.remainingFormatted)
        assertEquals("1h 30m", result.usedFormatted)
        assertFalse(result.isTargetExceeded)
    }

    @Test
    fun computeWidgetData_criticalTier() {
        val result = engine.computeWidgetData(usedMinutes = 110, targetMinutes = 120)

        assertEquals(110, result.usedMinutes)
        assertEquals(10, result.remainingMinutes)
        assertEquals(110f / 120f, result.progress, 0.001f)
        assertEquals(ProgressTier.CRITICAL, result.tier)
        assertEquals("10m left", result.remainingFormatted)
        assertFalse(result.isTargetExceeded)
    }

    @Test
    fun computeWidgetData_exactBudgetReached() {
        val result = engine.computeWidgetData(usedMinutes = 120, targetMinutes = 120)

        assertEquals(120, result.usedMinutes)
        assertEquals(0, result.remainingMinutes)
        assertEquals(1.0f, result.progress, 0.001f)
        assertEquals(ProgressTier.CRITICAL, result.tier)
        assertEquals("0m left", result.remainingFormatted)
        assertFalse(result.isTargetExceeded)
    }

    @Test
    fun computeWidgetData_exceededBudget() {
        val result = engine.computeWidgetData(usedMinutes = 160, targetMinutes = 120)

        assertEquals(160, result.usedMinutes)
        assertEquals(0, result.remainingMinutes)
        assertEquals(1.0f, result.progress, 0.001f)
        assertEquals(ProgressTier.CRITICAL, result.tier)
        assertEquals("0m left (Exceeded)", result.remainingFormatted)
        assertTrue(result.isTargetExceeded)
    }

    @Test
    fun computeWidgetData_zeroOrNegativeTarget_fallsBackToDefault() {
        val resultZero = engine.computeWidgetData(usedMinutes = 30, targetMinutes = 0)
        assertEquals(120, resultZero.targetMinutes)
        assertEquals(90, resultZero.remainingMinutes)

        val resultNegative = engine.computeWidgetData(usedMinutes = 30, targetMinutes = -50)
        assertEquals(120, resultNegative.targetMinutes)
        assertEquals(90, resultNegative.remainingMinutes)
    }

    @Test
    fun computeWidgetData_negativeUsage_clampedToZero() {
        val result = engine.computeWidgetData(usedMinutes = -20, targetMinutes = 120)
        assertEquals(0, result.usedMinutes)
        assertEquals(120, result.remainingMinutes)
        assertEquals(0.0f, result.progress, 0.001f)
    }
}
