# ADR 019: High-Precision UsageEvents Session Reconstruction, 24-Hour Hourly Telemetry Slicing, and Device Pickup Tracking

## Status
Accepted (Hardened via Adversarial Review)

## Context
Commercial digital wellbeing platforms (such as Stay Focused v13.x and Android Digital Wellbeing) require granular hourly analytics to answer two fundamental user questions:
1. **When am I most distracted?** (24-hour hourly foreground screen time distribution across individual applications).
2. **How compulsively am I checking my device?** (Hourly device pickups and screen unlock frequency).

### Limitations of Coarse Android APIs & Failure Modes Identified in Adversarial Review
Android's `UsageStatsManager.queryUsageStats(INTERVAL_DAILY, ...)` only yields aggregate totals for 24-hour windows. It cannot answer:
- How much time was spent on Instagram between 14:00 and 15:00?
- Was a 45-minute YouTube session continuous across the 13:45–14:30 boundary?
- How many times did the user unlock their phone at 02:00 in the morning?

Our adversarial threat modeling revealed several critical failure modes that must be strictly addressed:
1. **Room `@Upsert` Overwrite Hazard**: Blindly upserting hourly telemetry from `UsageStatsManager` would wipe out real-time `shortsBlockedCount` tracked by `FocusAccessibilityService` because Android's OS has no knowledge of intercepted feeds.
2. **Bare `UPDATE` Drop Hazard**: A bare SQL `UPDATE` on non-existent rows silently drops initial short-form block events.
3. **Midnight Rollover & IST (`UTC+05:30`) Offset Mismatch**: Hardcoded `3600000L` modulo math breaks on non-UTC timezones (e.g. India Standard Time `UTC+05:30`) where hour boundaries are offset from UTC by 30 minutes, and fails across midnight where `epochDay` increments.
4. **Ghost Usage on App Crash / OS Kill**: Unclosed sessions without `ACTIVITY_PAUSED` must be bounded by `SCREEN_NON_INTERACTIVE` and capped at 30 minutes (Android's max screen timeout), rather than 12 hours.
5. **False Pickups on Calls & Alarms**: Ringing alarms and incoming calls display over keyguard and resume activities without dismissing keyguard. On secured devices, pickups must trigger strictly on `KEYGUARD_HIDDEN`.
6. **Battery Drain from Screen-State Polling**: Querying `UsageStatsManager.queryEvents()` on every screen on/off causes Binder churn and triggers Realme UI 5.0 (ColorOS) battery killing. Reconstructive queries must run periodically (15m WorkManager), at midnight consolidation, or on-demand in the UI.

---

## Architectural Decision

### 1. Database Schema Evolution (Room Migration 9 -> 10)
To avoid mixing device-level metrics with app-level metrics (the sentinel `_device_total` anti-pattern), we normalize telemetry into two dedicated tables:

```sql
-- 1. Device-level hourly telemetry (pickups & total device screen time)
CREATE TABLE IF NOT EXISTS `hourly_device_telemetry` (
    `epochDay` INTEGER NOT NULL,
    `hourOfDay` INTEGER NOT NULL,
    `pickupCount` INTEGER NOT NULL DEFAULT 0,
    `totalScreenTimeMs` INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY(`epochDay`, `hourOfDay`)
);

CREATE INDEX IF NOT EXISTS `index_hourly_device_telemetry_epochDay`
ON `hourly_device_telemetry` (`epochDay`);

-- 2. Per-app hourly telemetry (foreground screen time & blocked shorts per app)
CREATE TABLE IF NOT EXISTS `hourly_app_telemetry` (
    `epochDay` INTEGER NOT NULL,
    `hourOfDay` INTEGER NOT NULL,
    `packageName` TEXT COLLATE NOCASE NOT NULL,
    `foregroundMs` INTEGER NOT NULL DEFAULT 0,
    `shortsBlockedCount` INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY(`epochDay`, `hourOfDay`, `packageName`)
);

CREATE INDEX IF NOT EXISTS `index_hourly_app_telemetry_epochDay_hourOfDay`
ON `hourly_app_telemetry` (`epochDay`, `hourOfDay`);
```

- `epochDay`: `LocalDate.toEpochDay()` (days since 1970-01-01), guaranteeing timezone-stable day identification.
- `hourOfDay`: Integer from $0$ to $23$ representing local clock hour.
- `packageName`: Target application package with SQLite `COLLATE NOCASE`.
- `foregroundMs`: Cumulative foreground time for this package in this specific hour.
- `shortsBlockedCount`: Number of short-form feeds intercepted for this package in this specific hour.

### 2. Pure Domain Session Slicing Engine (`UsageEventsReconstructor.kt`)

#### A. Hour-Boundary Session Slicing with Zone-Aware Precision
For any session $[T_{start}, T_{end}]$ for package $P$:
```kotlin
data class HourlyAppSlice(
    val epochDay: Long,
    val hourOfDay: Int,
    val packageName: String,
    val durationMs: Long
)
```
1. Given device `zoneId: ZoneId = ZoneId.systemDefault()`.
2. Convert $T_{start}$ to `ZonedDateTime`:
   - `startOfHour = currentZdt.truncatedTo(ChronoUnit.HOURS)`
   - `endOfHour = startOfHour.plusHours(1)`
3. For each hour interval $[H_{startMs}, H_{endMs})$:
   - `sliceStart = maxOf(sessionStart, H_startMs)`
   - `sliceEnd = minOf(sessionEnd, H_endMs)`
   - `sliceDuration = maxOf(0L, sliceEnd - sliceStart)`
   - If `sliceDuration > 0`:
     - Attribute to `(epochDay = currentZdt.toLocalDate().toEpochDay(), hourOfDay = currentZdt.hour, sliceDuration)`.
   - Advance `currentZdt = endOfHour` until $H_{startMs} \ge T_{end}$.
4. **Properties**:
   - Works seamlessly across midnight ($23:50 \to 00:30$ produces 10m on Day $D$, 30m on Day $D+1$).
   - Works across DST spring-forward (skipped hour) and fall-back (repeated hour).
   - Operates with exact millisecond precision in sub-hour offset timezones (e.g. IST `UTC+05:30`).

#### B. Screen Interactive Bounding & Ghost Usage Protection
- **Bounding Envelope**: An application session cannot continue while the screen is off or device is locked.
- Whenever `SCREEN_NON_INTERACTIVE` (16), `KEYGUARD_SHOWN` (17), or `DEVICE_SHUTDOWN` (26) occurs in the event stream, any active foreground session is immediately credited up to that event timestamp and cleared.
- **Safety Cap**: Any unclosed session without a subsequent event is clamped to $\min(T_{end}, T_{start} + 30\text{ minutes})$ (matching Android's maximum standard screen timeout), preventing multi-hour ghost accumulation on LMK kills or crashes.

#### C. Two-State Device Pickup Machine
A pickup represents an intentional, deliberate user interaction with the unlocked device:
- **States**: `LOCKED` (initial), `UNLOCKED`.
- **Transitions**:
  - `LOCKED` $\to$ `UNLOCKED`:
    - On secured devices (`keyguardManager.isKeyguardSecure == true`): Trigger strictly on `KEYGUARD_HIDDEN` (18).
    - On insecure devices (`keyguardManager.isKeyguardSecure == false`): Trigger on `SCREEN_INTERACTIVE` (15) followed by a non-system `ACTIVITY_RESUMED` within 10s.
    - Action: Attribute $+1$ pickup to `(epochDay, hourOfDay)` derived from the trigger timestamp.
  - `UNLOCKED` $\to$ `LOCKED`:
    - Trigger on `SCREEN_NON_INTERACTIVE` (16) or `KEYGUARD_SHOWN` (17) or `DEVICE_SHUTDOWN` (26).
- **Immunity Against False Positives**:
  - Incoming phone/WhatsApp calls and alarms show over keyguard while locked. Because keyguard is NOT dismissed (`KEYGUARD_HIDDEN` does not fire), incoming calls and ringing alarms NEVER count as device pickups.
  - Lockscreen glances (turning screen on to check notifications and turning off) do not fire `KEYGUARD_HIDDEN` and do not increment pickups.

### 3. DAO Boundary (`HourlyTelemetryDao.kt`)
To eliminate the `@Upsert` overwrite race condition, the DAO implements atomic SQL conflict resolutions:

```kotlin
@Dao
interface HourlyTelemetryDao {
    // 1. App Telemetry: Update foreground time while preserving shortsBlockedCount
    @Query("""
        INSERT INTO hourly_app_telemetry (epochDay, hourOfDay, packageName, foregroundMs, shortsBlockedCount)
        VALUES (:epochDay, :hourOfDay, :packageName, :foregroundMs, 0)
        ON CONFLICT(epochDay, hourOfDay, packageName)
        DO UPDATE SET foregroundMs = excluded.foregroundMs
    """)
    suspend fun upsertAppForegroundUsage(epochDay: Long, hourOfDay: Int, packageName: String, foregroundMs: Long)

    // 2. App Telemetry: Increment shortsBlockedCount atomically (creates row if none exists)
    @Query("""
        INSERT INTO hourly_app_telemetry (epochDay, hourOfDay, packageName, foregroundMs, shortsBlockedCount)
        VALUES (:epochDay, :hourOfDay, :packageName, 0, 1)
        ON CONFLICT(epochDay, hourOfDay, packageName)
        DO UPDATE SET shortsBlockedCount = hourly_app_telemetry.shortsBlockedCount + 1
    """)
    suspend fun incrementShortsBlocked(epochDay: Long, hourOfDay: Int, packageName: String)

    // 3. Device Telemetry: Atomic upsert of pickups and total screen time
    @Query("""
        INSERT INTO hourly_device_telemetry (epochDay, hourOfDay, pickupCount, totalScreenTimeMs)
        VALUES (:epochDay, :hourOfDay, :pickupCount, :totalScreenTimeMs)
        ON CONFLICT(epochDay, hourOfDay)
        DO UPDATE SET pickupCount = excluded.pickupCount, totalScreenTimeMs = excluded.totalScreenTimeMs
    """)
    suspend fun upsertDeviceTelemetry(epochDay: Long, hourOfDay: Int, pickupCount: Int, totalScreenTimeMs: Long)

    // Queries for UI & Analytics
    @Query("SELECT * FROM hourly_device_telemetry WHERE epochDay = :epochDay ORDER BY hourOfDay ASC")
    fun getHourlyDeviceTelemetryForDay(epochDay: Long): Flow<List<HourlyDeviceTelemetryEntity>>

    @Query("SELECT * FROM hourly_app_telemetry WHERE epochDay = :epochDay ORDER BY hourOfDay ASC, foregroundMs DESC")
    fun getHourlyAppTelemetryForDay(epochDay: Long): Flow<List<HourlyAppTelemetryEntity>>

    @Query("SELECT * FROM hourly_app_telemetry WHERE epochDay = :epochDay AND hourOfDay = :hourOfDay ORDER BY foregroundMs DESC")
    suspend fun getTopAppsForHour(epochDay: Long, hourOfDay: Int): List<HourlyAppTelemetryEntity>

    // 30-day retention prune (strictly accepts epochDay, NOT milliseconds!)
    @Query("DELETE FROM hourly_device_telemetry WHERE epochDay < :cutoffEpochDay")
    suspend fun pruneOldDeviceTelemetry(cutoffEpochDay: Long)

    @Query("DELETE FROM hourly_app_telemetry WHERE epochDay < :cutoffEpochDay")
    suspend fun pruneOldAppTelemetry(cutoffEpochDay: Long)
}
```

### 4. Background Sync & Reconciliation Cadence
1. **Periodic Refresh**: `UsageStatsTracker.syncHourlyTelemetry(database, zoneId)` runs every 15 minutes via `PeriodicTelemetryWorker` in `WorkManager`.
2. **Midnight Reset**: `MidnightResetReceiver` invokes final consolidation for `yesterdayEpochDay` at 00:00:01 and executes `pruneOldDeviceTelemetry(todayEpochDay - 30)` and `pruneOldAppTelemetry(todayEpochDay - 30)`.
3. **UI Lazy-Load**: The analytics screen triggers a one-shot reconciliation upon opening to ensure instantaneous freshness without background battery drain.
4. **Zero Screen-On/Off Polling**: Banned to protect battery life and avoid OEM process throttling on ColorOS / Realme UI.

### 5. UI Visualization (`HourlyUsageChart.kt`)
Jetpack Compose component:
- 24-column interactive bar chart ($00:00$ to $23:00$).
- Mode toggle: **Screen Time (minutes)** vs **Pickups (count)**.
- Active Hour Inspector: Tapping any bar reveals the top 5 applications used during that specific hour with exact minutes and app icons.
- Styled according to Monk Mode aesthetic (`MonkEmber`, `MonkCard`, `MonkLine`, `MonkText`).

---

## Invariants & Guarantees
1. **Zero Cloud Invariant**: 100% on-device SQLite storage. Zero network telemetry.
2. **Zero Screen Scraping Guarantee**: Only package names and system event constants from `UsageEvents` are processed. Zero UI view node inspection.
3. **Atomic Counter Invariant**: `shortsBlockedCount` is NEVER overwritten by `UsageStatsManager` background syncs.
4. **Mathematical Slicing Invariant**: $\sum_{h=0}^{23} \text{foregroundMs}(h, P) = \text{dailyForegroundMs}(P)$ within $\pm 100\text{ ms}$ boundary rounding tolerance.
5. **No False Pickups Invariant**: Unattended incoming calls, ringing alarms, and lockscreen notification glances do NOT count as device pickups.
