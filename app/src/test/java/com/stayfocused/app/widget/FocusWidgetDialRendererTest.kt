package com.stayfocused.app.widget

import android.graphics.Bitmap
import com.stayfocused.app.domain.model.FocusWidgetData
import com.stayfocused.app.ui.ProgressTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class FocusWidgetDialRendererTest {

    private lateinit var renderer: FocusWidgetDialRenderer

    @Before
    fun setUp() {
        renderer = FocusWidgetDialRenderer()
    }

    @Test
    fun renderDialBitmap_returnsValidBitmapWithCorrectDimensions() {
        val bitmap = renderer.renderDialBitmap(
            progress = 0.5f,
            tier = ProgressTier.NORMAL,
            sizePx = 200,
            strokeWidthPx = 20f
        )

        assertNotNull(bitmap)
        assertEquals(200, bitmap.width)
        assertEquals(200, bitmap.height)
        assertEquals(Bitmap.Config.ARGB_8888, bitmap.config)

        val stream = ByteArrayOutputStream()
        val success = bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        assertTrue(success)
        assertTrue(stream.size() > 0)
    }

    @Test
    fun renderDialBitmap_differentTiers_produceValidBitmaps() {
        for (tier in ProgressTier.values()) {
            val bitmap = renderer.renderDialBitmap(
                progress = 0.8f,
                tier = tier,
                sizePx = 150
            )
            assertNotNull(bitmap)
            assertEquals(150, bitmap.width)
            assertEquals(150, bitmap.height)
        }
    }

    @Test
    fun renderDialBitmap_clampsNegativeAndExcessiveProgress() {
        val bitmapNegative = renderer.renderDialBitmap(
            progress = -0.5f,
            tier = ProgressTier.NORMAL
        )
        assertNotNull(bitmapNegative)

        val bitmapExcess = renderer.renderDialBitmap(
            progress = 1.5f,
            tier = ProgressTier.CRITICAL
        )
        assertNotNull(bitmapExcess)
    }

    @Test
    fun renderDialWithText_returnsValidRenderedBitmap() {
        val data = FocusWidgetData(
            usedMinutes = 45,
            targetMinutes = 120,
            remainingMinutes = 75,
            progress = 45f / 120f,
            tier = ProgressTier.NORMAL,
            remainingFormatted = "1h 15m left",
            usedFormatted = "45m",
            targetFormatted = "2h 0m",
            isTargetExceeded = false
        )

        val bitmap = renderer.renderDialWithCenterText(data = data, sizePx = 220)

        assertNotNull(bitmap)
        assertEquals(220, bitmap.width)
        assertEquals(220, bitmap.height)

        val stream = ByteArrayOutputStream()
        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        assertTrue(stream.size() > 0)
    }
}
