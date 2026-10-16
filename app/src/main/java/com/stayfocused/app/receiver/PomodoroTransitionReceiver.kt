package com.stayfocused.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.stayfocused.app.service.FocusSoundService

/**
 * Hardware-wakeup alarm receiver for Pomodoro phase transitions.
 * Guarantees execution during deep Android Doze and ColorOS Quick Freeze modes.
 */
class PomodoroTransitionReceiver : BroadcastReceiver() {

    companion object {
        const val TAG = "PomodoroTransition"
        const val ACTION_PHASE_COMPLETE = "com.stayfocused.app.action.POMODORO_PHASE_COMPLETE"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_PHASE_COMPLETE) return
        Log.i(TAG, "Pomodoro transition alarm fired. Waking up device.")

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StayFocused:PomodoroWakeLock")
        wakeLock?.acquire(5000L) // 5-second safe auto-release timeout

        try {
            // 1. Play transition chime using USAGE_ALARM (audible even in DND priority mode)
            playTransitionChime(context)

            // 2. Vibrate with attention pulse
            vibrateDevice(context)

            // 3. Signal FocusSoundService to advance Pomodoro phase
            val serviceIntent = Intent(context, FocusSoundService::class.java).apply {
                action = FocusSoundService.ACTION_ALARM_ELAPSED
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing Pomodoro phase transition alarm", e)
        }
    }

    private fun playTransitionChime(context: Context) {
        try {
            val afd = context.assets.openFd("sounds/chime.ogg")
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                )
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                prepare()
                setOnCompletionListener { mp ->
                    mp.release()
                    try { afd.close() } catch (_: Exception) {}
                }
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not play transition chime", e)
        }
    }

    private fun vibrateDevice(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300), -1)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 300, 150, 300), -1)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not vibrate device", e)
        }
    }
}
