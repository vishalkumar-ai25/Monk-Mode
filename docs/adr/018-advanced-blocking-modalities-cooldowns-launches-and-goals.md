# ADR 018: Advanced Blocking Modalities: Continuous Session Cooldowns, Debounced Launch Limits, and Prerequisite Goal-Based Unlocking

## Status
Accepted (Hardened via Adversarial Review)

## Context
Commercial productivity and digital wellbeing applications (such as Stay Focused v13.x) offer multiple complementary interception modalities beyond static 24-hour daily quotas:

1. **Continuous Session Cooldowns (Anti-Binge Interval / "Waiting Time")**:
   Users frequently exhaust their daily allowances in single, prolonged compulsive binges. The cooldown modality restricts usage to continuous bursts of $S$ minutes (e.g., 10 minutes), after which the target app is strictly locked for a mandatory recovery interval of $C$ minutes (e.g., 15 minutes).
2. **Debounced Daily Launch Limits**:
   Users compulsively open distracting applications dozens or hundreds of times per day to check notifications. The launch limit enforces a maximum of $N$ daily launches (e.g., 5 times/day). However, naive counting creates severe flaws: configuration changes, system dialogs, screen rotations, and IME popups can trigger dozens of false launches within seconds. A robust transition debounce and immediate launch-barrier lockout are essential. Furthermore, blocked launch attempts must NEVER burn the user's launch quota.
3. **Prerequisite Goal-Based Unlocking ("Work Before Play")**:
   Users establish positive reinforcement habits by locking distracting entertainment applications (e.g., Instagram, YouTube) until a productive Goal Application (e.g., Duolingo, Kindle, Coursera) has reached a daily usage threshold $G$ (e.g., 15 minutes). Once the user achieves their daily goal, the distracting apps unlock automatically.

---

## Architectural Decision

### 1. Database Schema Evolution (Room Migration 8 -> 9)
We evolve `app_limits` with 7 new columns to guarantee monotonic timing, tamper resilience, and goal tracking across reboots:

```sql
ALTER TABLE `app_limits` ADD COLUMN `sessionAllowanceMinutes` INTEGER NOT NULL DEFAULT 0;
ALTER TABLE `app_limits` ADD COLUMN `cooldownDurationMinutes` INTEGER NOT NULL DEFAULT 0;
ALTER TABLE `app_limits` ADD COLUMN `cooldownStartElapsedRealtime` INTEGER NOT NULL DEFAULT 0;
ALTER TABLE `app_limits` ADD COLUMN `cooldownActiveUntilWallMs` INTEGER NOT NULL DEFAULT 0;
ALTER TABLE `app_limits` ADD COLUMN `cooldownBootCount` INTEGER NOT NULL DEFAULT -1;
ALTER TABLE `app_limits` ADD COLUMN `goalPackageName` TEXT DEFAULT NULL;
ALTER TABLE `app_limits` ADD COLUMN `goalDurationMinutes` INTEGER NOT NULL DEFAULT 0;
```

- `sessionAllowanceMinutes`: Max continuous minutes allowed per session (0 = disabled).
- `cooldownDurationMinutes`: Cooldown lockout duration in minutes following session exhaustion.
- `cooldownStartElapsedRealtime`: Hardware-monotonic elapsed uptime timestamp (`SystemClock.elapsedRealtime()`) when the cooldown was triggered.
- `cooldownActiveUntilWallMs`: Wall-clock epoch millisecond timestamp indicating when the cooldown expires (0 = no active cooldown). Used as secondary anchor across reboots.
- `cooldownBootCount`: Boot count at which the cooldown was initiated, verified via `SystemClockSnapshotProvider`.
- `goalPackageName`: Package name of the prerequisite goal app (null = disabled).
- `goalDurationMinutes`: Target duration in minutes required in `goalPackageName` today before this app unlocks.

### 2. Pure Domain Models & Evaluation Engine
In `com.stayfocused.app.domain.model.InterceptionModels.kt`:
- Added `BlockReason.SessionCooldown(val appName: String, val remainingCooldownMs: Long, val totalCooldownMs: Long) : BlockReason`
- Added `BlockReason.GoalNotMet(val appName: String, val goalAppName: String, val currentGoalUsageMs: Long, val targetGoalUsageMs: Long) : BlockReason`
- Extended `AppLimitSnapshot` with the 7 new fields.
- Extended `InterceptionContext` with:
  - `goalAppsUsageMs: Map<String, Long> = emptyMap()`
  - `currentBootCount: Int = 0`
  - `elapsedRealtimeMs: Long = 0L`

In `InterceptionDecisionEngine.kt`:
Strict deterministic priority sequence:
1. Self application (`com.stayfocused.app`) -> `Allow`.
2. Anti-tamper & Settings/Installer -> `Block` or `Allow`.
3. Temporary Break Session -> `Allow` if not Strict Mode.
4. Active Focus Profiles -> `Block(ProfileActive)`.
5. Manual App Block (0 min, 0 launches, isBlocked) -> `Block(ManuallyBlocked)`.
6. Daily Screen Time Limit -> `Block(LimitReached(TIME_LIMIT))`.
7. Daily Launch Limit -> `Block(LimitReached(LAUNCH_LIMIT))`.
8. Active Session Cooldown:
   Evaluated with monotonic clock:
   - Same boot (`context.currentBootCount == limit.cooldownBootCount`):
     $\text{elapsed} = \max(0L, \text{context.elapsedRealtimeMs} - \text{limit.cooldownStartElapsedRealtime})$.
     $\text{cooldownTotalMs} = \text{limit.cooldownDurationMinutes} \times 60,000\text{L}$.
     $\text{remaining} = \text{cooldownTotalMs} - \text{elapsed}$.
   - Reboot / Unknown boot:
     $\text{remaining} = \max(0L, \text{limit.cooldownActiveUntilWallMs} - \text{context.currentTimeMillis})$.
   - If $\text{remaining} > 1,000\text{L}$ (1s tolerance clamp):
     -> `Block(BlockReason.SessionCooldown(limit.appName, remaining, cooldownTotalMs))`.
