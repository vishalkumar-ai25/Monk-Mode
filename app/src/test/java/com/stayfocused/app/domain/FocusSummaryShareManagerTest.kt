package com.stayfocused.app.domain

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.stayfocused.app.domain.model.DailyFocusSummaryData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FocusSummaryShareManagerTest {

    private lateinit var context: Context
    private lateinit var shareManager: FocusSummaryShareManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shareManager = FocusSummaryShareManager(
            context = context,
            uriProvider = { _, file -> Uri.parse("content://com.stayfocused.app.fileprovider/shared_images/${file.name}") }
        )
    }

    @Test
    fun testSaveBitmapToFileCreatesFileOnDisk() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

        val file = shareManager.saveBitmapToFile(bitmap)

        assertNotNull(file)
        assertTrue(file.exists())
        assertTrue(file.length() > 0)
        assertEquals("daily_focus_summary.png", file.name)
    }

    @Test
    fun testSaveBitmapToCacheReturnsValidContentUri() {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

        val uri = shareManager.saveBitmapToCache(bitmap)

        assertNotNull(uri)
        assertTrue(uri.toString().startsWith("content://"))
    }

    @Test
    fun testBuildShareIntentConfiguresProperActionAndStream() {
        val testUri = Uri.parse("content://com.stayfocused.app.fileprovider/shared_images/daily_focus_summary.png")
        val data = DailyFocusSummaryData(
            dateText = "Sep 26, 2026",
            usedMinutes = 40,
            targetMinutes = 120,
            activeLimitsCount = 5,
            blockedDistractionsCount = 14
        )

        val chooserIntent = shareManager.buildShareIntent(testUri, data)

        assertEquals(Intent.ACTION_CHOOSER, chooserIntent.action)
        val targetIntent = chooserIntent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(targetIntent)
        assertEquals(Intent.ACTION_SEND, targetIntent?.action)
        assertEquals("image/png", targetIntent?.type)
        assertEquals(testUri, targetIntent?.getParcelableExtra(Intent.EXTRA_STREAM))

        val text = targetIntent?.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        assertTrue(text.contains("40m"))
        assertTrue(text.contains("120m"))
        assertTrue(text.contains("14 distractions blocked"))
    }
}
