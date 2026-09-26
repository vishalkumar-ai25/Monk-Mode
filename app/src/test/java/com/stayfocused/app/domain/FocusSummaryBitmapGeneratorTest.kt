package com.stayfocused.app.domain

import android.graphics.Bitmap
import com.stayfocused.app.domain.model.DailyFocusSummaryData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FocusSummaryBitmapGeneratorTest {

    private lateinit var generator: FocusSummaryBitmapGenerator

    @Before
    fun setUp() {
        generator = FocusSummaryBitmapGenerator()
    }

    @Test
    fun testFocusScoreCalculation() {
        val normal = DailyFocusSummaryData(
            dateText = "Sep 26, 2026",
            usedMinutes = 30,
            targetMinutes = 120,
            activeLimitsCount = 5,
            blockedDistractionsCount = 12
        )
        // (120 - 30) / 120 = 90 / 120 = 75%
        assertEquals(75, normal.focusScorePercentage)
        assertFalse(normal.isOverBudget)

        val overBudget = DailyFocusSummaryData(
            dateText = "Sep 26, 2026",
            usedMinutes = 150,
            targetMinutes = 120,
            activeLimitsCount = 5,
            blockedDistractionsCount = 12
        )
        assertEquals(0, overBudget.focusScorePercentage)
        assertTrue(overBudget.isOverBudget)
    }

    @Test
    fun testGenerateSummaryBitmapProducesValidDimensions() {
        val data = DailyFocusSummaryData(
            dateText = "Sep 26, 2026",
            usedMinutes = 45,
            targetMinutes = 120,
            activeLimitsCount = 6,
            blockedDistractionsCount = 18,
            activeProfileName = "Deep Work"
        )

        val bitmap = generator.generateSummaryBitmap(data)

        assertNotNull(bitmap)
        assertEquals(1080, bitmap.width)
        assertEquals(1350, bitmap.height)
        assertEquals(Bitmap.Config.ARGB_8888, bitmap.config)
    }

    @Test
    fun testGenerateSummaryBitmapDrawsContent() {
        val data = DailyFocusSummaryData(
            dateText = "Sep 26, 2026",
            usedMinutes = 45,
            targetMinutes = 120,
            activeLimitsCount = 4,
            blockedDistractionsCount = 8,
            activeProfileName = null
        )

        val bitmap = generator.generateSummaryBitmap(data)
        assertNotNull(bitmap)

        val stream = java.io.ByteArrayOutputStream()
        val compressed = bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        assertTrue(compressed)
        assertTrue(stream.size() > 0)
    }
}
