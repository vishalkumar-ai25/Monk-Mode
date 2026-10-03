# Phase 19: Anti-Uninstall & Tamper Protection Specification

## Objective
Prevent bypass of Strict Mode through app uninstallation, app data clearing, force stopping, or device admin deactivation while Strict Mode is active, while preserving full usability of standard Android Settings (Wi-Fi, Bluetooth, Display, Sound).

## Functional Requirements
1. **Device Administrator Enrollment & Status**:
   - `StayFocusedDeviceAdminReceiver` registers as a BIND_DEVICE_ADMIN receiver in `AndroidManifest.xml`.
   - Provide `StayFocusedDeviceAdminReceiver.isDeviceAdminActive(context)`.
   - Provide intent launcher to request Device Admin (`DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN`).
   - When active, the Android OS natively disables uninstallation from the home screen launcher and app drawer.

2. **Fine-Grained Settings Tamper Detection**:
   - When `isStrictModeActive == true`:
     - If the user opens Android Settings (`com.android.settings` or OEM variants like `com.coloros.settings`):
       - Scan the active window root node for text matching `"Monk Mode"` or `"Stay Focused"` or package `"com.stayfocused.app"`.
       - Also detect activities targeting Device Administrator deactivation (`DeviceAdminAdd`, `DeviceAdminSettings`).
       - If matched: evaluate as `BlockReason.SettingsTamper` $\rightarrow$ trigger `performGlobalAction(GLOBAL_ACTION_HOME)` and display `BlockOverlayManager`.
     - If the user opens Package Installer (`com.google.android.packageinstaller`, `com.android.packageinstaller`) or Google Play Store (`com.android.vending`) attempting to uninstall `com.stayfocused.app`:
       - Trigger `performGlobalAction(GLOBAL_ACTION_HOME)` and display `BlockOverlayManager`.
     - If the user opens any other settings screen (Wi-Fi, Bluetooth, Notifications, Battery, Display, Sound):
       - Allow immediately without interruption.

3. **Strict Lock UI Integration**:
   - In `StrictLockScreen.kt`:
     - Display a dedicated **Anti-Uninstall (Device Admin)** card.
     - If inactive: display a warning status with a direct button `"Activate Anti-Uninstall Protection"` that launches system Device Admin settings.
     - If active: display a green verified badge: `"Anti-Uninstall Active (OS Protected)"`.
     - When activating Strict Mode: if Device Admin is not active, prompt the user with a dialog: *"To guarantee you cannot uninstall the app during Strict Mode, activate Anti-Uninstall Protection."* with options to activate or proceed.

4. **Health Check Integration**:
   - Update `ProtectionCheckType` / `ProtectionHealthChecker` to include `DEVICE_ADMIN` check so the user can see their anti-tamper readiness in the protection overview.

5. **Security & Anti-Tamper Invariance**:
   - Zero telemetry: All checks run 100% on-device.
   - Boot grace period: Grace period (3-5 mins after reboot) remains active for emergency troubleshooting if configured.
   - Developer escape hatch: ADB debugging can always uninstall (`adb uninstall com.stayfocused.app`) to ensure development safety.
