# ADR 020: Hardened Pomodoro Study Timer State Machine and Ambient Soundscape Mixer

## Status
Accepted (Hardened via Adversarial Review)

## Context
Commercial productivity apps (like Stay Focused, Forest, and Brain.fm) integrate Pomodoro intervals directly with app/website blocking profiles and offline ambient soundscapes. Users entering intense work or study sessions need:
1. **Structured Interval Pacing**: Standard 25/5 or custom work/break intervals that automatically arm distraction blocks during work phases and relax them during breaks.
2. **Audio Sanctuary**: Offline multi-stem ambient sound mixing (Rain, Waves, Forest, Brown Noise, White Noise, Campfire) to mask distracting environments without depending on cloud music streaming or draining mobile data.
3. **Hardware Monotonicity & Doze Resilience**: Timer countdowns and audio loops must survive Android Doze mode, aggressive OEM process trashing (e.g. Realme UI 5.0 / ColorOS battery management), and sleep transitions without drifting, stuttering, or dropping background playback.

An initial architecture draft was audited by the `adversarial-reviewer` and revealed 9 critical vulnerabilities:
1. Missing `FOREGROUND_SERVICE_MEDIA_PLAYBACK` in manifest causing instant startup crashes on Android 14.
2. FGS termination on silent timers under Android 14 mediaPlayback enforcement.
3. AudioTrack depletion and thread thrashing on 6 simultaneous ExoPlayer instances on Dimensity 6300 chipsets.
4. Coroutine delay freeze during Android Doze mode / ColorOS Quick Freeze.
5. Strict Mode vs Pomodoro break deadlocks.
6. Destructive DND reset overriding user's prior silence modes.
7. Database migration data loss from unseeded tables and cascade deletion hazards.
8. Clock drift upon device reboot with `elapsedRealtime()`.
9. Notification IPC throttling from 1-second `notify()` loops.

This hardened ADR resolves every vulnerability through concrete architectural invariants.

---

## Decision

### 1. Pure Kotlin Pomodoro Domain State Machine (`PomodoroStateMachine.kt`)
- Zero Android framework dependencies (100% JVM unit testable).
- Explicit state hierarchy:
  - `Idle`: Timer not running.
  - `Work(cycle: Int, totalCycles: Int, remainingMs: Long, totalDurationMs: Long)`: Deep focus interval.
  - `ShortBreak(cycle: Int, remainingMs: Long, totalDurationMs: Long)`: Rest interval between cycles.
  - `LongBreak(remainingMs: Long, totalDurationMs: Long)`: Extended rest interval after completion of `longBreakInterval` cycles.
  - `Paused(phase: PomodoroPhase, remainingMs: Long, totalDurationMs: Long, cycle: Int)`: Suspended state.

#### Hardware-Monotonic Dual-Clock Invariant & Reboot Recovery
- Timing does **NOT** rely on coroutine delays.
- Upon starting/resuming a phase:
  ```kotlin
  targetElapsedRealtimeMs = currentElapsedRealtimeMs + durationMs
  targetWallTimeMs = currentWallTimeMs + durationMs
  sessionBootCount = currentBootCount
  ```
- On clock tick:
  - If `currentBootCount == sessionBootCount`, `remainingMs = maxOf(0L, targetElapsedRealtimeMs - currentElapsedRealtimeMs)`.
  - If `currentBootCount != sessionBootCount` (device rebooted), `remainingMs = maxOf(0L, targetWallTimeMs - currentWallTimeMs)`.

#### Doze-Proof Hardware Wakeup Alarms
- While screen is on, UI and notification display tick via `SystemClock.elapsedRealtime()`.
- When screen turns off, coroutine delays freeze during Android Doze Mode.
- To guarantee exact phase transitions, `PomodoroService` schedules an exact hardware wakeup alarm:
  ```kotlin
  alarmManager.setExactAndAllowWhileIdle(
      AlarmManager.ELAPSED_REALTIME_WAKEUP,
      targetElapsedRealtimeMs,
      transitionPendingIntent
  )
  ```
- Upon alarm trigger, `PomodoroTransitionReceiver`:
  1. Acquires a `PartialWakeLock` with a 5-second timeout.
  2. Plays the transition chime using `AudioAttributes.USAGE_ALARM` with `FLAG_AUDIBILITY_ENFORCED` so it sounds even if the screen is off or DND is active.
  3. Fires a high-priority vibration pulse.
  4. Advances the state machine to the next phase (Work $\to$ Break or Break $\to$ Work).