9. Goal-Based Prerequisite:
   If `limit.goalPackageName != null && limit.goalDurationMinutes > 0`:
   Goal usage = `context.goalAppsUsageMs[limit.goalPackageName] ?: 0L`.
   Required = `limit.goalDurationMinutes * 60,000L`.
   If $\text{goalUsage} < \text{required}$:
   -> `Block(BlockReason.GoalNotMet(limit.appName, goalAppName, goalUsage, required))`.
10. Default -> `Allow`.

### 3. Continuous Session Cooldown Tracking (`SessionCooldownTracker.kt`)
Pure Kotlin tracking engine:
- Inactivity Reset Threshold: To prevent the "2-minute exit" binge evasion loophole, the user must stay out of the app for at least the full cooldown duration:
  $$\text{InactivityResetThreshold} = \max(\text{cooldownDurationMinutes} \times 60,000\text{L}, 5 \times 60,000\text{L})$$
  Leaving the app for less than this duration pauses accumulation without resetting progress.
- Real-Time Foreground Monitoring Loop: Inside `FocusAccessibilityService.startForegroundMonitor`, the 1000ms loop ticks `sessionCooldownTracker.tick(nowElapsed, isScreenInteractive)`.
- When continuous usage reaches `sessionAllowanceMinutes * 60_000L`:
  - Triggers cooldown: sets `cooldownStartElapsedRealtime = nowElapsed`, `cooldownActiveUntilWallMs = nowWall + cooldownDurationMinutes * 60_000L`, and `cooldownBootCount = currentBootCount`.
  - Persists to Room DB asynchronously.
  - Evicts immediately via `performGlobalAction(GLOBAL_ACTION_HOME)`.
  - Displays `BlockOverlayView` with `BlockReason.SessionCooldown`.

### 4. Zero Binder IPC Hot-Path Cache
`FocusAccessibilityService` maintains an in-memory hot cache `@Volatile var cachedGoalAppUsageMs: Map<String, Long>`.
- Any package referenced as a `goalPackageName` in `app_limits` is automatically registered for foreground tracking and synced during daily reconciliations.
- `handleWindowEvent` reads directly from `cachedGoalAppUsageMs` in memory (0ms Binder IPC, eliminating ANRs and frame drops).

### 5. Debounced Launch Count Enforcer
- `recordAppLaunch` is called ONLY when `engine.evaluate(context)` returns `InterceptionResult.Allow`. Blocked launch attempts NEVER increment or burn the daily launch quota.
- 5-Second Debounce: A launch is counted only if:
  1. Target package differs from `lastForegroundPackage`, AND
  2. More than 5 seconds have elapsed since that package was last launched.
- Immediate Launch Barrier: If `currentDayLaunches >= dailyLaunchLimit`, the app is blocked *immediately* on window state change before window content can render.
- Atomic Cache Update: Upon an allowed launch, `cachedAppLimits` is synchronously updated in memory, guaranteeing immediate consistency before the asynchronous Room update completes.

### 6. Midnight Reset Invariant
At midnight (via `MidnightResetReceiver` and `MidnightResetWorker`):
- `currentDayUsageMs = 0`
- `currentDayLaunches = 0`
- `cooldownActiveUntilWallMs` and `cooldownStartElapsedRealtime` are NOT wiped! Active cooldowns survive midnight until their full monotonic duration has elapsed.

### 7. Goal Dependency Validation & Cycle Detection
In `GoalDependencyValidator.kt`:
- Rejects `goalPackageName.equals(packageName)` (self-dependency).
- Rejects `goalPackageName.equals(SELF_PACKAGE)` (Monk Mode as goal).
- Directed graph cycle detection (DFS): Rejects any configuration that introduces a circular dependency (e.g. A requires B and B requires A).
- Runtime Missing App Fallback: If `goalPackageName` is uninstalled or missing from `PackageManager`, the rule logs a warning and bypasses the goal block rather than permanently bricking the user.

### 8. Strict Mode Monotonic Ratchet Validation
When Strict Mode is active, existing limits cannot be loosened:
- `sessionAllowanceMinutes_new <= sessionAllowanceMinutes_old` (and cannot set to 0 if previously > 0).
- `cooldownDurationMinutes_new >= cooldownDurationMinutes_old` (cannot decrease cooldown).
- `dailyLaunchLimit_new <= dailyLaunchLimit_old` (cannot increase launches, cannot set to 0).
- `goalDurationMinutes_new >= goalDurationMinutes_old` (cannot decrease goal duration).
- `goalPackageName` cannot be changed or nullified once set.

---

## Invariants & Guarantees
1. **Zero Framework Coupling**: `InterceptionDecisionEngine`, `SessionCooldownTracker`, and `GoalDependencyValidator` are 100% pure Kotlin, with zero Android framework imports, enabling extensive JVM unit testing.
2. **Reboot & Tamper Resilience**: Cooldowns are anchored with monotonic hardware elapsed timestamps and boot counts.
3. **Strict Mode Invariance**: While Strict Mode is active, existing cooldowns, launch limits, and goal prerequisites cannot be loosened, deleted, or cleared.
4. **Zero Screen Scraping Guarantee**: No user texts or sensitive screens are read; enforcement relies purely on package names and monotonic hardware timestamps.
