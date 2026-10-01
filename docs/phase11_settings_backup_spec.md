# Feature Specification: Phase 11 — Local Encrypted Settings Export/Import

## 1. Context & Objectives
In Monk Mode (Stay Focused), users curate custom application limits, focus profiles with rule associations, and web filtering domain blocklists.
The objective of **Phase 11 (Local encrypted settings export/import)** is to provide a privacy-preserving, local-only backup and restore capability:
1. **Local & Cloudless**: Uses the Android Storage Access Framework (SAF) to write and read an encrypted backup file chosen by the user. Zero cloud, zero network permissions, zero external servers.
2. **Encrypted at Rest with AAD**: Uses authenticated encryption (`AES-256-GCM` with a 256-bit key derived from a user passphrase using `PBKDF2WithHmacSHA256` with 100,000 iterations, a cryptographically secure random 16-byte salt, and 12-byte IV). Authenticated Associated Data (AAD) cryptographically binds the envelope format and schema version (`monk_mode_encrypted_backup:v1`) directly to the GCM tag. Wrong password or tampered headers/ciphertext fail authentication cleanly.
3. **Anti-Tamper & Strict Mode Isolation (Anti-Bypass Guard)**:
   - **Deliberate Exclusion**: The backup **strictly excludes** recovery codes (`recovery_codes` table) and active/historical Strict Mode sessions (`strict_sessions` table) and audit logs (`failsafe_logs`). A backup cannot be exported or imported to reveal recovery codes, bypass lock durations, or rewrite audit trails.
   - **Atomic Strict Mode Import Guard**: Inside a Room transaction, if Strict Mode is currently active (`strictSessionDao.getActiveStrictSessionSync() != null`), importing settings is **strictly rejected** (`SettingsImportResult.BlockedByStrictMode`). This prevents users from importing a permissive backup to unblock apps or websites while in Strict Mode (TOCTOU race-condition proof).
   - **Quota Tamper Protection**: When importing `AppLimitEntity`, the user cannot gain additional screen time today; local `currentDayUsageMs` and `currentDayLaunches` are preserved for existing limits rather than reset to zero.
   - **Scoped Profile Rule Mutation**: Profile rules are deleted strictly scoped to the imported profile IDs, preventing rule erasure on untouched profiles.

---

## 2. Subsystem Boundaries & Anti-Tamper Isolation
- **`vpn/` package**: **NO changes**. `DnsVpnService` queries `BlockedDomainDao` as usual.
- **`service/` package**: **NO changes**. `FocusAccessibilityService` checks `AppLimitDao`.
- **`strict/` & `receiver/`**: **NO changes**. `StayFocusedDeviceAdminReceiver` remains completely untouched.
- **Data Layer (`data/local/`)**:
  - `FocusProfileDao`:
    - `getAllProfilesWithRulesSync(): List<FocusProfileWithRules>`
    - `getAllProfilesSync(): List<FocusProfileEntity>`
    - `deleteBlockedPackagesForProfiles(profileIds: List<Long>): Unit`
    - `deleteBlockedDomainsForProfiles(profileIds: List<Long>): Unit`
  - **Zero database schema changes & zero version bumps**: Operates on existing tables at version 3 (`app_limits`, `blocked_domains`, `focus_profiles`, `profile_blocked_packages`, `profile_blocked_domains`).
- **Domain Layer (`domain/`)**:
  - `SettingsCryptoEngine`: Pure Kotlin crypto (AES-GCM, PBKDF2 with AAD) and JSON serialization.
  - `domain/model/SettingsBackupModels.kt`: Data contracts for backup payloads and import results.
  - `SettingsBackupManager`: Coordinates validation, strict mode guards, and Room synchronization.
- **UI Layer (`ui/`)**:
  - `BackupRestoreDialogs`: Export and Import password prompts with SAF file picker launchers (`CreateDocument` / `OpenDocument`).

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
2. **Incorrect Passphrase / Corrupted Backup**: AES-GCM tag verification fails, throwing `AEADBadTagException` caught defensively and mapped to `SettingsImportResult.DecryptionFailed`.
3. **Invalid File / Non-Monk Format / Tampered Version**: Header validation rejects missing or unexpected format magic string or version mismatch via AAD verification.
4. **App Limit Quota Tamper**: Existing `currentDayUsageMs` is retained so importing an old backup doesn't reset today's consumed screen time.
5. **No Network**: Entire workflow operates on local storage through Android ContentResolver streams.

---

## 5. Success Criteria
- [ ] 100% of crypto logic (AES-GCM encryption, decryption, AAD, PBKDF2) unit tested in pure Kotlin (`SettingsCryptoEngineTest`).
- [ ] Room integration test (`SettingsBackupManagerTest`) verifies:
  - Valid export and import round-trip restores limits, domains, and profiles.
  - Active strict session aborts import with `BlockedByStrictMode`.
  - Quota `currentDayUsageMs` preserved across import.
- [ ] UI integration provides smooth SAF file creation and file selection.
