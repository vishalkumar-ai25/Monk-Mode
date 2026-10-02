# ADR 006: Scheduled and Interactive Strict Mode Activation

## Status
Accepted (Hardened via Adversarial Security Review)

## Context and Problem Statement
Stay Focused (Monk Mode) provides distraction defense and anti-relapse mechanics backed by Room `StrictSessionEntity` and multi-tiered failsafe overrides.
Two core capabilities are required:
1. **Interactive On-Demand Arming Flow**: Users can arm a Strict Mode session on demand by selecting duration (e.g., 1h, 2h, 4h, until midnight, custom), the active Focus Profile to lock in, and a deactivation challenge (`EXPIRATION_ONLY`, `COOL_DOWN`, or `RANDOM_TEXT`).
2. **Recurring Focus Schedules**: Users can configure recurring weekly schedules (e.g., Mon–Fri from 09:00 to 17:00, or nightly from 22:00 to 06:00). When a scheduled window starts, Strict Mode automatically arms with the assigned Focus Profile. When the window ends, the session deactivates.

### Adversarial Findings Addressed:
1. **Permanent Lockout / Re-Arming Trap**: Solved via `dismissedUntilEpochMs` column on `strict_schedules`. When emergency recovery codes or deactivation challenges disarm an active window, that window is marked dismissed until its conclusion, preventing `WatchdogWorker` and `BootCompletedReceiver` from immediately re-locking.
2. **Overlapping Schedules & State Corruption**: Evaluated holistically via `StrictScheduleEngine.getActiveSchedules(now)` rather than an isolated scalar ID. Deactivation occurs only when all overlapping active windows have elapsed.
3. **Hardware Monotonic Anti-Clock-Tampering**: Stores `startElapsedRealtime: Long` alongside `startTime: Long` to detect manual clock roll-forward attempts. Registers `Intent.ACTION_TIME_CHANGED` and `Intent.ACTION_TIMEZONE_CHANGED` listeners.
4. **App Blocker Synchronization**: Activating a scheduled session atomically calls `FocusProfileDao.switchToProfile(schedule.profileId)` so `FocusAccessibilityService` immediately enforces package blocking.
5. **Exact Alarm Guard**: `canScheduleExactAlarms()` checked on API 31+ with safe `setWindow` fallback to eliminate `SecurityException`.
6. **Alarm Clamping**: Next boundary calculation clamped to `maxOf(now + 5_000L, nextBoundary)` to prevent infinite alarm loops.
7. **Anti-Paste Burst Detection for Random Text Challenge**: Rejects text input where typing latency is below human threshold (< 1,000ms for 60 chars) to prevent IME clipboard pasting.

## Decision Drivers
- **Anti-Tamper Invariant #1 (Immutable Active State)**: While any Strict Session is active (`isActive == true`), all schedule mutations (create, edit, delete, toggle enable/disable) are hard-rejected.
- **Defense-in-Depth Alarm Architecture**: Schedule boundary transitions driven by `AlarmManager.setExactAndAllowWhileIdle()` clamped to `now + 5_000L`, backed by 15-minute reconciliation in `WatchdogWorker` and `BootCompletedReceiver`.
- **Zero Cloud / 100% Offline Privacy**: Schedules and activation state reside purely in local Room database (`strict_schedules`). Zero telemetry.
- **Strictest Challenge Hierarchy**: On overlapping sessions, `EXPIRATION_ONLY` > `COOL_DOWN` > `RANDOM_TEXT`.

## Consequences
- Room schema migration from v4 to v5 (`strict_schedules` table with foreign keys and indices, plus `strict_sessions` enhancements).
- New domain engines: `StrictScheduleEngine`, `RandomTextChallengeEngine`.
- New broadcast receiver: `StrictScheduleReceiver` and scheduler `StrictScheduleScheduler`.
- Updated `WatchdogWorker`, `BootCompletedReceiver`, and `MainActivity`.
- Comprehensive JVM unit test suite covering day-of-week bitmasks, cross-midnight calculations, DST resilience, clock drift, and anti-tamper locks.
