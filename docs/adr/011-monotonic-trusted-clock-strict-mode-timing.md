# ADR 011: Monotonic TrustedClock for Reboot- and Clock-Tamper-Proof Strict Timing

## Status
Accepted (Hardened via Adversarial Security Audit)

## Context and Problem Statement
Strict Mode and delayed unlock previously relied on `System.currentTimeMillis()`, allowing users to bypass active locks by rolling the system clock forward. Additionally, `BootCompletedReceiver` reset `startElapsedRealtime = -1L`, disabling tamper detection after reboot. An adversarial audit identified:
1. Conflating fixed-duration sessions with recurring calendar schedules risks cumulative deadlock and infinite lockups across overnight shutdowns.
2. Relying solely on `Settings.Global.BOOT_COUNT` risks freeze if the setting is unreadable (-1) on OEM ROMs; hardware elapsed rollback (`elapsed < lastElapsed`) provides mathematical proof of reboot.
3. Comparing wall-clock delta against monotonic delta across reboots causes 100% false-positive tamper alarms on benign overnight shutdowns.
4. Full Room `@Update` during periodic checkpoints creates lost-update races against delayed unlock requests.

## Decision
1. **Pure Domain `TrustedClock`**: Calculates `remainingMs = targetDurationMs - accumulatedMonotonicMs`, where `accumulatedMonotonicMs` aggregates monotonic elapsed realtime deltas.
2. **Dual-Condition Reboot Detection**: A reboot is identified if `(currentBootCount != -1 && session.bootCount != -1 && currentBootCount != session.bootCount) || (currentElapsed < session.lastElapsed)`.
3. **Session Semantics**:
   - `DURATION` sessions (interactive timed sessions): Remaining time governed purely by monotonic elapsed time; powered-off time clamped to 0 ms.
   - `SCHEDULED_WINDOW` sessions (recurring calendar windows): Deactivation occurs at the scheduled calendar boundary provided no in-session clock tamper occurred.
   - Indefinite sessions (`targetEndTime == 0L`): `remainingMs = Long.MAX_VALUE`.
4. **Bifurcated Tamper Detection**: Same-boot clock tamper is flagged when `(wallDelta > monotonicDelta + tolerance) || (wallDelta < -tolerance)`. Reboot wall jumps are not flagged as tamper; powered-off duration is simply clamped.
5. **Atomic DAO Checkpoints**: `StrictSessionDao.checkpointMonotonicClock(...)` updates monotonic tracking columns without touching other session state, eliminating lost-update race conditions.
6. **Hot-Path Performance**: `bootCount` is cached at process startup; `FocusAccessibilityService` checks `SystemClock.elapsedRealtime()` via memory/VDSO budget (< 10ms).
7. **Room Migration 5 -> 6**: Adds monotonic checkpoint columns (`accumulatedMonotonicMs`, `lastElapsedRealtime`, `lastWallTime`, `bootCount`, `delayedUnlockStartAccumulatedMs`) with safe backward-compatible seeding.

## Consequences
- Strict Mode cannot be escaped by manual clock manipulation (setting date/time forward 48h or rebooting into a spoofed date).
- Recurring schedules never deadlock across overnight phone shutdowns.
- No lost-update races between background checkpoints and user unlock requests.
- Backward-compatible Room migration with comprehensive unit, migration, and stress test suites.
