package com.stayfocused.app.sound

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.content.res.AssetManager
import android.media.SoundPool
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SoundscapeManagerTest {

    private lateinit var context: Context
    private lateinit var assetManager: AssetManager
    private lateinit var soundPool: SoundPool
    private lateinit var soundscapeManager: SoundscapeManager
    private val loadListenerSlot = slot<SoundPool.OnLoadCompleteListener>()

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        assetManager = mockk(relaxed = true)
        soundPool = mockk(relaxed = true)

        every { context.assets } returns assetManager
        val mockAfd = mockk<AssetFileDescriptor>(relaxed = true)
        every { assetManager.openFd(any()) } returns mockAfd

        every { soundPool.setOnLoadCompleteListener(capture(loadListenerSlot)) } returns Unit
        every { soundPool.load(any<AssetFileDescriptor>(), any()) } answers { 10 }
        every { soundPool.play(any(), any(), any(), any(), any(), any()) } answers { 101 }

        soundscapeManager = SoundscapeManager(context, soundPoolFactory = { soundPool })
    }

    @Test
    fun testPreloadAssetsAndSimulateLoadComplete() {
        soundscapeManager.preloadAssets()

        verify(atLeast = 6) { soundPool.load(any<AssetFileDescriptor>(), 1) }

        // Simulate load completion
        loadListenerSlot.captured.onLoadComplete(soundPool, 10, 0)
    }

    @Test
    fun testEnableStemPlaysSoundWithCorrectVolume() {
        soundscapeManager.preloadAssets()
        loadListenerSlot.captured.onLoadComplete(soundPool, 10, 0)

        soundscapeManager.setMasterVolume(0.8f)
        soundscapeManager.setStemState("rain", volume = 0.5f, isEnabled = true)

        // Effective volume = 0.8 * 0.5 = 0.4
        verify { soundPool.play(10, 0.4f, 0.4f, 1, -1, 1.0f) }
        assertTrue(soundscapeManager.isAnyStemAudible())
    }

    @Test
    fun testDisableStemStopsStream() {
        soundscapeManager.preloadAssets()
        loadListenerSlot.captured.onLoadComplete(soundPool, 10, 0)

        soundscapeManager.setStemState("rain", volume = 0.5f, isEnabled = true)
        assertTrue(soundscapeManager.isAnyStemAudible())

        // Disable rain
        soundscapeManager.setStemState("rain", volume = 0.5f, isEnabled = false)
        verify { soundPool.stop(101) }
        assertFalse(soundscapeManager.isAnyStemAudible())
    }

    @Test
    fun testMasterVolumeUpdatesActiveStreams() {
        soundscapeManager.preloadAssets()
        loadListenerSlot.captured.onLoadComplete(soundPool, 10, 0)

        soundscapeManager.setStemState("rain", volume = 0.5f, isEnabled = true)
        soundscapeManager.setMasterVolume(0.5f)

        // Effective volume = 0.5 * 0.5 = 0.25
        verify { soundPool.setVolume(101, 0.25f, 0.25f) }
    }

    @Test
    fun testPauseAndResumeAll() {
        soundscapeManager.preloadAssets()
        loadListenerSlot.captured.onLoadComplete(soundPool, 10, 0)

        soundscapeManager.setStemState("waves", volume = 0.7f, isEnabled = true)

        soundscapeManager.pauseAll()
        assertTrue(soundscapeManager.isPaused)
        verify { soundPool.autoPause() }
        assertFalse(soundscapeManager.isAnyStemAudible())

        soundscapeManager.resumeAll()
        assertFalse(soundscapeManager.isPaused)
        verify { soundPool.autoResume() }
        assertTrue(soundscapeManager.isAnyStemAudible())
    }

    @Test
    fun testSetDuckedScalesVolume() {
        soundscapeManager.preloadAssets()
        loadListenerSlot.captured.onLoadComplete(soundPool, 10, 0)

        soundscapeManager.setStemState("rain", volume = 1.0f, isEnabled = true)
        verify { soundPool.play(10, 1.0f, 1.0f, 1, -1, 1.0f) }

        // When ducked, volume scaled to 20%
        soundscapeManager.setDucked(true)
        assertTrue(soundscapeManager.isDucked)
        verify { soundPool.setVolume(101, 0.2f, 0.2f) }

        // When unducked, volume restored to 100%
        soundscapeManager.setDucked(false)
        assertFalse(soundscapeManager.isDucked)
        verify { soundPool.setVolume(101, 1.0f, 1.0f) }
    }
}
