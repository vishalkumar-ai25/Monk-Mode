# Tasks: Wave 1 Implementation (Theme, Protection Status Engine & Failsafe Integrity Log)

- [x] **Task 1: Theme & Visual Tokens**
  - [x] Create `MonkModeTheme.kt` with palette tokens and Material 3 `MonkModeColorScheme`.
  - [x] Apply `MonkModeTheme` in `MainActivity.kt`.
  - [x] Reskin core components: `DailyUsageDial.kt`, `TakeABreakCard.kt`.

- [x] **Task 2: Failsafe Integrity Log & Room Migration (Phase 15)**
  - [x] Create `FailsafeLogEntity.kt` and `FailsafeEventType` enum.
  - [x] Create `FailsafeLogDao.kt` (append-only contract).
  - [x] Update `StayFocusedDatabase.kt` to version 3 with `MIGRATION_2_3`.
  - [x] Implement `StayFocusedDatabaseMigrationTest.kt` for v2 -> v3.
  - [x] Write `FailsafeLogDaoTest.kt`.
  - [x] Instrument `FailsafeManager.kt` with logging.
  - [x] Instrument `BootCompletedReceiver.kt` with boot grace logging.
  - [x] Create `FailsafeLogFormatter.kt` and `FailsafeLogFormatterTest.kt`.
  - [x] Create `FailsafeLogCard.kt` and embed into `StrictLockScreen.kt`.

- [x] **Task 3: Protection Status Engine & Watchdog Health Alerts (Phase 8)**
  - [x] Create `ProtectionModels.kt` domain models.
  - [x] Create pure Kotlin `ProtectionStatusEngine.kt`.
  - [x] Write unit tests in `ProtectionStatusEngineTest.kt`.
  - [x] Create `ProtectionPreferences.kt`.
  - [x] Create `ProtectionHealthChecker.kt`.
  - [x] Update `WatchdogWorker.kt` to evaluate health and post RED alert notification.
  - [x] Write `WatchdogWorkerTest.kt`.
  - [x] Create `ProtectionStatusCard.kt` and embed into `DashboardScreen.kt`.

- [x] **Task 4: Full App Reskin & Quality Verification**
  - [x] Reskin `AppLimitsScreen.kt`, `WebBlockerScreen.kt`, and `NotificationVaultScreen.kt`.
  - [x] Run full unit test suite `./gradlew testDebugUnitTest` (100% pass).
  - [x] Assemble debug APK `./gradlew assembleDebug` (Build successful).
  - [ ] Physical device verification (Device disconnected).