#### Battery-Optimized Chronometer Notification
- Zero 1-second notification IPC spam:
  ```kotlin
  notificationBuilder
      .setUsesChronometer(true)
      .setChronometerCountDown(true)
      .setWhen(targetWallTimeMs)
  ```
- SystemUI renders the countdown smoothly at 60fps on the display without waking the app CPU or sending Binder IPC transactions.

---

### 2. Dual-Layer Focus Profile & Non-Destructive DND

#### Isolated Profile Arming (Zero Break Deadlock)
- When entering `Work`:
  - If `linkedProfileId != null`, arms the linked focus profile.
- When entering `ShortBreak` or `LongBreak`:
  - Only disarms the `linkedProfileId` so the user can use apps associated with that specific profile.
  - **INVARIANT**: Does **NOT** activate a global `BreakSessionEntity`. Global daily app limits, launch counts, continuous session cooldowns, and adult web filters remain 100% strictly enforced.
  - If the user was already in an independent Strict Mode session before starting Pomodoro, that strict session remains active and cannot be bypassed.

#### Non-Destructive DND Filter Management
- When Pomodoro starts with DND enabled:
  - Safely check `notificationManager.isNotificationPolicyAccessGranted()`.
  - Store `originalInterruptionFilter = notificationManager.currentInterruptionFilter`.
  - Wrap in `runCatching` to prevent `SecurityException`.
  - Apply `INTERRUPTION_FILTER_PRIORITY`.
- On break transition, pause, or session stop:
  - Restore `originalInterruptionFilter` (e.g. if the user already had DND active before Pomodoro, do NOT force `INTERRUPTION_FILTER_ALL`).

---

### 3. Ambient Soundscape Mixer (`SoundPool` Multi-Stem Engine)

#### SoundPool over Multi-ExoPlayer Architecture
- Multi-ExoPlayer (6 decoders) on budget SoCs (Dimensity 6300) depletes AudioFlinger tracks, causes 18-24 thread overhead, and risks `AudioTrack init failed`.
- Solution: Use Android's native `SoundPool`:
  ```kotlin
  val attributes = AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_MEDIA)
      .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
      .build()
  val soundPool = SoundPool.Builder()
      .setMaxStreams(6)
      .setAudioAttributes(attributes)
      .build()
  ```
- **Advantages**:
  1. Low memory footprint: Sound stems (10-30s seamless loops) are pre-loaded into memory (~1MB each).
  2. Native AudioFlinger mixing: All 6 stems are mixed in hardware/native C++ with 0 JVM thread overhead.
  3. True sample-accurate gapless looping: `soundPool.play(soundId, vol, vol, 1, -1, 1.0f)`.
  4. Real-time volume scaling: `soundPool.setVolume(streamId, effectiveVol, effectiveVol)` with zero allocation churn or lifecycle race conditions.
  5. Centralized atomic pause/resume: `soundPool.autoPause()` / `soundPool.autoResume()`.

#### Single Centralized Audio Focus
- Single `AudioFocusRequestCompat` requested at the service level.
- On `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK`: scale master volume to 20%.
- On `AUDIOFOCUS_LOSS_TRANSIENT`: call `soundPool.autoPause()`.
- On `AUDIOFOCUS_GAIN`: call `soundPool.autoResume()`.
- On `ACTION_AUDIO_BECOMING_NOISY` (headphones unplugged): pause soundscape immediately.

#### Foreground Service Lifecycle & Permissions
- Manifest declarations:
  ```xml
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
  <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
  ```
- Service configuration:
  - `android:foregroundServiceType="specialUse|mediaPlayback"`
  - When only timer is running: started with `FOREGROUND_SERVICE_TYPE_SPECIAL_USE`.
  - When soundscape is actively playing: promoted with `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`.
  - Silent timers will never be killed for lack of active media output.

---

### 4. Room Database Schema & Migration 10 $\to$ 11

