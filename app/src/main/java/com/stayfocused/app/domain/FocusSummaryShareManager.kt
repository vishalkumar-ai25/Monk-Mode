package com.stayfocused.app.domain

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.stayfocused.app.domain.model.DailyFocusSummaryData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Manages caching the generated summary card bitmap and launching the system share sheet.
 * Operates purely on local cache and scoped FileProvider content URIs with zero network involvement.
 */
class FocusSummaryShareManager(
    private val context: Context,
    private val bitmapGenerator: FocusSummaryBitmapGenerator = FocusSummaryBitmapGenerator(),
    private val authority: String = "${context.packageName}.fileprovider",
    private val uriProvider: ((Context, File) -> Uri)? = null
) {

    /**
     * Saves the bitmap to local cache as a PNG file and returns the File handle.
     * Cleans up stale summary images to prevent storage bloat and avoids 3rd party URI caching bugs.
     */
    fun saveBitmapToFile(bitmap: Bitmap): File {
        val shareDir = File(context.cacheDir, "shared_images").apply {
            if (!exists()) mkdirs()
        }
        // Clean up prior summary files to avoid unbounded cache growth
        shareDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("focus_summary_") || file.name == "daily_focus_summary.png") {
                file.delete()
            }
        }
        val file = File(shareDir, "focus_summary_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }

    /**
     * Saves the bitmap to local cache and returns a content URI.
     */
    fun saveBitmapToCache(bitmap: Bitmap): Uri {
        val file = saveBitmapToFile(bitmap)
        return uriProvider?.invoke(context, file) ?: FileProvider.getUriForFile(context, authority, file)
    }

    /**
     * Constructs the Intent.ACTION_SEND chooser intent for sharing the image.
     * Explicitly attaches ClipData for reliable URI permission propagation across Android 10+ and OEM ROMs.
     */
    fun buildShareIntent(contentUri: Uri, data: DailyFocusSummaryData): Intent {
        val shareText = "Today's focus performance on Monk Mode: ${data.usedMinutes}m screen time out of ${data.targetMinutes}m target (${data.focusScorePercentage}% focus maintained), ${data.blockedDistractionsCount} distractions blocked."

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            clipData = ClipData.newRawUri("Monk Mode Summary", contentUri)
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "My Monk Mode Focus Today")
            putExtra(Intent.EXTRA_TEXT, shareText)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        return Intent.createChooser(sendIntent, "Share Today's Focus").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Generates bitmap off the main thread, writes to cache on Dispatchers.IO,
     * and triggers the Android share sheet on Dispatchers.Main.
     */
    suspend fun shareDailySummary(data: DailyFocusSummaryData) = withContext(Dispatchers.IO) {
        val bitmap = bitmapGenerator.generateSummaryBitmap(data)
        val uri = saveBitmapToCache(bitmap)
        val chooserIntent = buildShareIntent(uri, data)
        withContext(Dispatchers.Main) {
            context.startActivity(chooserIntent)
        }
    }
}
