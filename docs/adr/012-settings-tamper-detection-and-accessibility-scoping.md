# ADR 012: Settings Tamper Detection by Class Name and Accessibility Scoping Evaluation

## Status
Accepted (Hardened via Adversarial Security Audit)

## Context and Problem Statement
When Strict Mode is active, users committed to deep focus must not be able to bypass focus restrictions by:
1. Opening Settings > Apps > Monk Mode to "Force Stop", "Clear Data", or "Uninstall".
2. Deactivating Monk Mode as Device Administrator or turning off its Accessibility Service.
3. Modifying system Date & Time to fast-forward session deadlines.
4. Tampering with VPN Settings or Private DNS to circumvent loopback DNS proxy blocking.
5. Triggering uninstallation via Package Installer or Google Play Store.

Previously, `SettingsTamperInspector` only blocked settings/installer screens when `windowTexts` contained our app name or package name (`referencesSelf`). Because `canRetrieveWindowContent="false"`, the accessibility event's `text` property is almost always sparse or empty, so `referencesSelf` evaluated to `false`, allowing the user to access these dangerous screens unimpeded.

## Threat Model & Adversarial Review Findings
An adversarial threat model for Android 14 and realme UI 5.0 (ColorOS 14) revealed:
1. **Single-Host Activity Reality (`SubSettings`)**: On modern Android, Settings screens run inside a single container (`com.android.settings.SubSettings` or `com.oplus.settings.OplusSubSettings`). Preference sub-screens (Wi-Fi, Apps, Date & Time) are Fragments that do not change the Window-level class name. Blocking `SubSettings` by class name alone causes massive false positives (locking users out of Wi-Fi and brightness), while allowing it causes false negatives.
2. **`android:packageNames` Dropping User Events**: If `android:packageNames` is statically set to settings packages in `accessibility_service_config.xml`, the Android OS drops all events from user applications (Chrome, Instagram, YouTube), completely disabling App Limits.
3. **Allow-List Privilege Escalation**: Whitelisting `*display*` without filtering permits navigation to "Display over other apps" (`SYSTEM_ALERT_WINDOW`), allowing revoking overlay permission.
4. **Transient Filter Uninstallation Bypass**: `TransientWindowFilter` previously classified all `PERMISSION_CONTROLLER_PACKAGES` as transient, inadvertently ignoring uninstallation confirmation dialogs.
5. **VPN Dialog Self-Blocking**: Blocking `com.android.vpndialogs` unconditionally intercepts Monk Mode's own VPN confirmation dialog (`ConfirmDialog`).

## Decision

### 1. Step 1: Diagnostic Logging & Observations
- In `FocusAccessibilityService.handleWindowEvent`, add debug logging when `isTargetSettingsOrInstaller && BuildConfig.DEBUG`:
  ```kotlin
  Log.d(TAG, "Settings/Installer window: pkg=$target, cls=$className, texts=$windowTexts")
  ```
- Document exact package names, class names, and window texts for realme UI 5 / Android 14 in `docs/tamper-observations.md`.

### 2. Step 2: Deterministic Class Name & Scoped Anti-Tamper Engine
In pure-Kotlin `SettingsTamperInspector`:
- **Explicit Allow-List (High Precedence)**:
  - Wi-Fi: `*wifi*`, `*networkprovider*` (Android 12+ Internet settings)
  - Bluetooth: `*bluetooth*`
  - Display: `*display*`, `*brightness*`, `*wallpaper*`, `*darkmode*`, excluding `*overlay*` / `*drawoverlay*` / `*specialaccess*`
  - Sound: `*sound*`, `*volume*`, `*ringtone*`, `*audio*`
  - If class matches allow-list, return `TamperDecision.Allow`.
- **Deterministic Class Name Block Categories**:
  - `app-details`: `installedappdetails`, `appdetails`, `appinfo`, `applicationsettings`, `manageapplications`, `appmanager`
  - `device-admin`: `deviceadmin`
  - `accessibility-service detail`: `accessibility`
  - `special-access`: `specialaccess`, `specialappaccess`, `drawoverlay`, `appdrawoverlay`
  - `date/time settings`: `datetime`, `date_time`, `zonepicker`, `setdate`, `settime`
  - `VPN settings`: `vpnsettings` or (`com.android.vpndialogs` excluding `confirmdialog`)
  - `private-DNS settings`: `privatedns`, `private_dns`
  - `package-installer uninstall`: `INSTALLER_PACKAGES` where class or text contains `uninstall`
  - Play Store uninstallation: `PLAY_STORE_PACKAGE` where class or text contains `uninstall` or `appdetails`
- **Text & Self Reference Matching**:
  - If `referencesSelf` is true on any settings/installer screen, block unconditionally.
  - If `windowTexts` contains dangerous action keywords (`"force stop"`, `"uninstall"`, `"clear data"`, `"clear storage"`, `"turn off"`), block unconditionally.

### 3. Step 3: Accessibility Scoping Evaluation & Privacy Claims
- **`android:packageNames` Evaluation**:
  - Must NOT be configured in `accessibility_service_config.xml` because it prevents `FocusAccessibilityService` from receiving events for monitored user apps (breaking App Limits).
- **`canRetrieveWindowContent` Evaluation**:
  - `canRetrieveWindowContent="true"` is declared in `accessibility_service_config.xml` to allow inspecting window text and nodes inside Settings/Installer dialogs where `SubSettings` masks fragment classes.
  - **Strict Code-Level Privacy Scoping**:
    - `FocusAccessibilityService.handleWindowEvent` ONLY accesses window node hierarchy when `isTargetSettingsOrInstaller == true`.
    - For all user applications (browsers, social media, banking, messaging), `FocusAccessibilityService` strictly enforces ZERO screen-scraping, inspecting only `targetPackageName`.
- **Uninstallation Transient Bypass Fix**:
  - In `handleWindowEvent`, uninstallation dialogs (`className?.contains("uninstall") == true`) are exempted from `TransientWindowFilter` suppression.

## Consequences
- Guaranteed anti-tamper enforcement even when OEM skins use generic container activities.
- General system settings (Wi-Fi, Bluetooth, Audio, Screen brightness) remain fully accessible.
- App Limits remain 100% operational across all user applications.
- Zero screen-scraping privacy guarantee on all personal and third-party apps is preserved in code.
