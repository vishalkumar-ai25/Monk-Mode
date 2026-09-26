# ADR 0004: Authenticated Encrypted Settings Backup & Restore via AES-256-GCM and PBKDF2

- **Status:** Accepted
- **Date:** 2026-09-26
- **Deciders:** Android Engineering & Architecture Team
- **Technical Context:** Android 8.0 (API 26) through Android 15 (API 35), Scoped Storage (Storage Access Framework).

---

## 1. Context & Problem Statement

Monk Mode users frequently configure intricate custom focus configurations, including daily app limits, launch quotas, blocked domains across categories, and complex schedule profiles. Users require a portable mechanism to backup and restore their configuration across device migrations, OS re-flashes, or app reinstalls.

However, adding export and import functionality introduces severe security and anti-relapse challenges:
1. **Malicious Tampering & Relapse Backdoors:** A user under active Strict Mode could export an unconstrained configuration, wipe current limits, or import an empty ruleset to escape restrictions.
2. **Quota Reset Attacks:** Importing a backup could maliciously reset daily screen time counters (`currentDayUsageMs = 0`, `currentDayLaunches = 0`), creating an infinite usage loophole.
3. **Recovery Code & Strict Token Leakage:** If emergency recovery codes or active strict session tokens were serialized into export files, private cryptographic authenticators could be leaked or cloned.
4. **Main-Thread ANRs on Low-End Hardware:** Key derivation with recommended iterations (200,000+) requires significant CPU compute that blocks the Android main thread if not explicitly offloaded.

---

## 2. Decision & Architecture

We enforce local-only, zero-cloud encrypted backup using **AES-256-GCM** authenticated symmetric encryption with keys derived via **PBKDF2-HMAC-SHA256**.

### A. Cryptographic Standards
- **Cipher:** `AES/GCM/NoPadding` (256-bit key length).
- **Initialization Vector (IV):** 12 bytes of cryptographically secure randomness generated per export via `SecureRandom`.
- **Authentication Tag:** 128-bit tag (`GCMParameterSpec`). Any bit-flipping, corruption, or incorrect passphrase immediately triggers `AEADBadTagException` prior to plaintext exposure.
- **Key Derivation:** `PBKDF2WithHmacSHA256` with:
  - 16-byte random salt.
  - 210,000 iterations (OWASP recommended standard for HMAC-SHA256).
- **Format Envelope:** Serialized as a structured JSON envelope containing base64-encoded salt, IV, and ciphertext, tagged with format versioning.

### B. Anti-Tamper & Security Rules
1. **Strict Exclusion of Authenticator Tables:**
   - The backup serializer explicitly excludes `recovery_codes` and `strict_sessions`. Only user policy tables (`app_limits`, `blocked_domains`, `focus_profiles`, and profile associations) are exported.
2. **Hard Block on Active Strict Mode:**
   - `SettingsBackupManager.importEncryptedBackup()` queries `strictSessionDao.getActiveStrictSessionSync()`. If any strict session is active, the import is hard-rejected (`SettingsImportResult.BlockedByStrictMode`), preventing users from bypassing active focus lockdown.
3. **Usage Counter Preservation (Anti-Relapse):**
   - When importing existing app limits, current daily consumption metrics (`currentDayUsageMs`, `currentDayLaunches`, and `lastResetTimestamp`) are retained from the live database rather than being overwritten by the backup file.

### C. Threading & Platform Health
- All key derivation and AES operations are executed on `Dispatchers.Default` (CPU pool), offloading work from both `Dispatchers.Main` and `Dispatchers.IO`.
- UI layers render an explicit progress indicator during key derivation to give clear user feedback and prevent Android ANRs (Application Not Responding).

---

## 3. Alternatives Considered

### Option 1: Unencrypted Plain JSON Export
- **Pros:** Trivial to implement; inspectable in text editor.
- **Cons:** Leaves user profiles, schedules, and package usage lists exposed in cleartext on shared external storage; easily editable by users to forge corrupted or modified schemas.
- **Verdict:** Rejected due to privacy and data integrity concerns.

### Option 2: Android Jetpack Security (`MasterKeys` / Android KeyStore)
- **Pros:** Hardware-backed cryptographic keys.
- **Cons:** KeyStore keys cannot be exported across different physical devices. If a user migrates to a new phone, KeyStore-encrypted backups cannot be restored.
- **Verdict:** Rejected for cross-device backup portability. User-passphrase derived PBKDF2 allows cross-device restoration while maintaining cryptographic isolation.

---

## 4. Consequences & Verification

- **Positive:** Airtight anti-tamper guarantees; zero recovery code leakage; protection against daily quota resets; deterministic unit test coverage in `SettingsCryptoEngineTest` and `SettingsBackupManagerTest`.
- **Negative:** Passphrase recovery is cryptographically impossible if forgotten (by design). Users must retain their chosen passphrase.
