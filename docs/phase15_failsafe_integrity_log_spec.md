# Feature Specification: Phase 15 — Failsafe Integrity Log

## 1. Overview
The Failsafe Integrity Log provides an immutable, append-only audit trail in the local Room database (`failsafe_logs`). Every time a failsafe layer is touched—such as a time-delayed unlock requested or cancelled, an emergency recovery code verification attempted (successful or failed), or the scoped boot grace window activated upon device reboot—an audit entry is permanently recorded. The log is read-only, viewable under the Strict Lock screen, and possesses no UI delete, edit, or clear actions.

---

## 2. Invariants & Safety Constraints
- **Zero Core Enforcer Modifications**: No modifications to `vpn/`, `service/`, or `DeviceAdminReceiver`.
- **Adjacent System Modifications**:
  - `FailsafeManager`: Instruments delay requests, cancellations, and recovery code consumption/verification attempts.
  - `BootCompletedReceiver`: Instruments the 5-minute boot grace period activation upon device restart or package replacement.
  - `StayFocusedDatabase`: Registers `FailsafeLogEntity` and `FailsafeLogDao`, incrementing database schema to version 3.
- **Strictly Append-Only**: `FailsafeLogDao` contains *only* `@Insert` and `@Query("SELECT ...")` methods. No `@Delete`, `@Update`, or `DROP/TRUNCATE` operations exist in the DAO or UI.
- **Viewable Under Strict Lock**: The audit log is accessible even while Strict Mode lock is fully armed, ensuring total visibility into past override attempts without weakening enforcement.
- **100% Offline & Private**: Stored locally on device in SQLite; zero network permissions, zero analytics, zero external servers.

---

## 3. Data Model & Architecture

### 3.1 Entity: `FailsafeLogEntity`
- `id: Long = 0L` (Auto-generated primary key)
- `timestamp: Long` (Epoch milliseconds of occurrence)
- `eventType: String` (Enumerated failsafe action):
  - `DELAY_REQUESTED`: User initiated a 24–48h time-delayed unlock request.
  - `DELAY_CANCELLED`: User cancelled an existing delayed unlock request.
  - `RECOVERY_CODE_ENTERED`: User entered a recovery code (stores success vs failure).
  - `BOOT_GRACE_WINDOW_USED`: Device restart detected and boot grace window armed.
- `details: String` (Human-readable contextual information, e.g. "Requested 24h delay for Session #1" or "Invalid recovery code entered").
- `success: Boolean` (True if the failsafe action succeeded or was granted; false if an invalid code was supplied).

### 3.2 Data Access Object: `FailsafeLogDao`
```kotlin
@Dao
interface FailsafeLogDao {
    @Insert
    suspend fun insertLog(log: FailsafeLogEntity): Long

    @Query("SELECT * FROM failsafe_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<FailsafeLogEntity>>

    @Query("SELECT * FROM failsafe_logs ORDER BY timestamp DESC")
    suspend fun getAllLogsSync(): List<FailsafeLogEntity>

    @Query("SELECT COUNT(*) FROM failsafe_logs")
    suspend fun getLogCount(): Int
}
```

---

## 4. Components & Files to Create / Modify

### 4.1 New Files
1. `app/src/main/java/com/stayfocused/app/data/local/entities/FailsafeLogEntity.kt`
2. `app/src/main/java/com/stayfocused/app/data/local/dao/FailsafeLogDao.kt`
3. `app/src/main/java/com/stayfocused/app/domain/FailsafeLogFormatter.kt`
4. `app/src/main/java/com/stayfocused/app/ui/components/FailsafeLogCard.kt`
5. `app/src/test/java/com/stayfocused/app/data/local/FailsafeLogDaoTest.kt`
6. `app/src/test/java/com/stayfocused/app/domain/FailsafeLogFormatterTest.kt`

### 4.2 Modified Files
1. `app/src/main/java/com/stayfocused/app/data/local/StayFocusedDatabase.kt`
2. `app/src/main/java/com/stayfocused/app/strict/FailsafeManager.kt`
3. `app/src/main/java/com/stayfocused/app/receiver/BootCompletedReceiver.kt`
4. `app/src/main/java/com/stayfocused/app/ui/screens/StrictLockScreen.kt`
5. `app/src/test/java/com/stayfocused/app/strict/FailsafeManagerTest.kt`
