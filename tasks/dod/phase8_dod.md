# Definition of Done: Phase 8 — Protection Status Card & Watchdog Health Alerts

## Functional Requirements
- [ ] Pure-Kotlin domain models (`ProtectionCheckType`, `ProtectionCheckStatus`, `ProtectionCheckResult`, `ProtectionOverallStatus`, `ProtectionStatusSnapshot`).
- [ ] `ProtectionStatusEngine` implements aggregate status calculation (RED, AMBER, GREEN) based on subsystem health.
- [ ] `ProtectionHealthChecker` inspects OS subsystems safely (Accessibility, VPN, Battery Optimization, Watchdog liveness, Device Admin, Usage Stats).
- [ ] `ProtectionStatusCard` rendered on the Dashboard with calm Monk Mode styling, visual status badges, and one-tap fix triggers.
- [ ] `WatchdogWorker` evaluates protection health periodically (every 15 min) and triggers a high-priority system notification within 15 minutes of entering a RED state.
- [ ] Unit tests for `ProtectionStatusEngineTest` and `WatchdogWorkerTest` execute with 100% pass rate.
