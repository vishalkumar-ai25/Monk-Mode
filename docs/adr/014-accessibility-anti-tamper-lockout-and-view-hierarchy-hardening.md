# ADR 014: Accessibility Deactivation Lockout and Deep View Hierarchy Anti-Tamper Hardening

## Status
Accepted (Hardened via Adversarial Threat Modeling)

## Context and Problem Statement
In Stay Focused / Monk Mode, the core interception engine relies on `FocusAccessibilityService`. When Strict Mode is active, users must NOT be able to disable Monk Mode's Accessibility permission via System Settings.

Live device observations on Realme C65 5G (realme UI 5.0 / Android 14) and adversarial threat modeling identified critical bypass vectors and architectural pitfalls:
1. **Generic Container (`SubSettings`) & Deep Nesting**: On ColorOS 14 and modern AOSP, settings sub-screens share the generic Activity class `com.android.settings.SubSettings` / `com.oplus.settings.SubSettings`. Preference titles ("Monk Mode", "Downloaded apps", "Use Monk Mode") live at depth 7–11.
2. **Main Thread IPC Budget (< 10ms)**: Unbounded DFS traversal across 100 nodes induces 50ms–180ms Binder IPC delays, causing frame stutters and ANR risks on budget SoCs (MediaTek Dimensity 6300).
3. **False-Positive Traps**:
   - Blindly matching `"accessibility"` in `DANGEROUS_CATEGORIES` blocks Sound settings (due to the standard "Accessibility volume" slider) and Display settings ("High contrast text").
   - Bare action verbs ("stop", "disable") falsely block "Stop personal hotspot" and "Disable SIM card".
   - Blindly un-filtering `PermissionController` blocks Monk Mode's own runtime permission prompts (e.g. `POST_NOTIFICATIONS`).
4. **Recent Apps Backstack Resume Bypass**: Using `GLOBAL_ACTION_HOME` leaves the Settings deactivation dialog suspended in the background; resuming Settings via the Recents task-switcher allows the user to tap "Stop" before anti-tamper can react.
5. **Hardware Shortcut Bypass**: The Android 12+ Accessibility Volume Key Shortcut (holding Vol Up + Vol Down for 3s) disarms accessibility directly in `system_server` without launching Settings.

## Decision & Hardening Contract

### 1. Targeted Fast-Bail View Hierarchy Traversal (Strictly < 10ms IPC)
- Traversal limits:
  - `maxDepth = 12`
  - `maxCount = 40` nodes (enforced via explicit `visitedNodes` counter in addition to text collection limit)
- **Fast-Bail Optimization**: The instant any collected text references self (`referencesSelf == true`), traversal immediately short-circuits and bails.
- Traverses `node.text`, `node.contentDescription`, and `node.paneTitle`.
- **Privacy Scoping Invariant**: Traversal runs ONLY when `isTargetSettingsOrInstaller == true`. Third-party user apps are never traversed (100% zero screen scraping guarantee).

### 2. Header & Pane Scoping (Zero False Positives on Sound/Display/Homepage)
- `isAllowedSettingsClass` (Wi-Fi, Bluetooth, Display, Sound, and root Settings homepage) maintains strict high precedence.
- Accessibility screening is scoped to top-level headers and pane titles within container activities or dialogs (`SubSettings`, dialogs, preference fragments):
  - Match if `paneTitle` or window title equals `"Accessibility"`, `"Downloaded apps"`, `"Downloaded services"`, `"Installed apps"`, or `"Installed services"` (case-insensitive) inside a container/dialog activity.
  - Explicitly exclude matching if the screen header contains `"Sound"`, `"Volume"`, `"Display"`, `"Brightness"`, or `"Wi-Fi"`, supporting both '&' and localized 'and' variations.
  - Root Settings homepage activities are exempted so scrolling past the "Accessibility" menu item does not trigger a false lockout.

### 3. Self-Coupled Dangerous Action Matching
- Bare single-word verbs ("stop", "disable") are **never** matched globally.
- Deactivation actions in Settings trigger `BlockTamper` ONLY when:
  - Coupled with self-reference: `referencesSelf && (text.contains("stop") || text.contains("turn off") || text.contains("disable"))`.
  - OR matching specific compound phrases: `"force stop"`, `"clear data"`, `"clear storage"`, `"deactivate this device admin app"`, `"stop monk mode"`, `"stop stay focused"`, `"use monk mode"`, `"use stay focused"`.

### 4. Surgical PermissionController Filtering & Self-Exemption
- `GrantPermissionsActivity` and `ReviewPermissionsActivity` retain their transient status.
- If `referencesSelf` is true on `GrantPermissionsActivity`, it is explicitly **exempted** from `BlockTamper` so Monk Mode can receive its own runtime permissions.
- `UninstallerActivity` remains non-transient and unconditionally blocked.
- Accessibility deactivation confirmation dialogs (which live inside `com.android.settings` / `com.oplus.settings`, e.g. `COUIDialog` or `AlertDialog`) are evaluated by `SettingsTamperInspector` via self-reference and compound actions.

### 5. Backstack Popping to Defeat Recents Resume Bypass
- When `BlockTamper` fires in Settings:
  1. Call `performGlobalAction(GLOBAL_ACTION_BACK)` first (pops the SubSettings / confirmation dialog off the activity backstack).
  2. Call `performGlobalAction(GLOBAL_ACTION_HOME)` immediately after (brings the launcher to foreground).
  3. Show block overlay if `canDrawOverlays()` is true.
- Because `GLOBAL_ACTION_BACK` popped the fragment, resuming Settings from Recent Apps returns to the main Settings root rather than the deactivation screen.

### 6. Hardware Shortcut & Volume Key Lockout
- Add `"accessibilityshortcut"`, `"accessibility_shortcut"`, `"accessibilitybutton"` to class and title block rules to prevent users from binding the Volume Up + Down hardware shortcut to disarm Monk Mode.

### 7. Expanded OEM Settings Packages for Realme UI 5.0 (ColorOS 14)
- Add: `com.oplus.securitypermission`, `com.coloros.securitypermission`, `com.oplus.battery`.

## Verification Story
1. Unit Tests:
   - `SettingsTamperInspectorTest`:
     - Verifies SubSettings with "Monk Mode" + "Stop" triggers BlockTamper.
     - Verifies "Accessibility volume" in Sound settings returns Allow.
     - Verifies "Stop personal hotspot" in Hotspot settings returns Allow.
     - Verifies GrantPermissionsActivity for Monk Mode returns Allow (self-exemption).
     - Verifies UninstallerActivity returns BlockTamper.
     - Verifies Accessibility Shortcut settings returns BlockTamper.
   - `TransientWindowFilterTest`:
     - Verifies GrantPermissionsActivity is transient, UninstallerActivity is not.
2. Device Verification on Realme C65 5G:
   - Deploy debug build via ADB.
   - Start strict session.
   - Test navigating to Settings > Accessibility > Monk Mode -> immediate backstack pop and kickout to Home screen.
