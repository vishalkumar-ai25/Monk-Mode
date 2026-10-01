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

