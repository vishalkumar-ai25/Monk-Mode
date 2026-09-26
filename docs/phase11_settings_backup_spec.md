# Feature Specification: Phase 11 — Local Encrypted Settings Export/Import

## 1. Context & Objectives
In Monk Mode (Stay Focused), users invest significant effort into curating application limits, focus profiles with rule associations, and web filtering domain blocklists.
The objective of **Feature #3 (Local encrypted settings export/import)** is to provide a privacy-preserving, local-only backup and restore capability:
1. **Local & Cloudless**: Uses the Android Storage Access Framework (SAF) to write and read an encrypted backup file chosen by the user. Zero cloud, zero network permissions, zero external servers.
2. **Encrypted at Rest**: Uses authenticated encryption (`AES-256-GCM` with a key derived from a user passphrase using `PBKDF2WithHmacSHA256` and a cryptographically secure random 16-byte salt and 12-byte IV). Wrong password or tampered files fail authentication.
3. **Anti-Tamper & Strict Mode Isolation (Anti-Bypass Guard)**:
   - **Deliberate Exclusion**: The backup **strictly excludes** recovery codes (`recovery_codes` table / `RecoveryCodeEntity`) and active/historical Strict Mode sessions (`strict_sessions` table / `StrictSessionEntity`). A backup cannot be exported or imported to reveal recovery codes or bypass lock durations.
   - **Strict Mode Import Guard**: If Strict Mode is currently active (`StrictSessionDao.getActiveStrictSessionSync() != null`), importing settings is **strictly rejected** (`SettingsImportResult.BlockedByStrictMode`). This prevents users from importing a permissive backup to unblock apps or websites while in Strict Mode.
   - **Quota Tamper Protection**: When importing `AppLimitEntity`, the user cannot gain additional screen time today; local `currentDayUsageMs` and `currentDayLaunches` are preserved for existing limits rather than reset to zero.

---

## 2. Subsystem Boundaries & Anti-Tamper Isolation
- **`vpn/` package**: **NO changes**. `DnsVpnService` queries `BlockedDomainDao` as usual; updates to the database naturally reflect in web filtering without service alterations.
- **`service/` package**: **NO changes**. `FocusAccessibilityService` checks `AppLimitDao`; updates in Room are read on demand.
- **`strict/` & `receiver/`**: **NO changes**. `StayFocusedDeviceAdminReceiver` remains completely untouched.
- **Data Layer (`data/local/`)**:
  - `FocusProfileDao`: Adds `getAllProfilesWithRulesSync(): List<FocusProfileWithRules>` and `getAllProfilesSync(): List<FocusProfileEntity>`.
  - **Zero schema changes & zero new entities**: Exports and imports existing tables (`app_limits`, `blocked_domains`, `focus_profiles`, `profile_blocked_packages`, `profile_blocked_domains`).
- **Domain Layer (`domain/`)**:
  - `SettingsCryptoEngine`: Pure Kotlin crypto (AES-GCM, PBKDF2) and JSON serialization.
  - `domain/model/SettingsBackupModels.kt`: Data contracts for backup payloads and import results.
  - `SettingsBackupManager`: Coordinates validation, strict mode guards, and Room synchronization.
- **UI Layer (`ui/`)**:
  - Export and Import password prompts with SAF file picker launchers (`CreateDocument` / `OpenDocument`).

---

## 3. Data Model & Encryption Container

### Plaintext JSON Structure:
```json
{
  "version": 1,
  "exportedAt": 1727330000000,
  "appLimits": [
    {
      "packageName": "com.instagram.android",
      "appName": "Instagram",
      "dailyTimeLimitMinutes": 30,
      "dailyLaunchLimit": 5,
      "isBlocked": false
    }
  ],
  "blockedDomains": [
    {
      "domain": "reddit.com",
      "isBlocked": true,
      "category": "social"
    }
  ],
  "profiles": [
    {
      "id": 1,
      "name": "Work Deep Focus",
      "isStrictMode": false,
      "scheduleStartTime": "09:00",
      "scheduleEndTime": "17:00",
      "activeDaysMask": 31,
      "blockedPackages": ["com.instagram.android"],
      "blockedDomains": ["reddit.com"]
    }
  ]
}
```

### Encrypted Envelope Format:
```json
{
  "format": "monk_mode_encrypted_backup",
  "version": 1,
  "salt": "<Base64 encoded 16-byte salt>",
  "iv": "<Base64 encoded 12-byte nonce>",
  "ciphertext": "<Base64 encoded AES-GCM ciphertext + 128-bit authentication tag>"
}
```

---

## 4. Edge Cases & Anti-Tamper Safeguards
1. **Attempting Import during Strict Mode**: Must return `SettingsImportResult.BlockedByStrictMode` and abort transaction without writing anything to Room.
2. **Incorrect Passphrase / Corrupted Backup**: AES-GCM tag verification fails, throwing `AEADBadTagException` caught and mapped to `SettingsImportResult.DecryptionFailed`.
3. **Invalid File / Non-Monk Format**: Header validation rejects missing or unexpected `format` magic string.
4. **App Limit Quota Tamper**: Existing `currentDayUsageMs` is retained so importing an old backup doesn't reset today's consumed screen time.
5. **No Network**: Entire workflow operates on local storage through Android ContentResolver streams.

---

## 5. Affected Files
1. `docs/phase11_settings_backup_spec.md` (this spec)
2. `tasks/todo.md` (Phase 11 task breakdown)
3. `tasks/dod/phase11_dod.md` (DoD verification checklist)
4. `app/src/main/java/com/stayfocused/app/domain/model/SettingsBackupModels.kt` (contracts & models)
5. `app/src/main/java/com/stayfocused/app/domain/SettingsCryptoEngine.kt` (AES-GCM encryption & serialization)
6. `app/src/test/java/com/stayfocused/app/domain/SettingsCryptoEngineTest.kt` (pure Kotlin TDD test suite)
7. `app/src/main/java/com/stayfocused/app/data/local/dao/FocusProfileDao.kt` (sync profile with rules queries)
8. `app/src/main/java/com/stayfocused/app/domain/SettingsBackupManager.kt` (export, import, anti-tamper strict guard)
9. `app/src/test/java/com/stayfocused/app/domain/SettingsBackupManagerTest.kt` (Room integration test suite)
10. `app/src/main/java/com/stayfocused/app/ui/components/BackupRestoreDialogs.kt` (password entry Compose dialogs)
11. `app/src/main/java/com/stayfocused/app/ui/screens/DashboardScreen.kt` (backup & restore trigger buttons)
