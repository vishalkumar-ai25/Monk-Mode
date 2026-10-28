# ADR 022: Persistent Focus Shield Foreground Service and Recents Task Removal Survival

## Status
Accepted

## Context
When users swipe the "Monk Mode" application (`com.stayfocused.app`) out of the Recent Apps switcher (task removal) on Android, especially on OEM skins such as Realme UI / ColorOS (Realme RMX3997), Xiaomi MIUI/HyperOS, and Samsung One UI, the app loses its working state:
1. **Process Kill on Task Removal**:
   - Android's `ActivityManagerService` cleans up tasks when swiped away from Recents.
   - If an application process does not host an active Foreground Service (with `startForeground(...)` and an ongoing user notification), the operating system terminates the process (`com.stayfocused.app`).
   - On OEM skins with aggressive background managers (e.g. ColorOS "Athena"), task removal is treated as a forced stop, killing all process components and severing the binder to `FocusAccessibilityService`.
2. **Accessibility Service Removal by OS**:
   - When an application's process hosting an Accessibility Service dies abruptly upon task swiping, Android's `AccessibilityManagerService` removes the dead service from `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`.
   - The user then observes that Monk Mode is no longer intercepting foreground apps, app limits are not enforced, settings anti-tamper is dead, and the accessibility permission appears disabled in system Settings.

## Decision
To ensure unwavering persistence across task swiping, back-press dismissals, and OEM task-killer sweeps, we introduce an always-on, high-priority **`FocusShieldService`** Foreground Service accompanied by task-retention declarations across all background enforcers:

### 1. Dedicated `FocusShieldService`
- **Class**: `com.stayfocused.app.service.FocusShieldService : android.app.Service`
- **Foreground Service Type**: `specialUse` (conforming to Android 14+ FGS requirements, bound to the declared `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`: *"Digital wellbeing continuous focus and anti-distraction session enforcement"*).
- **Sticky Restart**: Returns `START_STICKY` from `onStartCommand()`.
- **Ongoing Notification**:
  - Notification Channel: `monk_mode_shield` (`NotificationManager.IMPORTANCE_LOW`, silent, non-intrusive).
  - Flags: `NotificationCompat.FLAG_ONGOING_EVENT`, `setOngoing(true)`.
  - Icon: `android.R.drawable.ic_lock_lock`.
  - Content: Dynamic title & status (e.g. *"Monk Mode Active • Focus Shields Armed"* or active strict session countdown).
  - Tap Action: Deep link `PendingIntent` launching `MainActivity`.
- **Process State Elevation**:
  - Hosting `FocusShieldService` within `com.stayfocused.app` elevates the process to `PROC_STATE_FOREGROUND_SERVICE`.
  - Android explicitly preserves foreground-service processes when their top activities are removed from Recents.

### 2. Task Removal Defense (`onTaskRemoved`)
- Both `FocusShieldService` and `FocusAccessibilityService` override `onTaskRemoved(rootIntent: Intent?)`.
- Rather than shutting down or cancelling scopes, they re-arm foreground state:
  ```kotlin
  override fun onTaskRemoved(rootIntent: Intent?) {
      super.onTaskRemoved(rootIntent)
      Log.i(TAG, "Activity task swiped from Recents; maintaining persistent FocusShieldService.")
      FocusShieldService.start(applicationContext)
  }
  ```

### 3. Manifest Hardening (`stopWithTask="false"`)
Explicitly declare `android:stopWithTask="false"` across:
- `com.stayfocused.app.service.FocusShieldService`
- `com.stayfocused.app.service.FocusAccessibilityService`
- `com.stayfocused.app.vpn.DnsVpnService`

### 4. Lifecycle Binding & Liveness Assurance
- `MainActivity.onCreate()`: Automatically starts `FocusShieldService.start(applicationContext)`.
- `FocusAccessibilityService.onCreate()` & `onServiceConnected()`: Validates and starts `FocusShieldService`.
- `BootCompletedReceiver`: Autostarts `FocusShieldService.start(context)` on system boot and package update.
- `WatchdogWorker`: In its 15-minute periodic execution, verifies `FocusShieldService.isRunning` and re-starts if ever displaced by memory pressure.

## Consequences
- **Positive**:
  - Swiping Monk Mode out of Recents no longer kills the background process or drops `FocusAccessibilityService`.
  - App limits, website blocking, short-form feed blocking, and strict anti-tamper remain 100% active at all times.
  - Transparent notification gives users confidence that Monk Mode is guarding their focus without obtrusive sounds.
- **Negative / Trade-offs**:
  - Persistent ongoing notification icon in the Android status bar (required by Android platform policy for all foreground services).
