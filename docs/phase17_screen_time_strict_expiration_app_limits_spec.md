# Phase 17 Specification: Device Screen Time Tracking, Multi-Day Strict Mode Expiration, and Active App Limit Lockout

## 1. Objective
Bring Monk Mode to full parity with the official Stay Focused experience:
1. Accurately measure and display real daily device screen time and per-app usage (e.g. YouTube 2h 05m).
2. Provide a reliable, prominent prompt for Android Usage Access (`PACKAGE_USAGE_STATS`).
3. Support extended Strict Mode blocking durations (`1, 2, 7, 15, 30, 45, 60, 75, 90 days`) and an exact Expiration Date & Time picker.
4. Support rapid app limit presets (`15 min`, `30 min`, `1 hr`, `2 hr`) and active in-foreground lockout when the limit expires.

## 2. Components & Architecture

### 2.1 UsageStatsTracker & Health Subsystem
- `UsageStatsTracker`:
  - `queryTotalDeviceScreenTimeMs(startTime, endTime)`: Calculates sum of foreground durations for all apps from `00:00:00` today.
  - `queryTopUsedApps(limit, startTime, endTime)`: Returns ordered list of `AppUsageStats(packageName, appName, usageMs, launchCount)`.
  - `queryPackageUsageToday(packageName)`: Returns foreground ms for a single package.
- `ProtectionCheckType.USAGE_ACCESS`:
  - Integrated into `ProtectionHealthChecker` and `ProtectionStatusEngine`.
  - Fix action triggers `Settings.ACTION_USAGE_ACCESS_SETTINGS`.

### 2.2 Strict Mode Expiration & Multi-Day Dialog
- `ArmStrictSessionDialog`:
  - Tab 1 / Section 1: Preset Durations
    - Hours: `1h`, `2h`, `4h`, `8h`, `12h`
    - Days: `1d`, `2d`, `7d`, `15d`, `30d`, `45d`, `60d`, `75d`, `90d`
  - Tab 2 / Section 2: Exact Expiration Date & Time Picker
    - Allows picking target Date (`Sat, Oct 3`, `Sun, Oct 4`, ...) and Time (`02:21 AM`).
    - Computes duration dynamically.
- `TimeFormatter`:
  - `formatRemainingTime(remainingMs: Long)`: Handles multi-day durations (`14d 06h 42m`).
  - `formatExpirationDateTime(epochMs: Long)`: Formats exact target end time (`EEE, MMM d 'at' hh:mm a`).

### 2.3 App Limits & Real-Time Lockout
- `AppLimitsScreen`:
  - Preset limit buttons: `15m`, `30m`, `1h`, `2h`, `Custom`.
  - Instant save to Room DB.
- `FocusAccessibilityService`:
  - Intercept immediately on package change if `currentDayUsageMs >= limitMs`.
  - Maintain a 5-second polling loop while a time-limited package is foregrounded to catch limit expiry during continuous sessions.
  - Show `BlockOverlayManager` with `BlockReason.LimitReached` and perform `GLOBAL_ACTION_HOME`.

## 3. Testing Strategy
- Unit tests for `UsageStatsTracker` (total screen time, top apps).
- Unit tests for `ProtectionStatusEngine` with `USAGE_ACCESS`.
- Unit tests for `TimeFormatter` multi-day formatting and expiration formatting.
- Unit tests for `InterceptionDecisionEngine` with limit threshold boundaries.
