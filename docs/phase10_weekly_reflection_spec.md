# Feature Specification: Phase 10 — Weekly Reflection Digest

## 1. Context & Objectives
In Stay Focused (Monk Mode), user progress and mindful reflection are reinforced by Sunday-evening summaries.
The objective of **Feature #2 (Weekly reflection digest)** is to deliver a Sunday-night local notification summarizing the week's focus performance:
1. **Total Blocked Attempts**: Aggregated count of blocked app launches and suppressed distracting notifications during the week.
2. **Biggest Drop vs Last Week**: Identifies the application that achieved the largest reduction in foreground screen time compared to the previous week (e.g. "Instagram (-2h 15m)").
3. **Longest Strict-Mode Streak**: Longest consecutive streak (or longest uninterrupted session) in hours/days under Strict Mode.
4. **Local & Private**: Pure Room aggregation + UsageStatsTracker comparison, zero new entities, zero cloud/network access.

---

## 2. Subsystem Boundaries & Anti-Tamper Isolation
- **`vpn/` package**: **NO changes**.
- **`service/` package**: **NO changes**.
- **`strict/` & `receiver/`**: **NO changes**.
- **Data Layer (`data/local/dao/`)**:
  - `SuppressedNotificationDao`: Gains aggregation query `getSuppressedCountSince(startTime: Long): Int`.
  - `StrictSessionDao`: Gains aggregation query `getSessionsSince(startTime: Long): List<StrictSessionEntity>`.
  - `AppLimitDao`: Gains aggregation query `getBlockedAppLaunchesCount(): Int`.
  - **Zero new entities**: Utilizes existing tables (`suppressed_notifications`, `strict_sessions`, `app_limits`).
- **Domain Layer (`domain/`)**: Pure Kotlin `WeeklyReflectionEngine` and domain models `WeeklyReflectionDigest`, `AppUsageDrop`.
- **Worker / Scheduler (`worker/`)**: `WeeklyReflectionWorker` and `WeeklyReflectionScheduler` for scheduling Sunday-night notification execution at 21:00.

---

## 3. Data & Aggregation Matrix

### 1. Total Blocked Attempts
`totalBlocked = countOfSuppressedNotifications(since Monday 00:00) + sumOfBlockedAppLaunches`
- `SuppressedNotificationDao.getSuppressedCountSince(startOfWeek)`
- `AppLimitDao.getBlockedAppLaunchesCount()`

### 2. Biggest Drop vs Last Week
- `usageThisWeek`: `UsageStatsTracker.queryForegroundUsage(startOfWeek, now)`
- `usageLastWeek`: `UsageStatsTracker.queryForegroundUsage(startOfLastWeek, startOfWeek)`
- For each app package present in `usageLastWeek`:
  `dropMs = usageLastWeek[pkg] - usageThisWeek.getOrDefault(pkg, 0L)`
- The app with maximum `dropMs > 0` is the "biggest drop":
  e.g., `AppUsageDrop(packageName = "com.instagram.android", appName = "Instagram", dropMinutes = 135)`.
- If no drops or no last-week data: `null` (or "Keep building your focus habits").

### 3. Longest Strict-Mode Streak
- Queried from `StrictSessionDao.getSessionsSince(startOfWeek)` (or all sessions).
- Evaluates longest continuous session duration (`targetEndTime - startTime`) or consecutive day streak in hours.
- Formatted as `"${hours}h"`.

---

## 4. Sunday-Night Scheduling SLA
- Calculated at Sunday 21:00 (9:00 PM) in the user's local `ZoneId`.
- Scheduled via WorkManager `PeriodicWorkRequestBuilder` with initial delay to next Sunday 21:00.
- Notification posted with ID `2003` on `stayfocused_reflection_channel`.
- Tap target launches `MainActivity` to view Dashboard.

---

## 5. Affected Files
1. `app/src/main/java/com/stayfocused/app/domain/model/WeeklyReflectionModels.kt` (domain data classes)
2. `app/src/main/java/com/stayfocused/app/domain/WeeklyReflectionEngine.kt` (pure Kotlin logic & calculation)
3. `app/src/test/java/com/stayfocused/app/domain/WeeklyReflectionEngineTest.kt` (unit tests)
4. `app/src/main/java/com/stayfocused/app/data/local/dao/SuppressedNotificationDao.kt` (aggregation query)
5. `app/src/main/java/com/stayfocused/app/data/local/dao/StrictSessionDao.kt` (aggregation query)
6. `app/src/main/java/com/stayfocused/app/data/local/dao/AppLimitDao.kt` (aggregation query)
7. `app/src/main/java/com/stayfocused/app/worker/WeeklyReflectionWorker.kt` (WorkManager worker)
8. `app/src/main/java/com/stayfocused/app/worker/WeeklyReflectionScheduler.kt` (scheduler helper)
9. `app/src/test/java/com/stayfocused/app/worker/WeeklyReflectionWorkerTest.kt` (unit tests)
