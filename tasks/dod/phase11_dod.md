# Definition of Done: Phase 11 — Local Encrypted Settings Export/Import

## 1. Cryptographic Correctness & Integrity
- [ ] `SettingsCryptoEngine` uses `AES-256-GCM` with a cryptographically secure 16-byte random salt and 12-byte IV.
- [ ] PBKDF2WithHmacSHA256 uses 100,000 iterations to derive a 256-bit AES key.
- [ ] Authenticated Associated Data (AAD) is bound to the GCM cipher (`monk_mode_encrypted_backup:v1`).
- [ ] Tampered ciphertext, bad password, corrupted salt/IV, or header alterations throw `AEADBadTagException` and map to `SettingsImportResult.DecryptionFailed`.
- [ ] Pure Kotlin unit tests in `SettingsCryptoEngineTest` pass 100%.

## 2. Anti-Tamper & Strict Mode Guards
- [ ] Export payload strictly excludes `recovery_codes`, `strict_sessions`, and `failsafe_logs`.
- [ ] If Strict Mode is currently active in `strict_sessions`, import returns `SettingsImportResult.BlockedByStrictMode` inside a Room transaction and does not write to the database.
- [ ] Importing `AppLimitEntity` preserves existing `currentDayUsageMs` and `currentDayLaunches` to prevent quota reset cheating.
- [ ] Room integration tests in `SettingsBackupManagerTest` pass 100%.

## 3. UI & Usability
- [ ] `BackupRestoreDialogs` renders password inputs with confirmation and error handling.
- [ ] SAF `CreateDocument` and `OpenDocument` activity result launchers integrated in `DashboardScreen`.
- [ ] Themed with Monk Mode design tokens.

## 4. Verification
- [ ] `./gradlew testDebugUnitTest` passes 100%.
- [ ] `./gradlew assembleDebug` builds successfully.
