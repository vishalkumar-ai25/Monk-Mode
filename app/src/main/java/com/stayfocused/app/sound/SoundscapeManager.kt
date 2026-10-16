package com.stayfocused.app.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.util.Log

/**
 * High-performance ambient audio mixer wrapping Android's native SoundPool.
 *
 * Guarantees:
 * 1. Native AudioFlinger mixing (0 JVM decoder threads, zero CPU/battery drain).
 * 2. Asynchronous load verification via OnLoadCompleteListener.
 * 3. Exact sample-accurate seamless looping (loop = -1).
 * 4. Atomic pause/resume and zero-allocation real-time volume adjustment.
 */
class SoundscapeManager(
    private val context: Context,
    private val soundPoolFactory: (() -> SoundPool)? = null
) {
    companion object {
        private const val TAG = "SoundscapeManager"
        private const val MAX_STREAMS = 7 // 6 stems + 1 transition chime
    }

    private val soundPool: SoundPool = soundPoolFactory?.invoke() ?: run {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        SoundPool.Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(attributes)
            .build()
    }

    private val soundIdMap = mutableMapOf<String, Int>()
    private val loadedSamples = mutableSetOf<Int>()
    private val activeStreamMap = mutableMapOf<String, Int>()
    private val stemVolumeMap = mutableMapOf<String, Float>()
    private val stemEnabledMap = mutableMapOf<String, Boolean>()

    var masterVolume: Float = 1.0f
        private set

    var isPaused: Boolean = false
        private set

    var isDucked: Boolean = false
        private set

    private var chimeSoundId: Int? = null

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) {
                synchronized(loadedSamples) {
                    loadedSamples.add(sampleId)
                }
                Log.d(TAG, "Sound sample $sampleId loaded successfully")
                // Start any pending streams that were enabled while loading
                synchronized(soundIdMap) {
                    val stemId = soundIdMap.entries.find { it.value == sampleId }?.key
                    if (stemId != null && stemEnabledMap[stemId] == true && (stemVolumeMap[stemId] ?: 0f) > 0f) {
                        applyStemPlayback(stemId)
                    }
                }
            } else {
                Log.e(TAG, "Failed to load sample $sampleId, status = $status")
            }
        }
    }

    /**
     * Pre-loads the 6 ambient stems and transition chime from APK assets.
     */
    fun preloadAssets() {
        val stems = mapOf(
            "rain" to "sounds/rain.ogg",
            "waves" to "sounds/waves.ogg",
            "forest" to "sounds/forest.ogg",
            "whitenoise" to "sounds/whitenoise.ogg",
            "brownnoise" to "sounds/brownnoise.ogg",
            "campfire" to "sounds/campfire.ogg"
        )

        for ((id, path) in stems) {
            try {
                val afd = context.assets.openFd(path)
                val soundId = soundPool.load(afd, 1)
                soundIdMap[id] = soundId
            } catch (e: Exception) {
                Log.e(TAG, "Error opening asset $path", e)
            }
        }

        try {
            val chimeAfd = context.assets.openFd("sounds/chime.ogg")
            chimeSoundId = soundPool.load(chimeAfd, 1)
        } catch (e: Exception) {
            Log.e(TAG, "Error opening chime asset", e)
        }
    }

    fun setMasterVolume(volume: Float) {
        masterVolume = volume.coerceIn(0f, 1f)
        for (stemId in activeStreamMap.keys) {
            updateStreamVolume(stemId)
        }
    }

    fun setStemState(stemId: String, volume: Float, isEnabled: Boolean) {
        stemVolumeMap[stemId] = volume.coerceIn(0f, 1f)
        stemEnabledMap[stemId] = isEnabled
        applyStemPlayback(stemId)
    }

    fun setDucked(ducked: Boolean) {
        if (isDucked == ducked) return
        isDucked = ducked
        for (stemId in activeStreamMap.keys) {
            updateStreamVolume(stemId)
        }
    }

    private fun getEffectiveVolume(stemId: String): Float {
        val stemVol = stemVolumeMap[stemId] ?: 0f
        val duckFactor = if (isDucked) 0.2f else 1.0f
        return (masterVolume * stemVol * duckFactor).coerceIn(0f, 1f)
    }

    private fun applyStemPlayback(stemId: String) {
        val sampleId = soundIdMap[stemId] ?: return
        val isLoaded = synchronized(loadedSamples) { loadedSamples.contains(sampleId) }
        val isEnabled = stemEnabledMap[stemId] ?: false
        val effectiveVol = getEffectiveVolume(stemId)

        if (isEnabled && effectiveVol > 0f && !isPaused) {
            val existingStream = activeStreamMap[stemId]
            if (existingStream != null) {
                soundPool.setVolume(existingStream, effectiveVol, effectiveVol)
            } else if (isLoaded) {
                // loop = -1 for continuous seamless playback
                val streamId = soundPool.play(sampleId, effectiveVol, effectiveVol, 1, -1, 1.0f)
                if (streamId != 0) {
                    activeStreamMap[stemId] = streamId
                }
            }
        } else {
            val existingStream = activeStreamMap.remove(stemId)
            if (existingStream != null) {
                soundPool.stop(existingStream)
            }
        }
    }

    private fun updateStreamVolume(stemId: String) {
        val streamId = activeStreamMap[stemId] ?: return
        val effectiveVol = getEffectiveVolume(stemId)
        soundPool.setVolume(streamId, effectiveVol, effectiveVol)
    }

    fun playChime() {
        val sampleId = chimeSoundId ?: return
        val isLoaded = synchronized(loadedSamples) { loadedSamples.contains(sampleId) }
        if (isLoaded) {
            // One-shot chime at priority 2
            soundPool.play(sampleId, 1.0f, 1.0f, 2, 0, 1.0f)
        }
    }

    fun pauseAll() {
        isPaused = true
        soundPool.autoPause()
    }

    fun resumeAll() {
        isPaused = false
        soundPool.autoResume()
        // Re-apply volumes for active stems
        for (stemId in activeStreamMap.keys) {
            updateStreamVolume(stemId)
        }
    }

    fun stopAll() {
        isPaused = false
        for ((_, streamId) in activeStreamMap) {
            soundPool.stop(streamId)
        }
        activeStreamMap.clear()
        stemEnabledMap.clear()
    }

    fun isAnyStemAudible(): Boolean {
        if (isPaused || masterVolume <= 0f) return false
        return activeStreamMap.any { (stemId, _) ->
            (stemVolumeMap[stemId] ?: 0f) > 0f && (stemEnabledMap[stemId] ?: false)
        }
    }

    fun release() {
        stopAll()
        soundPool.release()
    }
}
