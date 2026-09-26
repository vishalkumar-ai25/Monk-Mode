# Feature Specification: Phase 9 — Friction-Based Break Requests

## 1. Context & Objectives
In Stay Focused (Monk Mode), quick breaks allow temporary relief from app and website blocking during non-strict focus sessions. However, commercial-grade digital wellness tools introduce mindful friction to prevent impulse break-taking:
- Requiring a typed reason forces the user to pause, reflect, and state their intention before dopamine-seeking habits take over.
- The reason is permanently recorded alongside the break session in Room DB (`BreakSessionEntity.reason`), maintaining an audit trail of break triggers.
- The break **cannot start** without a non-empty, non-whitespace reason string.

---

## 2. Subsystem Boundaries & Anti-Tamper Isolation
- **`vpn/` package**: **NO changes**. DNS proxy filtering respects `BreakDecisionEngine.isBreakActive()` as before.
- **`service/` package**: **NO changes**. `FocusAccessibilityService` checks `isBreakActive()` via Room DB.
- **`strict/` & `receiver/`**: **NO changes**. Strict Mode prohibitions are unchanged: breaks remain strictly forbidden during active strict sessions.
- **`breaks/` & `data/local/`**: `BreakSessionEntity` gains a `reason` property. `BreakDecisionEngine` enforces non-empty reason validation before creating or authorizing a break session.
- **`ui/`**: `TakeABreakCard` introduces a clean Monk Mode friction dialog/input to collect the reason prior to starting a session, and displays the reason while the break is active.

---

## 3. Data Model Changes

### `BreakSessionEntity`
Add column:
```kotlin
@ColumnInfo(name = "reason")
val reason: String = ""
```
- Schema: Bump database version to `3`. Room migration alters `break_sessions` table or uses `fallbackToDestructiveMigration`.

---

## 4. Decision Engine Logic & Edge Cases

### Validation Rules in `BreakDecisionEngine`
1. **`canStartBreak(reason: String, isStrictModeActive: Boolean): Boolean`**:
   - If `isStrictModeActive == true`: returns `false`.
   - If `reason.trim().isEmpty()`: returns `false`.
   - If `reason.isNotBlank()` and `!isStrictModeActive`: returns `true`.

2. **`createBreakSession(currentTimeMs: Long, durationMinutes: Int, reason: String): BreakSessionEntity`**:
   - Validates `require(reason.isNotBlank()) { "Break reason cannot be blank" }`.
   - Populates `reason = reason.trim()`.
   - Sets `startTime = currentTimeMs`, `endTime = currentTimeMs + (durationMinutes * 60 * 1000L)`.

### Edge Cases
- **Whitespace-only string** (e.g. `"   "`): Treated as empty, validation fails.
- **Very short reason** (e.g. `"Work call"`): Allowed, trimmed.
- **Active Strict Mode**: Strict Mode takes precedence over any entered reason; break is rejected.
- **Deactivated/Ended break**: The recorded reason is preserved in the entity for history/analytics.
- **Quick Settings Tile**: Invokes `createBreakSession` with an explicit non-empty tag (`"Quick Settings Break"`).

---

## 5. Affected Files
1. `app/src/main/java/com/stayfocused/app/data/local/entities/BreakSessionEntity.kt` (data model)
2. `app/src/main/java/com/stayfocused/app/data/local/StayFocusedDatabase.kt` (version bump to 3)
3. `app/src/main/java/com/stayfocused/app/breaks/BreakDecisionEngine.kt` (pure Kotlin logic)
4. `app/src/test/java/com/stayfocused/app/breaks/BreakDecisionEngineTest.kt` (unit tests)
5. `app/src/main/java/com/stayfocused/app/ui/components/TakeABreakCard.kt` (friction UI input)
6. `app/src/main/java/com/stayfocused/app/ui/screens/DashboardScreen.kt` (wires reason to break creation)
7. `app/src/main/java/com/stayfocused/app/tile/TakeABreakTileService.kt` (tile service integration)
8. `app/src/test/java/com/stayfocused/app/tile/TakeABreakTileServiceTest.kt` (tile unit tests)
