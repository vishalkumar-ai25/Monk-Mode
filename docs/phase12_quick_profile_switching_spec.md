# Feature Specification: Phase 12 — Quick Profile Switching from Dashboard

## 1. Context & Objectives
In Monk Mode (Stay Focused), users configure multiple `FocusProfileEntity` profiles (e.g. "Deep Work", "Reading", "Wind Down") each with distinct app and website blocking rules.
The objective of **Feature #6 (Quick profile switching from Dashboard)** is to give users immediate, low-friction control over their focus state directly from the Dashboard:
1. **Horizontal Chip Row**: Displays all existing `FocusProfileEntity` profiles horizontally at the top of the Dashboard.
2. **Immediate Activation**: Tapping an inactive profile immediately deactivates the current profile and activates the selected profile without any confirmation dialogs or friction in normal operation.
3. **Strict Mode Confirmation Guard**: If the currently active (outgoing) profile was in **Strict Mode** (`isStrictMode == true`), an explicit confirmation dialog is displayed before switching, ensuring the user deliberately acknowledges transitioning out of strict focus rules.
4. **Seamless Room Integration**: Operates purely on existing tables (`focus_profiles`, `profile_blocked_packages`, `profile_blocked_domains`). Zero changes to `vpn/`, `service/`, or `DeviceAdminReceiver`.

---

## 2. Subsystem Boundaries & Anti-Tamper Isolation
- **`vpn/` package**: **NO changes**. `DnsVpnService` relies on `BlockedDomainDao` and `FocusProfileDao.getActiveBlockedDomains()`. Updating active profile in Room immediately propagates active blocked domains.
- **`service/` package**: **NO changes**. `FocusAccessibilityService` checks `FocusProfileDao.getAllProfilesWithRules()` and `getActiveBlockedPackages()`.
- **`strict/` & `receiver/`**: **NO changes**. `StayFocusedDeviceAdminReceiver` remains completely untouched.
- **Data Layer (`data/local/dao/FocusProfileDao.kt`)**:
  - `deactivateAllProfiles(): Unit`
  - `switchToProfile(targetId: Long): Unit` (transactional switch)
  - `getActiveProfileSync(): FocusProfileEntity?`
- **Domain Layer (`domain/`)**:
  - `ProfileSwitchDecisionEngine`: Pure Kotlin engine evaluating switch decisions (`AlreadyActive`, `ImmediateSwitch`, `RequiresConfirmation`).
  - `domain/model/ProfileSwitchModels.kt`: Data contracts for decisions.
- **UI Layer (`ui/`)**:
  - `QuickProfileChipRow.kt`: Composable horizontal chip row and `StrictProfileSwitchDialog`.
  - `DashboardScreen.kt`: Wired to display profile chips and handle switches.

---

## 3. Decision Matrix (`ProfileSwitchDecisionEngine`)

| Current Active Profile State | Target Profile State | Decision | Action |
| :--- | :--- | :--- | :--- |
| `targetProfile.id == currentProfile.id` and active | Any | `AlreadyActive` | No-op, maintain active state |
| `currentProfile == null` or `!currentProfile.isActive` | Any target profile | `ImmediateSwitch` | Switch to target immediately |
| `currentProfile.isStrictMode == false` | Different target profile | `ImmediateSwitch` | Switch to target immediately |
| `currentProfile.isStrictMode == true` | Different target profile | `RequiresConfirmation` | Prompt dialog; switch only upon confirmation |

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
9. `app/src/main/java/com/stayfocused/app/ui/components/QuickProfileChipRow.kt` (Compose UI chips & confirmation dialog)
10. `app/src/main/java/com/stayfocused/app/ui/screens/DashboardScreen.kt` (Dashboard integration)
