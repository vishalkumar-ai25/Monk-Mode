# Feature Specification: Phase 16 — Scheduled & Interactive Strict Mode Activation

## 1. Context & Objectives
Stay Focused (Monk Mode) provides distraction defense and anti-relapse mechanics backed by Room `StrictSessionEntity` and multi-tiered failsafe overrides.
The objective of **Phase 16** is to deliver two complementary capabilities inspired by the official Stay Focused app while upholding our open-source, zero-telemetry, and anti-relapse standards:
1. **Interactive On-Demand Arming**: Users can arm a Strict Mode session on demand by selecting duration (e.g. 1h, 2h, 4h, until midnight, custom), the active Focus Profile to lock in, and a deactivation challenge (`EXPIRATION_ONLY`, `COOL_DOWN`, or `RANDOM_TEXT`).
2. **Recurring Focus Schedules**: Users can configure recurring weekly schedules (e.g. Mon–Fri from 09:00 to 17:00, or nightly from 22:00 to 06:00). When a scheduled window starts, Strict Mode automatically arms with the assigned Focus Profile. When the window ends, the session gracefully deactivates.
3. **Hardened Anti-Tamper & Security Invariants** (Post-Adversarial Audit):
   - **Dismissed Window State**: `dismissedUntilEpochMs` prevents `WatchdogWorker` and `BootCompletedReceiver` from entering an infinite re-arming loop if a user disarms via an emergency recovery code or valid challenge.
   - **Multi-Schedule Overlap**: Deactivation occurs only when all currently active schedule windows have elapsed.
   - **Clock Skew Protection**: `startElapsedRealtime: Long` monotonic baseline detects manual clock roll-forward attempts.
   - **Atomic App Blocker Synchronization**: Activating a schedule triggers `FocusProfileDao.switchToProfile()` so `FocusAccessibilityService` immediately blocks the configured apps.
   - **Exact Alarm Guard**: Safe fallback on API 31+/34+ if exact alarm capability is restricted.
   - **Anti-Paste Burst Detection**: `RandomTextChallengeEngine` measures character injection timing to reject instant IME clipboard paste.

---

## 2. Subsystem Boundaries & Anti-Tamper Isolation
- **`vpn/` package**: **NO changes**. DNS blocking continues to mirror active profile rules.
- **`service/` package**: **NO changes**. `FocusAccessibilityService` checks `FocusProfileEntity.isActive`.
- **`strict/` & `receiver/`**:
  - `StayFocusedDeviceAdminReceiver`: Preserved.
  - `StrictScheduleReceiver`: Broadcast receiver for exact schedule alarms, time changes, and boot completion.
  - `StrictScheduleScheduler`: Helper calculating next boundary epoch and registering exact alarms with `AlarmManager`.
- **Data Layer (`data/local/`)**:
  - Room migration v4 -> v5.
  - Table `strict_schedules`:
    - `id: Long = 0` (PrimaryKey, autoGenerate = true)
    - `name: String`
    - `daysOfWeekMask: Int` (bits 0-6: Mon=1, Tue=2, Wed=4, Thu=8, Fri=16, Sat=32, Sun=64)
    - `startMinuteOfDay: Int` (0..1439)
    - `endMinuteOfDay: Int` (0..1439)
    - `profileId: Long` (ForeignKey to focus_profiles.id, onDelete = CASCADE)
    - `deactivationChallenge: String` (EXPIRATION_ONLY, COOL_DOWN, RANDOM_TEXT)
    - `isEnabled: Boolean = true`
    - `dismissedUntilEpochMs: Long = 0L`
    - `createdAt: Long`
  - Table `strict_sessions`:
    - `startElapsedRealtime: Long = 0L` (monotonic hardware timestamp)
    - `deactivationChallenge: String = "EXPIRATION_ONLY"`
  - DAO: `StrictScheduleDao`
- **Domain Layer (`domain/`)**:
  - `StrictScheduleEngine`: Pure Kotlin engine evaluating active windows, cross-midnight logic, overlap resolution, next alarm calculation, and dismissal validation.
  - `RandomTextChallengeEngine`: Curated quotes repository, verbatim text matching, and anti-paste timing validation.
- **UI Layer (`ui/`)**:
  - `StrictLockScreen.kt`:
    - "Active Strict Session" card with remaining countdown, active profile, and deactivation options.
    - "Arm Strict Mode" button launching `ArmStrictSessionDialog`.
    - "Focus Schedules" card listing schedules with day chips (M, T, W, T, F, S, S), start/end times, and toggle switches.
    - `ScheduleConfigDialog`: Add/Edit schedule modal.
    - `RandomTextChallengeDialog`: Modal showing challenge phrase and verbatim typing field.

---

## 3. Room Schema v5 SQL Migration

```sql
CREATE TABLE IF NOT EXISTS `strict_schedules` (
    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    `name` TEXT NOT NULL,
    `daysOfWeekMask` INTEGER NOT NULL,
    `startMinuteOfDay` INTEGER NOT NULL,
    `endMinuteOfDay` INTEGER NOT NULL,
    `profileId` INTEGER NOT NULL,
    `deactivationChallenge` TEXT NOT NULL,
    `isEnabled` INTEGER NOT NULL,
    `dismissedUntilEpochMs` INTEGER NOT NULL,
    `createdAt` INTEGER NOT NULL,
    FOREIGN KEY(`profileId`) REFERENCES `focus_profiles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS `index_strict_schedules_profileId` ON `strict_schedules` (`profileId`);

ALTER TABLE `strict_sessions` ADD COLUMN `startElapsedRealtime` INTEGER NOT NULL DEFAULT 0;
ALTER TABLE `strict_sessions` ADD COLUMN `deactivationChallenge` TEXT NOT NULL DEFAULT 'EXPIRATION_ONLY';
```

---

## 4. Definition of Done
1. Room migration test v4 -> v5 passes in `StayFocusedDatabaseMigrationTest`.
2. `StrictScheduleEngineTest` thoroughly tests day masks, same-day windows, cross-midnight windows, next alarm calculations, and dismissed window overrides.
3. `RandomTextChallengeEngineTest` tests quotes, verbatim matching, and anti-paste detection.
4. `StrictScheduleScheduler` and `StrictScheduleReceiver` handle alarms, clock changes, and boot events.
5. `WatchdogWorker` reconciles active schedules every 15 minutes.
6. Full Compose UI integrated into `StrictLockScreen`.
7. All 200+ unit tests pass, and app builds cleanly.
