# ADR 004: Friction-Based Break Justification and Weekly Reflection Engine

## Status
Accepted

## Context and Problem Statement
Stay Focused (Monk Mode) is designed around cognitive friction and deliberate digital habits.
Two key behavioral features were proposed in the community contribution (PR #1):
1. **Friction-Based Break Justification**: Instead of granting instant, impulsive 5–15 minute breaks at the tap of a button, require users to pause and articulate an intentional reason ("Why do you need a break?"). The reason is permanently audited in the database.
2. **Weekly Reflection Digest**: A weekly Sunday evening summary (at 21:00) notifying the user of total distractions blocked, the app with the largest screen time reduction vs. the previous week, and their longest Strict Mode streak.

However, integrating these features introduces architectural and database integrity challenges:
- `BreakSessionEntity` currently lacks a `reason` column. How do we evolve the Room database schema from version 3 to version 4 without risking data corruption, preserving schema history, and ensuring backward compatibility?
- How do we guarantee that the new break reasoning prompt can never be used to bypass an active Strict Mode session?
- How do we schedule the Sunday reflection digest efficiently, surviving device reboots and WorkManager rescheduling without battery drain or redundant background processes?
- How do we keep all usage comparisons and notification history 100% private, on-device, and zero-telemetry?

## Decision Drivers
- **Strict Mode Anti-Relapse Invariant**: Under no circumstance may any break (regardless of entered reason or duration) be authorized while an active Strict Mode session is in progress.
- **Database Schema Integrity**: The database schema must increment from `version = 3` to `version = 4`. A non-destructive `MIGRATION_3_4` must add the `reason` column with a non-null default empty string (`''`). Pre-existing schemas (`1.json`, `2.json`, `3.json`) must remain immutable.
- **Pure Domain Decoupling (TDD)**: Break validation rules and weekly digest computations must be housed in pure Kotlin classes (`BreakDecisionEngine` and `WeeklyReflectionEngine`), decoupled from the Android framework to enable deterministic, millisecond JVM testing.
- **100% Offline Privacy**: All usage analytics are aggregated locally from `UsageStatsTracker` and Room DAOs. Zero external network calls or cloud services are involved.
- **Battery-Conscious Background Scheduling**: The Sunday reflection worker must use WorkManager periodic scheduling with initial delay alignment to Sunday 21:00, re-armed safely via `BootCompletedReceiver` and `WatchdogWorker`.

## Considered Options
### 1. Break Justification Storage
- **Option A: In-memory or temporary dialog state**: Capture reason only in UI; do not store in DB.
  - *Cons*: Lost on process death; no audit trail for user reflection or weekly summaries.
- **Option B: Schema v3 -> v4 Room Migration with `reason` Column**:
  - *Pros*: Permanent relational audit trail; enables future reflection analytics on break reasons; fully compatible with existing Room entities.
  - *Cons*: Requires tested Room migration `MIGRATION_3_4`.
  - *Decision*: **Option B**.

### 2. Weekly Reflection Scheduling
- **Option A: Exact AlarmManager `AlarmManager.setExactAndAllowWhileIdle`**:
  - *Cons*: Requires `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM` permissions; battery intensive; fails if OEM kills alarms; overkill for non-critical notification digest.
- **Option B: WorkManager PeriodicWorkRequest (7 days interval) with Initial Delay**:
  - *Pros*: Compliant with Android battery guidelines; respects Doze mode; automatically managed by OS; easily re-armed on device boot and periodic watchdog runs.
  - *Decision*: **Option B**.

## Decision Outcome
Chosen architecture:
1. **Room Migration v3 -> v4**:
   - `BreakSessionEntity` gains `@ColumnInfo(name = "reason") val reason: String = ""`
   - `MIGRATION_3_4` executes:
     `ALTER TABLE break_sessions ADD COLUMN reason TEXT NOT NULL DEFAULT ''`
   - `StayFocusedDatabaseMigrationTest` verifies data preservation across v2 -> v3 -> v4 and directly v3 -> v4.
2. **Break Decision Engine Rules**:
   - `canStartBreak(reason: String, isStrictModeActive: Boolean)` returns `false` if `isStrictModeActive == true` OR `reason.isBlank()`.
   - `createBreakSession(currentTimeMs: Long, durationMinutes: Int, reason: String)` throws `IllegalArgumentException` if `reason.isBlank()`, trimming valid reasons.
   - Quick Settings Tile passes `"Quick Settings Break"` as the explicit non-empty reason.
3. **Weekly Reflection Engine & Worker**:
   - `WeeklyReflectionEngine` calculates biggest drop across apps in foreground screen time (last week vs this week) and longest continuous strict session in hours.
   - `WeeklyReflectionWorker` runs periodically, posts local notification to channel `stayfocused_reflection_channel` (`NOTIFICATION_ID = 2003`).
   - `WeeklyReflectionScheduler` computes next Sunday 21:00 epoch millis and enqueues periodic WorkManager job with policy `ExistingPeriodicWorkPolicy.KEEP`.
