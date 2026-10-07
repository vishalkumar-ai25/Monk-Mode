# ADR 015: Scoped Anti-Tamper Isolation for Third-Party Apps & Hardware-Monotonic Live Foreground Time Tracking

## Status
Accepted (Amended via Adversarial Review)

## Context
User reported two critical issues on Realme C65 5G (Android 14, realme UI 5.0):

1. **Third-Party App Accessibility Disruption**:
   - Monk Mode was interfering with other accessibility-based apps installed from the Play Store (`com.stayfocused` "Stay Focused" and `com.burockgames.timeclocker` "StayFree").
   - Root causes:
     - `selfAppNames` in `SettingsTamperInspector` contained `"stay focused"` and `"stayfocused"`, causing any settings screen or dialogue for the Play Store app `com.stayfocused` to be classified as tampering targeting Monk Mode (`referencesSelf == true`) and forcefully terminated (`GLOBAL_ACTION_BACK` + `GLOBAL_ACTION_HOME`).
     - `DANGEROUS_ACTIONS` included `"stop stay focused"`, `"turn off stay focused"`, `"disable stay focused"`, and `"use stay focused"`.
     - Class-name checks (`ACCESSIBILITY_TOKENS`) and text headers (`ACCESSIBILITY_HEADERS`) blocked all accessibility settings screens indiscriminately, preventing users from viewing or configuring StayFree or Stay Focused accessibility services.

2. **Time Limit Bypass & Tracking Inaccuracy**:
   - YouTube and Chrome exceeded their 30-minute daily limits (e.g. Chrome used for >90 minutes, YouTube >32 minutes) without being blocked.
   - Root causes:
     - While an app is in active foreground, Android emits zero `UsageEvents` until the activity pauses or flushes. Thus, the 5-second polling loop queried stale usage and never saw the limit being breached during continuous viewing.
     - `recordAppLaunch` wrote back stale `existing.currentDayUsageMs` rather than querying current usage.
     - `syncUsageWithDatabase` could overwrite higher in-memory recorded usage downward with delayed OS stats.
     - `FocusAccessibilityService` lacked top-level exception handling, causing any unhandled exception during event processing to crash the service, leading Android's `AccessibilityManagerService` to mark it as crashed (`mCrashedServices`) and ultimately disable it.

## Adversarial Review Resolutions

### 1. Third-Party App Isolation (`SettingsTamperInspector`)
- **Strip Third-Party Tokens**: `selfAppNames` is strictly restricted to `setOf("monk mode", "monkmode")`. Remove all `"stay focused"` variations from `selfAppNames` and `DANGEROUS_ACTIONS`.
- **Allow Accessibility Listing Navigation**: `AccessibilitySettings` and "Downloaded apps" listings are permitted so users can navigate to and configure StayFree and Stay Focused.
- **Deterministic App Details Protection**: To prevent race conditions on OEM ROMs where App Info window text is delayed (which would allow rapid 'Force stop' tapping before text recognition), App Info (`InstalledAppDetails`, `AppDetailsActivity`) remains deterministically blocked by class name alone during Strict Mode. In contrast, Accessibility Settings navigation and service detail pages are scoped to `referencesSelf == true` so third-party accessibility apps (StayFree, Stay Focused) remain fully configurable.
- **Direct Node Text Lookup & Content Change Handling**: Use `findAccessibilityNodeInfosByText` for fast lookup, and handle `TYPE_WINDOW_CONTENT_CHANGED` strictly for Settings/Installer packages to neutralize asynchronous UI rendering race conditions.

### 2. Live Foreground Time Tracking (`FocusAccessibilityService`)
- **Hardware-Monotonic Live Foreground Tracking**:
  - `currentForegroundPackage: String?`
  - `currentForegroundStartElapsed: Long` (via `SystemClock.elapsedRealtime()`)
  - `currentForegroundBaseUsageMs: Long`
- **Instant Launch Evaluation**:
  - When an app with a daily time limit is launched, evaluate immediately: `currentTotal = maxOf(cachedAppLimits[pkg]?.currentDayUsageMs ?: 0L, usageStatsTracker.queryPackageUsageToday(pkg))`.
  - If `currentTotal >= limitMs`, block immediately without allowing foreground entry.
- **Continuous 1-Second Polling Loop**:
  - In `foregroundMonitorJob`, poll every 1 second: `currentTotal = currentForegroundBaseUsageMs + (SystemClock.elapsedRealtime() - currentForegroundStartElapsed)`.
  - When `currentTotal >= limitMs`, immediately persist lockout to DB, issue `performGlobalAction(GLOBAL_ACTION_HOME)`, and display `BlockOverlayView`.
  - Commit incremental usage to Room DB every 15 seconds to guard against sudden crashes or reboots.
- **Display Lifecycle Integration (Screen-Off / Screen-On)**:
  - Register dynamic `BroadcastReceiver` for `ACTION_SCREEN_OFF` and `ACTION_USER_PRESENT` / `ACTION_SCREEN_ON`.
  - On `ACTION_SCREEN_OFF`: Pause monotonic tracking, commit accrued time to Room DB, and cancel `foregroundMonitorJob` to eliminate overnight quota exhaustion.
  - On `ACTION_USER_PRESENT` / `ACTION_SCREEN_ON`: Resume tracking if an app is in the foreground.
- **Midnight Rollover Handling**:
  - Detect date boundary changes in the monitor loop (`getStartOfToday() > currentSessionStartDay`), committing yesterday's slice and resetting today's base usage to 0.

### 3. Usage Stats Sync Hardening (`UsageStatsTracker`)
- **Preserve Highest Recorded Usage**: In `syncUsageWithDatabase`, update Room DB with `maxOf(limit.currentDayUsageMs, actualUsageMs)` so background OS syncs never roll back live monotonic usage.

### 4. Service Crash & Disconnection Hardening
- Wrap `onAccessibilityEvent` and `handleWindowEvent` in top-level `runCatching` blocks to guarantee zero unhandled exceptions reach `system_server`.
- Wrap all `AccessibilityNodeInfo` operations in defensive exception handlers for `DeadObjectException` and `IllegalStateException`.

## Consequences
- StayFree and Play Store Stay Focused accessibility services are completely unaffected by Monk Mode.
- App limits are enforced with 1-second precision during continuous usage sessions.
- Monotonic tracking is paused while the screen is off, preventing false lockouts.
- Service resilience against crashes and OEM disabling is maximized.