#### Tables
1. `pomodoro_settings`:
   - `id: Int` (PK = 1)
   - `workMinutes: Int` (default 25)
   - `shortBreakMinutes: Int` (default 5)
   - `longBreakMinutes: Int` (default 15)
   - `longBreakInterval: Int` (default 4)
   - `autoStartBreaks: Boolean` (default false)
   - `autoStartWork: Boolean` (default false)
   - `isDndEnabled: Boolean` (default false)
   - `linkedProfileId: Long?` (Foreign key to `focus_profiles(id)` with `onDelete = ForeignKey.SET_NULL`)
   - Index on `linkedProfileId`.

2. `pomodoro_session`:
   - `id: Int` (PK = 1)
   - `phaseName: String` ("IDLE", "WORK", "SHORT_BREAK", "LONG_BREAK", "PAUSED")
   - `currentCycle: Int`
   - `targetElapsedRealtimeMs: Long`
   - `targetWallTimeMs: Long`
   - `totalDurationMs: Long`
   - `remainingMs: Long`
   - `bootCount: Int`
   - `isRunning: Boolean`
   - `originalDndFilter: Int`

3. `sound_stems`:
   - `stemId: String` (PK)
   - `title: String`
   - `assetPath: String`
   - `volume: Float` (0.0 to 1.0)
   - `isEnabled: Boolean`

#### Migration 10 $\to$ 11 Seeding
- `MIGRATION_10_11` creates all 3 tables with appropriate foreign keys and indices.
- **CRITICAL**: Executes `INSERT OR IGNORE` for:
  - Default `pomodoro_settings` row (`id = 1, workMinutes = 25, shortBreakMinutes = 5, ...`).
  - 6 initial `sound_stems`:
    1. `rain` ("Rain & Thunder", "sounds/rain.ogg", 0.0f, false)
    2. `waves` ("Ocean Waves", "sounds/waves.ogg", 0.0f, false)
    3. `forest` ("Forest Wind", "sounds/forest.ogg", 0.0f, false)
    4. `whitenoise` ("White Noise", "sounds/whitenoise.ogg", 0.0f, false)
    5. `brownnoise` ("Brown Noise", "sounds/brownnoise.ogg", 0.0f, false)
    6. `campfire` ("Campfire", "sounds/campfire.ogg", 0.0f, false)

---

### 5. UI Architecture (`PomodoroSoundscapeScreen.kt`)
- Segmented Header: `[ Pomodoro Timer | Ambient Soundscape ]`
- **Pomodoro Tab**:
  - Circular animated canvas countdown dial with elapsed arc and remaining time.
  - Cycle indicator pills: `Cycle 1 of 4 ● ○ ○ ○`.
  - Large Primary Action: Start / Pause / Resume with Monk Mode Amber branding.
  - Secondary Actions: Skip Phase, Reset.
  - Quick Presets row: `25/5 Standard`, `50/10 Extended`, `90/20 Deep Work`.
  - Configuration Modal: Custom work/break sliders, auto-start toggles, linked profile picker.
- **Soundscape Tab**:
  - Master volume slider ($0\%$ to $100\%$) and Master Mute/Play toggle.
  - Sound stem cards (Rain, Waves, Forest, White Noise, Brown Noise, Campfire) with individual volume sliders.
  - Preset mix chips: `Deep Study` (Brown Noise 60%, Rain 40%), `Ocean Storm` (Waves 70%, Rain 50%), `Cabin Fire` (Campfire 80%, Forest 30%), `Reset All`.

---

## Verification & Safety Invariants
1. **Zero Battery Drain**: Native `SoundPool` avoids multi-thread decoding; chronometer notification avoids 1-second IPC wakeups; CPU sleeps safely during Doze.
2. **Deterministic Wakeup**: `AlarmManager.setExactAndAllowWhileIdle()` guarantees work/break transitions execute reliably with transition chimes even in deep Doze.
3. **No Strict Bypass**: Pomodoro breaks strictly disarm only the `linkedProfileId`, preserving all global limits and anti-tamper rules.
4. **Offline Only**: 100% local assets in APK, zero cloud egress.
5. **MediaSessionCompat Integration**: `FocusSoundService` maintains `MediaSessionCompat` to satisfy Android 14 FGS `mediaPlayback` requirements.
6. **Asynchronous SoundPool Loading**: Stems wait for `OnLoadCompleteListener` before playback to prevent silent load failures.
7. **Asset Non-Compression**: `androidResources { noCompress += listOf("ogg", "wav") }` prevents AGP from compressing sound assets, guaranteeing valid file descriptors via `openFd()`.
