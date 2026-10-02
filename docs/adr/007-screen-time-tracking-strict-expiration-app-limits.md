# ADR 007: Device Screen Time Tracking, Multi-Day Strict Mode Expiration, and Active App Limit Lockout

## Status
Accepted

## Context
User testing on real physical hardware (Realme RMX3997, Android 16 / SDK 36) revealed three essential user experience and protection requirements:
1. **Screen Time Tracking**: The dashboard was showing `0m of 2h 0m target` even though the user had active screen time (e.g. YouTube 2h 05m).
   - Root Cause A: `PACKAGE_USAGE_STATS` AppOps permission was not granted by the OS, and the app had no user-facing permission request flow or health check for it.
   - Root Cause B: Dashboard computed `totalUsedMinutes` by only summing `appLimits.sumOf { it.currentDayUsageMs }`. If no app limits were configured or updated, the dashboard reported 0m instead of true device screen time.
2. **Strict Mode Multi-Day Duration & Expiration Picker**: The existing dialog only offered short durations up to 8 hours. Users require blocking for extended commitments (`1, 2, 7, 15, 30, 45, 60, 75, 90 days`) and an exact Expiration Date & Time picker (matching the official Play Store Stay Focused UX).
3. **Per-App Usage Limits & Active Lockout**: Users require standard preset limits (`15 min`, `30 min`, `1 hr`, `2 hr`) per app. When an app reaches its time limit, it must be locked immediately even if kept open continuously.

## Decision

### 1. Usage Access & Screen Time Engine
- Add `ProtectionCheckType.USAGE_ACCESS` to `ProtectionHealthChecker` and `ProtectionStatusEngine`.
- Add `UsageStatsTracker.queryTotalDeviceScreenTimeMs()` and `UsageStatsTracker.queryTopUsedApps()` to query aggregate foreground time directly from `UsageStatsManager`.
- Add a prominent "Usage Access Required" banner on `DashboardScreen` that launches `Settings.ACTION_USAGE_ACCESS_SETTINGS`.
- Add a "Usage Overview" / "Most Used Apps Today" breakdown card on `DashboardScreen` displaying top apps, icons, and real-time screen time with a direct "Set Limit" action.

### 2. Multi-Day Strict Mode Expiration UX
- Enhance `ArmStrictSessionDialog` with:
  - Preset duration chips: Hours (`1h, 2h, 4h, 8h, 12h`) and Days (`1d, 2d, 7d, 15d, 30d, 45d, 60d, 75d, 90d`).
  - An exact "Expiration Date & Time" picker allowing the user to select target date and time.
- Upgrade `TimeFormatter` to format multi-day remaining times (`14d 06h 42m`) and formatted expiration timestamps (`Sat, Oct 3, 02:21 AM`).

### 3. App Limit Presets & Active Foreground Lockout
- In `AppLimitsScreen.kt`, provide one-tap preset limit chips (`15 min`, `30 min`, `1 hr`, `2 hr`, `Custom`).
- In `FocusAccessibilityService.kt`, query real-time package usage on package transition, and maintain a lightweight 5-second polling loop while a time-limited app remains in the foreground. If `usageMs >= limitMs`, trigger immediate `BlockReason.LimitReached` overlay and navigate Home.

## Consequences
- 100% offline, zero cloud, zero telemetry.
- Seamless alignment with official Play Store Stay Focused workflow while preserving strict anti-tamper guarantees.
