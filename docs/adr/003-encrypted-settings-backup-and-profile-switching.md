# ADR 003: Encrypted Settings Backup and Hardened Profile Switching Invariants

## Status
Accepted

## Context and Problem Statement
In Stay Focused (Monk Mode), users spend significant effort setting up focus configurations: custom app limits, web domain blocklists, and multi-profile setups (e.g., "Work Deep Focus", "Study Session", "Night Wind Down").
We need to provide two key usability improvements:
1. **Local Encrypted Backup/Restore**: Enabling users to backup and restore their configured settings across device resets or reinstalls, completely offline without any third-party cloud.
2. **Quick Profile Switching from Dashboard**: Enabling users to easily switch between active focus profiles with one tap from the dashboard.

However, both capabilities introduce severe security and anti-relapse risks if improperly implemented:
- **Relapse Vector A (Backup Tampering / Injection)**: A user under Strict Mode might import a "clean" or "empty" backup file to immediately erase all app and domain blocks, effectively breaking Strict Mode.
- **Relapse Vector B (Secret Extraction)**: An export file containing recovery codes or active session tokens could be manipulated to forge recovery codes or artificially shorten session durations.
- **Relapse Vector C (Quota Reset Cheating)**: Restoring app limits could wipe `currentDayUsageMs`, resetting daily screen time to zero and giving the user unauthorized access.
- **Relapse Vector D (Profile Switch Bypass)**: A user in Strict Mode on "Deep Work" (which blocks social media) could tap a "Casual" profile chip on the dashboard to deactivate "Deep Work", completely bypassing Strict Mode without entering recovery codes or waiting for the delay timer.

## Decision Drivers
- **Anti-Relapse Invariant**: Strict Mode is sacred and immutable while active. No dashboard action or backup restore may weaken or cancel an active Strict Mode session.
- **Zero Cloud / Privacy-First**: 100% offline, local Storage Access Framework (SAF) integration with authenticated encryption at rest.
- **Cryptographic Rigor**: Industry-standard authenticated encryption (`AES-256-GCM`) with password-based key derivation (`PBKDF2WithHmacSHA256`) and cryptographically secure random salt and nonce.
- **Quota Integrity**: Daily consumed screen time and launch counts must never be reset by importing a backup.

## Considered Options

### For Settings Backup:
1. **Option A: Plaintext JSON Export via SAF**
   - *Pros*: Simple to implement.
   - *Cons*: Passwords, confidential schedule habits, and app usage configurations stored unencrypted on public storage.
2. **Option B: Cloud Backup (Firebase / Google Drive API)**
   - *Pros*: Automatic sync across devices.
   - *Cons*: Violates zero-network policy for Monk Mode; requires API keys, OAuth consent, and internet permissions.
3. **Option C: Local AES-256-GCM Encrypted Envelope with User Passphrase**
   - *Pros*: Zero network required, user owns the file, authenticated encryption with 128-bit GCM tag ensures tamper detection, password-derived key protects data at rest.

### For Profile Switching:
1. **Option A: Unconditional Profile Switch on Chip Click (Contributor Fork Flaw)**
   - *Pros*: Lowest friction.
   - *Cons*: Fatal Strict Mode bypass! Users can switch away from strict profiles with zero barrier.
2. **Option B: Confirmation Dialog for Strict Profiles (Contributor Fork Draft)**
   - *Pros*: Adds a speedbump.
   - *Cons*: A simple "Yes, confirm" dialog completely defeats the entire premise of Strict Mode. Relapsing users will simply click "Confirm".
3. **Option C: Hard-Locked Profile Switching During Active Strict Sessions (Chosen Hardened Architecture)**
   - *Pros*: Mathematically impossible to bypass Strict Mode via profile switching. If a Strict Session is active (`StrictSessionDao.getActiveStrictSessionSync() != null`), profile switching is rejected with `BlockedByStrictMode`. The user is instructed that Strict Mode must expire or be disarmed via the formal failsafe channel (`StrictLockScreen`).

## Decision Outcome
Chosen architecture: **Option C for Backup (AES-256-GCM Local Envelope)** and **Option C for Profile Switching (Hard-Locked During Active Strict Sessions)**.

