# Tasks: Wave 1 Implementation (Theme, Protection Status Engine & Failsafe Integrity Log)

- [x] **Task 1: Theme & Visual Tokens**
- [x] **Task 2: Failsafe Integrity Log & Room Migration (Phase 15)**
- [x] **Task 3: Protection Status Engine & Watchdog Health Alerts (Phase 8)**
- [x] **Task 4: Full App Reskin & Quality Verification**

---

# Tasks: Wave 2 Implementation (Encrypted Settings Backup & Hardened Profile Switching)

- [x] **Task 1: Architecture & Specs**
- [x] **Task 2: Encrypted Settings Backup Engine (Phase 11 - TDD)**
- [x] **Task 3: Hardened Quick Profile Switching Engine (Phase 12 - TDD)**
- [x] **Task 4: UI Components & Dashboard Integration**
- [x] **Task 5: Verification & Quality Gate**
  - [x] Commit, merge to `main`, and push to `origin/main` (`f20f725`).

---

# Tasks: Wave 3 Implementation (Friction-Based Breaks & Weekly Reflection Digest)

- [x] **Task 1: Architecture & Specs**
  - [x] Write ADR-004: Friction-Based Break Justification and Weekly Reflection Engine (`docs/adr/004-friction-breaks-and-weekly-reflection.md`).
  - [x] Write `docs/phase9_friction_based_breaks_spec.md` & `tasks/dod/phase9_dod.md`.
  - [x] Write `docs/phase10_weekly_reflection_spec.md` & `tasks/dod/phase10_dod.md`.

- [x] **Task 2: Friction-Based Breaks Engine & Room Migration v3 -> v4 (Phase 9 - TDD)**
  - [x] Add `reason: String = ""` to `BreakSessionEntity.kt`.
  - [x] Bump `StayFocusedDatabase` to version `4` and implement `MIGRATION_3_4`.
  - [x] Generate/verify exported Room schema `4.json`.
  - [x] Update `StayFocusedDatabaseMigrationTest.kt` for v2 -> v3 -> v4 and v3 -> v4 data integrity.
  - [x] Update pure Kotlin `BreakDecisionEngine.kt` (`canStartBreak`, `createBreakSession` with `require(reason.isNotBlank())`).
  - [x] Update and pass `BreakDecisionEngineTest.kt`.
  - [x] Update `TakeABreakTileService.kt` to supply explicit reason `"Quick Settings Break"`.
  - [x] Update and pass `TakeABreakTileServiceTest.kt`.
  - [x] Update `TakeABreakCard.kt` to present friction prompt asking for reason before starting break and show strict lock state.
  - [x] Update `DashboardScreen.kt` to pass user's reason to break session creation and guard via `canStartBreak`.

- [x] **Task 3: Weekly Reflection Digest Engine & WorkManager Scheduling (Phase 10 - TDD)**
  - [x] Implement `domain/model/WeeklyReflectionModels.kt` (`AppUsageDrop`, `WeeklyReflectionDigest`).
  - [x] Implement pure Kotlin `WeeklyReflectionEngine.kt` (biggest drop, streak hours, digest formatting, next Sunday 21:00 calculation).
  - [x] Write and pass `WeeklyReflectionEngineTest.kt`.
  - [x] Add Room aggregation queries to `SuppressedNotificationDao`, `StrictSessionDao`, and `AppLimitDao` (with conditional subtraction for allowed launch limits).
  - [x] Write and pass `WeeklyReflectionDaoTest.kt`.
  - [x] Implement `WeeklyReflectionWorker.kt` (Room + UsageStats aggregation, bounded single IPC app label resolution, local notification).
  - [x] Implement `WeeklyReflectionScheduler.kt` (PeriodicWorkRequest to next Sunday 21:00).
  - [x] Write and pass `WeeklyReflectionWorkerTest.kt`.
  - [x] Re-arm reflection scheduling in `BootCompletedReceiver`, `WatchdogWorker`, and `MainActivity`.

- [x] **Task 4: Quality Gate & Adversarial Code Review**
  - [x] Run full unit test suite `./gradlew testDebugUnitTest` (100% pass).
  - [x] Assemble debug APK `./gradlew assembleDebug` (0 errors).
  - [x] Invoke adversarial code review (`code-reviewer`) and address all required findings.
  - [x] Commit, merge to `main`, and push to `origin/main` (`11f4f4a`).

---

# Tasks: Wave 4 Implementation (Shareable Focus Summary & Glance Home Screen Widget)

- [x] **Task 1: Architecture & Specs**
  - [x] Write ADR-005: On-Device Shareable Focus Summary Card and Glance Home Screen Widget (`docs/adr/005-shareable-focus-summary-and-glance-widget.md`).
  - [x] Write `docs/phase13_shareable_focus_summary_spec.md` & `tasks/dod/phase13_dod.md`.
  - [x] Write `docs/phase14_glance_widget_spec.md` & `tasks/dod/phase14_dod.md`.

