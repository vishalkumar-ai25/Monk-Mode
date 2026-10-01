# Tasks: Wave 1 Implementation (Theme, Protection Status Engine & Failsafe Integrity Log)

- [x] **Task 1: Theme & Visual Tokens**
- [x] **Task 2: Failsafe Integrity Log & Room Migration (Phase 15)**
- [x] **Task 3: Protection Status Engine & Watchdog Health Alerts (Phase 8)**
- [x] **Task 4: Full App Reskin & Quality Verification**

---

# Tasks: Wave 2 Implementation (Encrypted Settings Backup & Hardened Profile Switching)

- [x] **Task 1: Architecture & Specs**
  - [x] Write ADR-003: Encrypted Settings Backup and Hardened Profile Switching Invariants (`docs/adr/003-encrypted-settings-backup-and-profile-switching.md`).
  - [x] Conduct Doubt-Driven adversarial security audit on strict mode bypass & crypto vectors.
  - [x] Write `docs/phase11_settings_backup_spec.md` & `tasks/dod/phase11_dod.md`.
  - [x] Write `docs/phase12_quick_profile_switching_spec.md` & `tasks/dod/phase12_dod.md`.

- [x] **Task 2: Encrypted Settings Backup Engine (Phase 11 - TDD)**
  - [x] Implement `domain/model/SettingsBackupModels.kt` (export DTOs and import result types).
  - [x] Implement pure Kotlin `SettingsCryptoEngine.kt` (AES-256-GCM, PBKDF2 100k, AAD binding, defensive exception mapping).
  - [x] Write and pass `SettingsCryptoEngineTest.kt` (key derivation, round-trip encrypt/decrypt, bad password, tampered AAD/ciphertext).
  - [x] Add synchronous and scoped profile helper methods to `FocusProfileDao.kt`.
  - [x] Implement `SettingsBackupManager.kt` with Room transaction, strict mode check, and quota preservation.
  - [x] Write and pass `SettingsBackupManagerTest.kt` (Room database integration test).

- [x] **Task 3: Hardened Quick Profile Switching Engine (Phase 12 - TDD)**
  - [x] Implement `domain/model/ProfileSwitchModels.kt` (`AlreadyActive`, `BlockedByStrictMode`, `ImmediateSwitch`).
  - [x] Implement pure Kotlin `ProfileSwitchDecisionEngine.kt` (enforcing strict session lockout).
  - [x] Implement `ProfileSwitchManager.kt` for atomic transactional profile switches.
  - [x] Write and pass `ProfileSwitchDecisionEngineTest.kt` & `ProfileSwitchManagerTest.kt`.
  - [x] Add atomic `switchToProfile(targetId: Long)` single-query update to `FocusProfileDao.kt`.
  - [x] Write and pass `FocusProfileSwitchDaoTest.kt`.

- [x] **Task 4: UI Components & Dashboard Integration**
  - [x] Implement `QuickProfileChipRow.kt` with Monk Mode tokens, TalkBack accessibility semantics, and Strict Mode rejection alert.
  - [x] Implement `BackupRestoreDialogs.kt` with Monk Mode styled password inputs, keyboard hygiene, and error banners.
  - [x] Integrate Profile Chip Row and Backup/Restore SAF launchers into `DashboardScreen.kt` with OOM bounds and activity recreation resilience.

- [x] **Task 5: Verification & Quality Gate**
  - [x] Run full unit test suite `./gradlew testDebugUnitTest` (100% pass).
  - [x] Assemble debug APK `./gradlew assembleDebug` (0 errors).
  - [x] Adversarial Code Review via `code-reviewer` subagent and all findings addressed.
  - [ ] Commit, merge to `main`, and push to `origin/main`.