### Architectural Rules & Invariants

#### 1. Backup Serialization & Anti-Tamper Exclusion
- The exported JSON includes:
  - `app_limits` (packageName, appName, dailyTimeLimitMinutes, dailyLaunchLimit, isBlocked).
  - `blocked_domains` (domain, isBlocked, category).
  - `focus_profiles` (name, isStrictMode, scheduleStartTime, scheduleEndTime, activeDaysMask) and associated `profile_blocked_packages` and `profile_blocked_domains`.
- The exported JSON **explicitly excludes**:
  - `recovery_codes` (never exported).
  - `strict_sessions` (never exported).
  - `failsafe_logs` (never exported).

#### 2. Cryptographic Envelope & Authenticated Associated Data (AAD)
```json
{
  "format": "monk_mode_encrypted_backup",
  "version": 1,
  "salt": "<Base64 16-byte random salt>",
  "iv": "<Base64 12-byte random nonce>",
  "ciphertext": "<Base64 AES-256-GCM ciphertext + 128-bit tag>"
}
```
- Derived key: `PBKDF2WithHmacSHA256` (100,000 iterations, 256-bit key length).
- Cipher: `AES/GCM/NoPadding`.
- **AAD Binding**: Envelope format and version are bound into the GCM authentication tag:
  `cipher.updateAAD("monk_mode_encrypted_backup:v$version".toByteArray(Charsets.UTF_8))`
  Tampering with plaintext `format` or `version` causes GCM authentication failure (`AEADBadTagException`), preventing header manipulation.
- Defensive deserialization catches `AEADBadTagException`, `IllegalArgumentException`, `JsonSyntaxException`, mapping them to typed error results (`DecryptionFailed`, `InvalidEnvelope`, `CorruptedPayload`).

#### 3. Strict Mode Import Guard (TOCTOU Defense)
- Strict mode is verified **atomically inside the Room transaction**:
  ```kotlin
  database.withTransaction {
      val activeStrictSession = strictSessionDao.getActiveStrictSessionSync()
      if (activeStrictSession != null && activeStrictSession.isActive) {
          return@withTransaction SettingsImportResult.BlockedByStrictMode
      }
      // Import execution...
  }
  ```
- If Strict Mode is active, import is aborted immediately inside the transaction.

#### 4. Quota Tamper & Profile Scoping Guards
- During import of `AppLimitEntity`:
  - If a package limit already exists in Room, retain its existing `currentDayUsageMs`, `currentDayLaunches`, and `lastResetDate`.
  - Only update limits (`dailyTimeLimitMinutes`, `dailyLaunchLimit`, `isBlocked`).
  - New package limits default `currentDayUsageMs` to 0.
- During import of profiles:
  - Do not overwrite the current runtime active profile; set `isActive = false` on imported profiles or maintain the existing active profile ID.
  - Delete child rules (`profile_blocked_packages`, `profile_blocked_domains`) strictly scoped to `profileId IN (:importedIds)`, preserving untouched local profiles.

#### 5. Hardened Profile Switch Invariants
- `ProfileSwitchDecisionEngine` evaluates target switch:
  - If `currentProfile?.id == targetProfile.id`: `AlreadyActive`.
  - If `activeStrictSession != null && activeStrictSession.isActive`:
    `BlockedByStrictMode(activeSessionEndTime)` -> Switching blocked!
  - If no strict session is active: `ImmediateSwitch`.
- Atomic Single-Query Switch:
  `UPDATE focus_profiles SET isActive = (CASE WHEN id = :targetId THEN 1 ELSE 0 END)`
  Executed inside a database transaction to ensure exactly one profile is active without intermediate states.

## Consequences
### Positive
- Strict Mode remains completely uncompromised against both user relapses and TOCTOU concurrency.
- AES-256-GCM with AAD prevents metadata tampering and offline envelope corruption.
- Single-query profile switching prevents orphaned profiles or multi-active state corruption.

### Negative / Trade-offs
- Users locked in Strict Mode cannot switch profiles even between two strict profiles without waiting for the session to conclude. (This is a deliberate trade-off in favor of psychological commitment).