- [x] **Task 2: Shareable Focus Summary Card (Phase 13 - TDD)**
  - [x] Add FileProvider paths XML `app/src/main/res/xml/file_paths.xml`.
  - [x] Register `androidx.core.content.FileProvider` in `app/src/main/AndroidManifest.xml`.
  - [x] Implement `domain/model/FocusSummaryModels.kt` (`DailyFocusSummaryData`).
  - [x] Implement `domain/FocusSummaryBitmapGenerator.kt` (Canvas 1080x1350 card renderer).
  - [x] Write and pass `FocusSummaryBitmapGeneratorTest.kt`.
  - [x] Implement `domain/FocusSummaryShareManager.kt` (cache writer & `Intent.ACTION_SEND` builder).
  - [x] Write and pass `FocusSummaryShareManagerTest.kt`.
  - [x] Add "Share Today" button to Daily Usage card in `DashboardScreen.kt`.

- [x] **Task 3: Home-Screen Glance Widget (Phase 14 - TDD)**
  - [x] Add Glance 1.1.1 dependencies to `gradle/libs.versions.toml` and `app/build.gradle.kts`.
  - [x] Add widget resources: `res/xml/focus_glance_widget_info.xml` and `res/layout/widget_loading.xml`.
  - [x] Register `FocusGlanceWidgetReceiver` in `AndroidManifest.xml`.
  - [x] Implement `domain/model/FocusWidgetData.kt`.
  - [x] Implement pure Kotlin `FocusWidgetDataEngine.kt`.
  - [x] Write and pass `FocusWidgetDataEngineTest.kt`.
  - [x] Implement `widget/FocusWidgetDialRenderer.kt` (Canvas dial renderer).
  - [x] Write and pass `FocusWidgetDialRendererTest.kt`.
  - [x] Implement `widget/FocusGlanceWidget.kt` and `widget/FocusGlanceWidgetReceiver.kt`.
  - [x] Write and pass `FocusGlanceWidgetTest.kt`.
  - [x] Wire widget refresh into `WatchdogWorker.kt` and `DashboardScreen.kt`.
  - [x] Update and pass `WatchdogWorkerTest.kt`.

- [x] **Task 4: Quality Gate, Code Review & Merge**
  - [x] Run full JVM unit test suite `./gradlew testDebugUnitTest` (100% pass - 202 tests).
  - [x] Assemble debug APK `./gradlew assembleDebug` (0 errors).
  - [x] Invoke adversarial code review (`code-reviewer`) and address all required findings (ClipData, off-thread Dispatchers.IO, suspending Watchdog worker update, widget 110dp layout, score depletion styling).
  - [x] Commit, merge to `main`, and push to `origin/main` (`451b0aa`).

## Phase 16: Scheduled & Interactive Strict Mode Activation

- [x] **Task 1: Room Database Schema Migration v4 -> v5 (TDD)**
  - [x] Implement `StrictScheduleEntity.kt` and `StrictScheduleDao.kt`.
  - [x] Update `StrictSessionEntity.kt` with `startElapsedRealtime: Long` and `deactivationChallenge: String`.
  - [x] Implement `MIGRATION_4_5` in `StayFocusedDatabase.kt` and bump database version to 5.
  - [x] Write and pass `StayFocusedDatabaseMigrationTest.kt` verifying v4 -> v5 migration.
  - [x] Write and pass `StrictScheduleDaoTest.kt`.

- [x] **Task 2: StrictScheduleEngine & RandomTextChallengeEngine Domain (TDD)**
  - [x] Implement `StrictScheduleEngine.kt` (same-day, cross-midnight, bitmask, overlap resolution, next boundary).
  - [x] Write and pass `StrictScheduleEngineTest.kt`.
  - [x] Implement `RandomTextChallengeEngine.kt` (quotes, verbatim validation, anti-paste timing).
  - [x] Write and pass `RandomTextChallengeEngineTest.kt`.

- [x] **Task 3: Alarm Scheduling, Broadcast Receiver & Watchdog Reconciliation**
  - [x] Implement `StrictScheduleScheduler.kt` with `canScheduleExactAlarms()` guard and safe fallback.
  - [x] Implement `StrictScheduleReceiver.kt` handling alarms, clock changes, and boot events.
  - [x] Register receiver in `AndroidManifest.xml` with `ACTION_TIME_CHANGED`, `ACTION_TIMEZONE_CHANGED`, `BOOT_COMPLETED`.
  - [x] Wire schedule reconciliation into `WatchdogWorker.kt` and `BootCompletedReceiver.kt`.
  - [x] Write and pass unit tests for scheduler, receiver, and watchdog reconciliation.

- [x] **Task 4: Interactive Strict Mode UI in StrictLockScreen**
  - [x] Build "Active Strict Mode" card with live countdown, active profile, and challenge disarm action.
  - [x] Build "Arm Strict Mode Now" button + `ArmStrictSessionDialog`.
  - [x] Build "Focus Schedules" card with day-of-week chips (M, T, W, T, F, S, S) and active state indicator.
  - [x] Build "+ Add Schedule" and Edit modal (`ScheduleConfigDialog`).
  - [x] Enforce anti-tamper lock: disable editing/deleting schedules when Strict Mode is active.
  - [x] Build `RandomTextChallengeDialog` for stoic phrase typing.

- [x] **Task 5: Verification, Code Review & On-Device Deployment**
  - [x] Run full JVM unit test suite `./gradlew testDebugUnitTest` (230 tests passing).
  - [x] Build debug APK `./gradlew assembleDebug` (0 errors).
  - [x] Code review via `code-reviewer` subagent and resolved all findings.
  - [x] Install on connected phone via ADB and verify live.


