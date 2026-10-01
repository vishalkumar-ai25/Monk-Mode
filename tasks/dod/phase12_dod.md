# Definition of Done: Phase 12 — Hardened Quick Profile Switching

## 1. Domain Logic & Invariants
- [ ] `ProfileSwitchDecisionEngine` evaluates target profile against current state and active strict sessions.
- [ ] Switching is strictly BLOCKED with `BlockedByStrictMode` if `strict_sessions` has an active session (`isActive == true`).
- [ ] Switching to the currently active profile returns `AlreadyActive`.
- [ ] Switching between inactive profiles when outside Strict Mode returns `ImmediateSwitch`.
- [ ] Pure Kotlin unit tests in `ProfileSwitchDecisionEngineTest` pass 100%.

## 2. Room Data Layer
- [ ] `FocusProfileDao.switchToProfile(targetId: Long)` atomically activates the target profile and deactivates all others in a single SQL query (`UPDATE focus_profiles SET isActive = (CASE WHEN id = :targetId THEN 1 ELSE 0 END)`).
- [ ] Room tests in `FocusProfileSwitchDaoTest` pass 100%.

## 3. UI Presentation
- [ ] `QuickProfileChipRow` displays horizontal list of profiles with Monk Mode styling.
- [ ] Active profile highlighted with `MonkSage` or `MonkEmber` indicators.
- [ ] Tapping a profile when locked in Strict Mode shows clear feedback informing the user that Strict Mode locks profile switching.
- [ ] Integrated cleanly into `DashboardScreen.kt`.

## 4. Verification
- [ ] `./gradlew testDebugUnitTest` passes 100%.
- [ ] `./gradlew assembleDebug` builds successfully.
