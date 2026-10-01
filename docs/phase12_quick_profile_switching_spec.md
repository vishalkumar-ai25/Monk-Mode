# Feature Specification: Phase 12 — Hardened Quick Profile Switching from Dashboard

## 1. Context & Objectives
In Monk Mode (Stay Focused), users configure multiple `FocusProfileEntity` profiles (e.g. "Deep Work", "Reading", "Wind Down") each with distinct app and website blocking rules.
The objective of **Phase 12 (Hardened quick profile switching)** is to provide an intuitive control surface on the Dashboard while guaranteeing 100% strict-mode anti-relapse integrity:
1. **Horizontal Chip Row**: Displays all existing `FocusProfileEntity` profiles horizontally at the top of the Dashboard.
2. **Immediate Activation When Inactive/Normal**: Tapping an inactive profile immediately deactivates other profiles and activates the selected profile using a single atomic SQL update query.
3. **Hard-Locked Strict Mode Invariant**: If a Strict Session is active (`strictSessionDao.getActiveStrictSessionSync() != null` where `isActive == true`), switching to any other profile is **strictly blocked** (`ProfileSwitchDecision.BlockedByStrictMode`). The user CANNOT bypass Strict Mode via a simple confirmation dialog or button tap.
4. **Seamless Room Integration**: Operates purely on existing tables (`focus_profiles`, `profile_blocked_packages`, `profile_blocked_domains`). Zero changes to `vpn/`, `service/`, or `DeviceAdminReceiver`.

---

## 2. Subsystem Boundaries & Anti-Tamper Isolation
- **`vpn/` package**: **NO changes**. `DnsVpnService` relies on `BlockedDomainDao` and `FocusProfileDao.getActiveBlockedDomains()`. Updating active profile in Room immediately propagates active blocked domains.
- **`service/` package**: **NO changes**. `FocusAccessibilityService` checks `FocusProfileDao.getAllProfilesWithRules()` and `getActiveBlockedPackages()`.
- **`strict/` & `receiver/`**: **NO changes**. `StayFocusedDeviceAdminReceiver` remains completely untouched.
- **Data Layer (`data/local/dao/FocusProfileDao.kt`)**:
  - `switchToProfile(targetId: Long): Unit` (atomic SQL query `UPDATE focus_profiles SET isActive = (CASE WHEN id = :targetId THEN 1 ELSE 0 END)`)
  - `getActiveProfileSync(): FocusProfileEntity?`
- **Domain Layer (`domain/`)**:
  - `ProfileSwitchDecisionEngine`: Pure Kotlin engine evaluating switch decisions (`AlreadyActive`, `ImmediateSwitch`, `BlockedByStrictMode`).
  - `domain/model/ProfileSwitchModels.kt`: Data contracts for decisions.
- **UI Layer (`ui/`)**:
  - `QuickProfileChipRow.kt`: Composable horizontal chip row and feedback snackbar/dialog explaining Strict Mode lock when blocked.
  - `DashboardScreen.kt`: Wired to display profile chips and handle switches.

---

## 3. Decision Matrix (`ProfileSwitchDecisionEngine`)

| Current Active State | Target Profile State | Decision | Action |
| :--- | :--- | :--- | :--- |
| `targetProfile.id == currentProfile.id` and active | Any | `AlreadyActive` | No-op, maintain active state |
| Active Strict Session exists (`isActive == true`) | Any different profile | `BlockedByStrictMode` | Rejection toast/dialog; redirect to `StrictLockScreen` |
| No active Strict Session | Any inactive profile | `ImmediateSwitch` | Atomically activate target profile in Room |

---

## 4. Affected Files
1. `docs/phase12_quick_profile_switching_spec.md` (this spec)
2. `tasks/todo.md` (Phase 12 task checklist)
3. `tasks/dod/phase12_dod.md` (DoD verification checklist)
4. `app/src/main/java/com/stayfocused/app/domain/model/ProfileSwitchModels.kt` (domain contracts)
5. `app/src/main/java/com/stayfocused/app/domain/ProfileSwitchDecisionEngine.kt` (decision engine)
6. `app/src/test/java/com/stayfocused/app/domain/ProfileSwitchDecisionEngineTest.kt` (unit tests)
7. `app/src/main/java/com/stayfocused/app/data/local/dao/FocusProfileDao.kt` (transactional switch queries)
8. `app/src/test/java/com/stayfocused/app/data/local/FocusProfileSwitchDaoTest.kt` (DAO switch tests)
9. `app/src/main/java/com/stayfocused/app/ui/components/QuickProfileChipRow.kt` (Compose UI chips)
10. `app/src/main/java/com/stayfocused/app/ui/screens/DashboardScreen.kt` (Dashboard integration)
