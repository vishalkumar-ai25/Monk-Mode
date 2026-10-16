package com.stayfocused.app.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.data.local.entities.PomodoroSessionEntity
import com.stayfocused.app.domain.PomodoroStateMachine
import com.stayfocused.app.domain.model.PomodoroConfig
import com.stayfocused.app.domain.model.PomodoroPhase
import com.stayfocused.app.domain.model.PomodoroState
import com.stayfocused.app.receiver.PomodoroTransitionReceiver
import com.stayfocused.app.sound.SoundscapeManager
import com.stayfocused.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Hardened Foreground Service managing Pomodoro Study Timer and Multi-Stem Soundscape.
 *
 * Invariants:
 * 1. Dual FGS type: specialUse (digital wellbeing) for timer, mediaPlayback only when audio is audible.
 * 2. MediaSessionCompat synchronization satisfying Android 14 FGS policy.
 * 3. Exact alarm scheduling for phase transitions in deep Doze mode.
 * 4. Chronometer notification avoiding 1-second IPC wakeups.
 * 5. Non-destructive DND filter management.
 */
class FocusSoundService : Service() {

    companion object {
        const val TAG = "FocusSoundService"
        const val CHANNEL_ID = "focus_pomodoro_soundscape"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START_WORK = "com.stayfocused.app.action.POMODORO_START_WORK"
        const val ACTION_PAUSE = "com.stayfocused.app.action.POMODORO_PAUSE"
        const val ACTION_RESUME = "com.stayfocused.app.action.POMODORO_RESUME"
        const val ACTION_SKIP = "com.stayfocused.app.action.POMODORO_SKIP"
        const val ACTION_RESET = "com.stayfocused.app.action.POMODORO_RESET"
        const val ACTION_ALARM_ELAPSED = "com.stayfocused.app.action.POMODORO_ALARM_ELAPSED"
        const val ACTION_UPDATE_STEM = "com.stayfocused.app.action.SOUNDSCAPE_UPDATE_STEM"
        const val ACTION_UPDATE_MASTER_VOL = "com.stayfocused.app.action.SOUNDSCAPE_UPDATE_MASTER_VOL"
        const val ACTION_STOP_SERVICE = "com.stayfocused.app.action.POMODORO_STOP_SERVICE"

        const val EXTRA_WORK_MINS = "extra_work_mins"
        const val EXTRA_SHORT_BREAK_MINS = "extra_short_break_mins"
        const val EXTRA_LONG_BREAK_MINS = "extra_long_break_mins"
        const val EXTRA_LONG_BREAK_INTERVAL = "extra_long_break_interval"
        const val EXTRA_AUTO_START_BREAKS = "extra_auto_start_breaks"
        const val EXTRA_AUTO_START_WORK = "extra_auto_start_work"
        const val EXTRA_DND_ENABLED = "extra_dnd_enabled"
        const val EXTRA_LINKED_PROFILE_ID = "extra_linked_profile_id"

        const val EXTRA_STEM_ID = "extra_stem_id"
        const val EXTRA_STEM_VOLUME = "extra_stem_volume"
        const val EXTRA_STEM_ENABLED = "extra_stem_enabled"
        const val EXTRA_MASTER_VOLUME = "extra_master_volume"

        private val _pomodoroStateFlow = MutableStateFlow(PomodoroState())
        val pomodoroStateFlow: StateFlow<PomodoroState> = _pomodoroStateFlow.asStateFlow()

        private val _isSoundscapeActiveFlow = MutableStateFlow(false)
        val isSoundscapeActiveFlow: StateFlow<Boolean> = _isSoundscapeActiveFlow.asStateFlow()

        var isServiceRunning = false
            private set

        fun startWork(context: Context, config: PomodoroConfig) {
            val intent = Intent(context, FocusSoundService::class.java).apply {
                action = ACTION_START_WORK
                putExtra(EXTRA_WORK_MINS, config.workMinutes)
                putExtra(EXTRA_SHORT_BREAK_MINS, config.shortBreakMinutes)
                putExtra(EXTRA_LONG_BREAK_MINS, config.longBreakMinutes)
                putExtra(EXTRA_LONG_BREAK_INTERVAL, config.longBreakInterval)
                putExtra(EXTRA_AUTO_START_BREAKS, config.autoStartBreaks)
                putExtra(EXTRA_AUTO_START_WORK, config.autoStartWork)
                putExtra(EXTRA_DND_ENABLED, config.isDndEnabled)
                config.linkedProfileId?.let { putExtra(EXTRA_LINKED_PROFILE_ID, it) }
            }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun pause(context: Context) {
            val intent = Intent(context, FocusSoundService::class.java).apply { action = ACTION_PAUSE }
            context.startService(intent)
        }

        fun resume(context: Context) {
            val intent = Intent(context, FocusSoundService::class.java).apply { action = ACTION_RESUME }
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun skip(context: Context) {
            val intent = Intent(context, FocusSoundService::class.java).apply { action = ACTION_SKIP }
            context.startService(intent)
        }

        fun reset(context: Context) {
            val intent = Intent(context, FocusSoundService::class.java).apply { action = ACTION_RESET }
            context.startService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, FocusSoundService::class.java).apply { action = ACTION_STOP_SERVICE }
            context.startService(intent)
        }

        fun updateStem(context: Context, stemId: String, volume: Float, isEnabled: Boolean) {
            if (!isServiceRunning && (!isEnabled || volume <= 0f)) {
                // Service is not running and stem is disabled/zeroed; persist directly to SQLite
                val db = StayFocusedDatabase.getInstance(context.applicationContext)
                CoroutineScope(Dispatchers.IO).launch {
                    db.soundStemDao().updateStemState(stemId, volume, isEnabled)
                }
                return
            }

            val intent = Intent(context, FocusSoundService::class.java).apply {
                action = ACTION_UPDATE_STEM
                putExtra(EXTRA_STEM_ID, stemId)
                putExtra(EXTRA_STEM_VOLUME, volume)
                putExtra(EXTRA_STEM_ENABLED, isEnabled)
            }
            if (isServiceRunning) {
                context.startService(intent)
            } else {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            }
        }

        fun updateMasterVolume(context: Context, masterVolume: Float) {
            if (!isServiceRunning && masterVolume <= 0f) {
                return
            }

            val intent = Intent(context, FocusSoundService::class.java).apply {
                action = ACTION_UPDATE_MASTER_VOL
                putExtra(EXTRA_MASTER_VOLUME, masterVolume)
            }
            if (isServiceRunning) {
                context.startService(intent)
            } else {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickerJob: Job? = null

    private lateinit var stateMachine: PomodoroStateMachine
    private lateinit var soundscapeManager: SoundscapeManager
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var alarmManager: AlarmManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var database: StayFocusedDatabase

    private var currentConfig = PomodoroConfig()
    private var originalDndFilter: Int? = null

    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequestCompat? = null
    private var hasAudioFocus = false

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.i(TAG, "Audio focus lost permanently; pausing ambient audio")
                hasAudioFocus = false
                soundscapeManager.pauseAll()
                _isSoundscapeActiveFlow.value = false
                updateForegroundServiceState()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.i(TAG, "Audio focus lost transiently; pausing ambient audio")
                soundscapeManager.pauseAll()
                _isSoundscapeActiveFlow.value = false
                updateForegroundServiceState()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.i(TAG, "Audio focus ducking requested; scaling master volume to 20%")
                soundscapeManager.setDucked(true)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.i(TAG, "Audio focus gained/restored")
                hasAudioFocus = true
                soundscapeManager.setDucked(false)
                if (soundscapeManager.isAnyStemAudible()) {
                    soundscapeManager.resumeAll()
                    _isSoundscapeActiveFlow.value = true
                    updateForegroundServiceState()
                }
            }
        }
    }

    private fun requestCentralizedAudioFocus(): Boolean {
        if (hasAudioFocus) return true

        val attributes = AudioAttributesCompat.Builder()
            .setUsage(AudioAttributesCompat.USAGE_MEDIA)
            .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
            .build()

        val request = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .setWillPauseWhenDucked(false)
            .build()

        audioFocusRequest = request
        val result = AudioManagerCompat.requestAudioFocus(audioManager, request)
        hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        Log.i(TAG, "Requested centralized audio focus, granted: $hasAudioFocus")
        return hasAudioFocus
    }

    private fun abandonCentralizedAudioFocus() {
        audioFocusRequest?.let { request ->
            AudioManagerCompat.abandonAudioFocusRequest(audioManager, request)
            Log.i(TAG, "Abandoned centralized audio focus")
        }
        audioFocusRequest = null
        hasAudioFocus = false
        soundscapeManager.setDucked(false)
    }

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                Log.i(TAG, "Audio becoming noisy (headphones unplugged); pausing soundscape")
                soundscapeManager.pauseAll()
                _isSoundscapeActiveFlow.value = false
                updateForegroundServiceState()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        database = StayFocusedDatabase.getInstance(this)
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        stateMachine = PomodoroStateMachine()
        soundscapeManager = SoundscapeManager(this).apply {
            preloadAssets()
        }

        mediaSession = MediaSessionCompat(this, "FocusSoundMediaSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { handleAction(ACTION_RESUME) }
                override fun onPause() { handleAction(ACTION_PAUSE) }
                override fun onSkipToNext() { handleAction(ACTION_SKIP) }
                override fun onStop() { handleAction(ACTION_STOP_SERVICE) }
            })
            isActive = false
        }

        createNotificationChannel()
        registerReceiver(becomingNoisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))

        // Start 1-second UI ticker
        startTicker()

        // Restore persisted state from DB asynchronously
        serviceScope.launch(Dispatchers.IO) {
            val savedSettings = database.pomodoroDao().getSettings()
            if (savedSettings != null) {
                currentConfig = PomodoroConfig(
                    workMinutes = savedSettings.workMinutes,
                    shortBreakMinutes = savedSettings.shortBreakMinutes,
                    longBreakMinutes = savedSettings.longBreakMinutes,
                    longBreakInterval = savedSettings.longBreakInterval,
                    autoStartBreaks = savedSettings.autoStartBreaks,
                    autoStartWork = savedSettings.autoStartWork,
                    isDndEnabled = savedSettings.isDndEnabled,
                    linkedProfileId = savedSettings.linkedProfileId
                )
            }

            val savedSession = database.pomodoroDao().getSession()
            if (savedSession != null && savedSession.phaseName != PomodoroPhase.IDLE.name) {
                val elapsed = SystemClock.elapsedRealtime()
                val wall = System.currentTimeMillis()
                val bootCount = getBootCount()
                val rawState = PomodoroState(
                    phase = try { PomodoroPhase.valueOf(savedSession.phaseName) } catch (_: Exception) { PomodoroPhase.IDLE },
                    currentCycle = savedSession.currentCycle,
                    remainingMs = savedSession.remainingMs,
                    totalDurationMs = savedSession.totalDurationMs,
                    isRunning = savedSession.isRunning,
                    targetElapsedRealtimeMs = savedSession.targetElapsedRealtimeMs,
                    targetWallTimeMs = savedSession.targetWallTimeMs,
                    bootCount = savedSession.bootCount
                )
                val restored = stateMachine.restoreSession(rawState, elapsed, wall, bootCount, currentConfig)
                originalDndFilter = savedSession.originalDndFilter
                withContext(Dispatchers.Main) {
                    onStateTransitioned(restored)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START_WORK -> {
                extractConfigFromIntent(intent)
                val elapsed = SystemClock.elapsedRealtime()
                val wall = System.currentTimeMillis()
                val bootCount = getBootCount()
                val newState = stateMachine.startWork(currentConfig, elapsed, wall, bootCount)
                onStateTransitioned(newState)
            }
            ACTION_PAUSE -> {
                val elapsed = SystemClock.elapsedRealtime()
                val wall = System.currentTimeMillis()
                val bootCount = getBootCount()
                val newState = stateMachine.pause(elapsed, wall, bootCount)
                onStateTransitioned(newState)
            }
            ACTION_RESUME -> {
                val elapsed = SystemClock.elapsedRealtime()
                val wall = System.currentTimeMillis()
                val bootCount = getBootCount()
                val newState = stateMachine.resume(elapsed, wall, bootCount)
                onStateTransitioned(newState)
            }
            ACTION_SKIP -> {
                val elapsed = SystemClock.elapsedRealtime()
                val wall = System.currentTimeMillis()
                val bootCount = getBootCount()
                val newState = stateMachine.skip(currentConfig, elapsed, wall, bootCount)
                onStateTransitioned(newState)
            }
            ACTION_RESET -> {
                val newState = stateMachine.reset(currentConfig.longBreakInterval)
                onStateTransitioned(newState)
            }
            ACTION_ALARM_ELAPSED -> {
                val elapsed = SystemClock.elapsedRealtime()
                val wall = System.currentTimeMillis()
                val bootCount = getBootCount()
                val newState = stateMachine.onAlarmElapsed(currentConfig, elapsed, wall, bootCount)
                onStateTransitioned(newState)
            }
            ACTION_UPDATE_STEM -> {
                val stemId = intent.getStringExtra(EXTRA_STEM_ID)
                val volume = intent.getFloatExtra(EXTRA_STEM_VOLUME, 0f)
                val isEnabled = intent.getBooleanExtra(EXTRA_STEM_ENABLED, false)
                if (stemId != null) {
                    soundscapeManager.setStemState(stemId, volume, isEnabled)
                    _isSoundscapeActiveFlow.value = soundscapeManager.isAnyStemAudible()
                    updateForegroundServiceState()
                    serviceScope.launch(Dispatchers.IO) {
                        database.soundStemDao().updateStemState(stemId, volume, isEnabled)
                    }
                }
            }
            ACTION_UPDATE_MASTER_VOL -> {
                val volume = intent.getFloatExtra(EXTRA_MASTER_VOLUME, 1.0f)
                soundscapeManager.setMasterVolume(volume)
                _isSoundscapeActiveFlow.value = soundscapeManager.isAnyStemAudible()
                updateForegroundServiceState()
            }
            ACTION_STOP_SERVICE -> {
                handleStopService()
                return START_NOT_STICKY
            }
        }

        return START_NOT_STICKY
    }

    private fun handleAction(action: String) {
        val intent = Intent(this, FocusSoundService::class.java).apply {
            this.action = action
        }
        startService(intent)
    }

    private fun onStateTransitioned(newState: PomodoroState) {
        _pomodoroStateFlow.value = newState

        // 1. Alarm Scheduling / Cancellation
        if (newState.isRunning && newState.targetElapsedRealtimeMs > 0L) {
            scheduleTransitionAlarm(newState.targetElapsedRealtimeMs)
        } else {
            cancelTransitionAlarm()
        }

        // 2. Profile and DND management
        when (newState.phase) {
            PomodoroPhase.WORK -> {
                enableDndIfConfigured()
                armLinkedProfile()
            }
            PomodoroPhase.SHORT_BREAK, PomodoroPhase.LONG_BREAK, PomodoroPhase.IDLE -> {
                restoreDndIfModified()
                disarmLinkedProfile()
            }
            PomodoroPhase.PAUSED -> {
                // Keep current DND and profile intact during pause
            }
        }

        // 3. Update Foreground Service state & Notification
        updateForegroundServiceState()

        // 4. Persist running session state
        serviceScope.launch(Dispatchers.IO) {
            val sessionEntity = PomodoroSessionEntity(
                id = 1,
                phaseName = newState.phase.name,
                currentCycle = newState.currentCycle,
                targetElapsedRealtimeMs = newState.targetElapsedRealtimeMs,
                targetWallTimeMs = newState.targetWallTimeMs,
                totalDurationMs = newState.totalDurationMs,
                remainingMs = newState.remainingMs,
                bootCount = newState.bootCount,
                isRunning = newState.isRunning,
                originalDndFilter = originalDndFilter ?: 1
            )
            database.pomodoroDao().saveSession(sessionEntity)
        }
    }

    private fun scheduleTransitionAlarm(targetElapsedRealtimeMs: Long) {
        val intent = Intent(this, PomodoroTransitionReceiver::class.java).apply {
            action = PomodoroTransitionReceiver.ACTION_PHASE_COMPLETE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            3001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    targetElapsedRealtimeMs,
                    pendingIntent
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    targetElapsedRealtimeMs,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled Pomodoro exact wakeup alarm for elapsed $targetElapsedRealtimeMs")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule exact alarm", e)
        }
    }

    private fun cancelTransitionAlarm() {
        val intent = Intent(this, PomodoroTransitionReceiver::class.java).apply {
            action = PomodoroTransitionReceiver.ACTION_PHASE_COMPLETE
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            3001,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Cancelled Pomodoro exact alarm")
        }
    }

    private fun enableDndIfConfigured() {
        if (!currentConfig.isDndEnabled) return
        try {
            if (notificationManager.isNotificationPolicyAccessGranted) {
                if (originalDndFilter == null) {
                    originalDndFilter = notificationManager.currentInterruptionFilter
                }
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                Log.i(TAG, "Enabled DND priority mode (saved original: $originalDndFilter)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not set DND interruption filter", e)
        }
    }

    private fun restoreDndIfModified() {
        val filter = originalDndFilter ?: return
        try {
            if (notificationManager.isNotificationPolicyAccessGranted) {
                notificationManager.setInterruptionFilter(filter)
                Log.i(TAG, "Restored original DND filter: $filter")
                originalDndFilter = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not restore DND interruption filter", e)
        }
    }

    private fun armLinkedProfile() {
        val profileId = currentConfig.linkedProfileId ?: return
        serviceScope.launch(Dispatchers.IO) {
            try {
                database.focusProfileDao().setProfileActive(profileId, true)
                Log.i(TAG, "Armed linked focus profile id: $profileId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to arm linked focus profile", e)
            }
        }
    }

    private fun disarmLinkedProfile() {
        val profileId = currentConfig.linkedProfileId ?: return
        serviceScope.launch(Dispatchers.IO) {
            try {
                database.focusProfileDao().setProfileActive(profileId, false)
                Log.i(TAG, "Disarmed linked focus profile id: $profileId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to disarm linked focus profile", e)
            }
        }
    }

    private fun updateForegroundServiceState() {
        val isSoundActive = soundscapeManager.isAnyStemAudible()
        val isTimerActive = _pomodoroStateFlow.value.phase != PomodoroPhase.IDLE

        if (isSoundActive) {
            requestCentralizedAudioFocus()
        } else {
            abandonCentralizedAudioFocus()
        }

        if (!isSoundActive && !isTimerActive) {
            // Nothing active; satisfy FGS requirement if started as FGS, then stop
            val notification = buildNotification(_pomodoroStateFlow.value, false)
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        // Determine FGS Type
        val fgsType = if (isSoundActive) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else 0
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else 0
        }

        // Update MediaSession state (active ONLY when audio is playing to prevent Bluetooth hijacking)
        mediaSession.isActive = isSoundActive
        if (isSoundActive) {
            mediaSession.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_STOP)
                    .setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                    .build()
            )
        } else {
            mediaSession.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_NONE, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0f)
                    .build()
            )
        }

        val notification = buildNotification(_pomodoroStateFlow.value, isSoundActive)
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
    }

    private fun buildNotification(state: PomodoroState, isSoundActive: Boolean): android.app.Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when (state.phase) {
            PomodoroPhase.WORK -> "Deep Focus (Cycle ${state.currentCycle}/${state.totalCycles})"
            PomodoroPhase.SHORT_BREAK -> "Short Break"
            PomodoroPhase.LONG_BREAK -> "Long Break (Cycle ${state.currentCycle})"
            PomodoroPhase.PAUSED -> "Timer Paused"
            PomodoroPhase.IDLE -> if (isSoundActive) "Ambient Soundscape Playing" else "Focus Sanctuary"
        }

        val soundHint = if (isSoundActive) " • Ambient Soundscape ON" else ""

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentIntent(openAppIntent)
            .setOngoing(state.isRunning || isSoundActive)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (state.phase != PomodoroPhase.IDLE) {
            if (state.isRunning && state.targetWallTimeMs > System.currentTimeMillis()) {
                builder.setUsesChronometer(true)
                builder.setChronometerCountDown(true)
                builder.setWhen(state.targetWallTimeMs)
                builder.setContentText("Focus Interval in progress$soundHint")
            } else {
                builder.setUsesChronometer(false)
                builder.setContentText("Paused: ${state.formattedRemainingTime} remaining$soundHint")
            }

            // Notification Actions
            if (state.isRunning) {
                val pauseIntent = PendingIntent.getService(
                    this, 1,
                    Intent(this, FocusSoundService::class.java).apply { action = ACTION_PAUSE },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(android.R.drawable.ic_media_pause, "Pause", pauseIntent)
            } else {
                val resumeIntent = PendingIntent.getService(
                    this, 2,
                    Intent(this, FocusSoundService::class.java).apply { action = ACTION_RESUME },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(android.R.drawable.ic_media_play, "Resume", resumeIntent)
            }

            val skipIntent = PendingIntent.getService(
                this, 3,
                Intent(this, FocusSoundService::class.java).apply { action = ACTION_SKIP },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_next, "Skip", skipIntent)

            val stopIntent = PendingIntent.getService(
                this, 4,
                Intent(this, FocusSoundService::class.java).apply { action = ACTION_RESET },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Reset", stopIntent)
        } else {
            builder.setContentText("Offline ambient audio mixer active")
            val stopSoundIntent = PendingIntent.getService(
                this, 5,
                Intent(this, FocusSoundService::class.java).apply { action = ACTION_STOP_SERVICE },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_pause, "Mute All", stopSoundIntent)
        }

        return builder.build()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            var counter = 0
            while (isActive) {
                delay(1000L)
                val currentState = _pomodoroStateFlow.value
                if (currentState.isRunning) {
                    val elapsed = SystemClock.elapsedRealtime()
                    val wall = System.currentTimeMillis()
                    val bootCount = getBootCount()
                    val updated = stateMachine.tick(elapsed, wall, bootCount, currentConfig)
                    _pomodoroStateFlow.value = updated

                    counter++
                    // Persist session checkpoint every 15 seconds
                    if (counter % 15 == 0) {
                        launch(Dispatchers.IO) {
                            database.pomodoroDao().updateSessionProgress(updated.isRunning, updated.remainingMs)
                        }
                    }
                }
            }
        }
    }

    private fun handleStopService() {
        cancelTransitionAlarm()
        restoreDndIfModified()
        disarmLinkedProfile()
        abandonCentralizedAudioFocus()
        soundscapeManager.stopAll()
        _isSoundscapeActiveFlow.value = false
        _pomodoroStateFlow.value = stateMachine.reset()

        serviceScope.launch(Dispatchers.IO) {
            database.pomodoroDao().clearSession()
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun extractConfigFromIntent(intent: Intent) {
        currentConfig = PomodoroConfig(
            workMinutes = intent.getIntExtra(EXTRA_WORK_MINS, currentConfig.workMinutes),
            shortBreakMinutes = intent.getIntExtra(EXTRA_SHORT_BREAK_MINS, currentConfig.shortBreakMinutes),
            longBreakMinutes = intent.getIntExtra(EXTRA_LONG_BREAK_MINS, currentConfig.longBreakMinutes),
            longBreakInterval = intent.getIntExtra(EXTRA_LONG_BREAK_INTERVAL, currentConfig.longBreakInterval),
            autoStartBreaks = intent.getBooleanExtra(EXTRA_AUTO_START_BREAKS, currentConfig.autoStartBreaks),
            autoStartWork = intent.getBooleanExtra(EXTRA_AUTO_START_WORK, currentConfig.autoStartWork),
            isDndEnabled = intent.getBooleanExtra(EXTRA_DND_ENABLED, currentConfig.isDndEnabled),
            linkedProfileId = if (intent.hasExtra(EXTRA_LINKED_PROFILE_ID)) {
                intent.getLongExtra(EXTRA_LINKED_PROFILE_ID, -1L).takeIf { it > 0 }
            } else currentConfig.linkedProfileId
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Focus Timer & Ambient Soundscape",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Displays the active Pomodoro interval countdown and ambient sound mixer status."
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun getBootCount(): Int {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                android.provider.Settings.Global.getInt(contentResolver, android.provider.Settings.Global.BOOT_COUNT, 1)
            } else 1
        } catch (_: Exception) { 1 }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        tickerJob?.cancel()
        serviceScope.cancel()
        try { unregisterReceiver(becomingNoisyReceiver) } catch (_: Exception) {}
        cancelTransitionAlarm()
        restoreDndIfModified()
        abandonCentralizedAudioFocus()
        soundscapeManager.release()
        mediaSession.release()
    }
}
