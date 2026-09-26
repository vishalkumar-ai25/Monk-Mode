# ADR 002: Failsafe Integrity Audit Log Architecture

## Status
Accepted

## Context and Problem Statement
In Stay Focused (Monk Mode), the strict enforcement mechanisms (Accessibility overlay interception, DNS loopback proxy, and Device Admin anti-uninstall protection) are intentionally designed to be virtually irreversible while active. To prevent user lockouts under catastrophic edge cases (e.g., forgotten recovery PINs, medical or professional emergencies), two failsafe channels exist:
1. **Time-Delayed Unlock**: A 24h to 48h countdown request allowing planned disarming of Strict Mode.
2. **Emergency Recovery Code**: A single-use high-entropy hashed secret code stored in `recovery_codes`.
3. **Boot Grace Window**: A 5-minute scoped window following device boot to allow troubleshooting without active lockouts.

However, without an immutable audit trail, a user in the throes of a digital relapse might repeatedly attempt brute-forcing recovery codes or triggering repeated device reboots to exploit grace windows without any lasting record of these attempts. Moreover, if a bypass occurs, there is zero visibility into how or when the failsafe was invoked.

How do we architect an audit logging system for failsafe mechanisms that is:
1. Completely immune to tampering or deletion, even if the user attempts to reset settings.
2. Observable directly within the Strict Lock UI without allowing the log itself to be used as a bypass attack surface.
3. 100% private, local-only, and zero-network.

## Decision Drivers
- **Anti-Tamper & Security Invariance**: The audit log must be append-only. No UI actions or public DAOs may support updating, clearing, or deleting log records.
- **Privacy & Offline Integrity**: All audit data must remain on the device within the SQLite database (`failsafe_logs`). No telemetric data or log events are ever transmitted over the network.
- **Forensic Visibility**: Every state transition on a failsafe mechanism (delay requested, delay cancelled, recovery code attempt with success/failure, boot grace period activation) must be captured with precise timestamps.
- **Zero Enforcement Interference**: Writing to the audit log must be asynchronous and fail-safe, never deadlocking or blocking core enforcers (`FocusAccessibilityService`, `DnsVpnService`).

## Considered Options
1. **Option 1: In-Memory / SharedPreferences Log**
   - *Pros*: Simple, zero schema migrations.
   - *Cons*: Vulnerable to data wiping, cleared easily when clearing app data or resetting preferences, not relational, no transactional guarantees.
2. **Option 2: Encrypted Append-Only File on Internal Storage**
   - *Pros*: Decoupled from SQLite schema.
   - *Cons*: Requires custom file rotation, serialization, and stream locking. Cannot be easily queried, joined, or observed as a reactive Room `Flow` in Jetpack Compose.
3. **Option 3: Append-Only SQLite Table via Room (`failsafe_logs`) with Restricted DAO Interface**
   - *Pros*: Fully transactional, reactive Flow integration into Compose UI, survives app updates, enforceable append-only invariant at the DAO API level (omitting `@Delete` and `@Update`).
   - *Cons*: Requires a Room database schema migration (`MIGRATION_2_3`).

## Decision Outcome
Chosen option: **Option 3: Append-Only SQLite Table via Room (`failsafe_logs`) with Restricted DAO Interface**.

### Architecture Details
1. **Schema (`failsafe_logs`)**:
   - `id`: `INTEGER PRIMARY KEY AUTOINCREMENT`
   - `timestamp`: `INTEGER NOT NULL` (System epoch millis)
   - `eventType`: `TEXT NOT NULL` (`DELAY_REQUESTED`, `DELAY_CANCELLED`, `RECOVERY_CODE_ENTERED`, `BOOT_GRACE_WINDOW_USED`)
   - `details`: `TEXT NOT NULL` (Contextual event details)
   - `success`: `INTEGER NOT NULL` (Boolean flag: 1 for success/granted, 0 for failure/rejected)
2. **DAO Contract (`FailsafeLogDao`)**:
   - Contains ONLY `@Insert` and `@Query("SELECT ...")`.
   - Explicitly omits any `@Delete`, `@Update`, or `TRUNCATE` operations.
3. **FailsafeManager Integration**:
   - Every `verifyAndConsumeRecoveryCode()` call records a log:
     - If code is invalid or null: `success = false`, `eventType = RECOVERY_CODE_ENTERED`.
     - If code is valid: `success = true`, `eventType = RECOVERY_CODE_ENTERED`.
   - Every `requestDelayedUnlock()` records `eventType = DELAY_REQUESTED`.
   - Every `cancelDelayedUnlock()` records `eventType = DELAY_CANCELLED`.
4. **BootCompletedReceiver Integration**:
   - On `BOOT_COMPLETED`, records `eventType = BOOT_GRACE_WINDOW_USED`.
5. **UI Presentation (`FailsafeLogCard`)**:
   - Read-only Composable rendered within `StrictLockScreen`.
   - Observes `database.failsafeLogDao().getAllLogs()` via Compose `collectAsState`.

## Consequences
### Positive
- Transparent forensic log builds user psychological accountability against impulsive override attempts.
- Brute-force guessing of recovery codes leaves an undeniable audit trail.
- Migration is formally tested with Room `MigrationTestHelper`.

### Negative / Trade-offs
- Schema version increments to `version = 3`. Existing databases require migration `MIGRATION_2_3`.
- Small, bounded storage growth in SQLite over months of usage (a few kilobytes at most).
