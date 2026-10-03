# ADR 009: Anti-Uninstall & Tamper Protection via Device Administrator & Accessibility

## Status
Accepted

## Context
When Strict Mode is active, users committed to deep focus must not be able to circumvent their focus session by uninstalling or force-stopping the Monk Mode app.
If a user can simply drag the app icon to "Uninstall", open Android Settings to "Clear Data" or "Force Stop", or deactivate the accessibility service, the entire behavioral guarantee of Strict Mode is compromised.

Android provides two synergistic APIs to build multi-layered anti-uninstall and anti-tamper defenses:
1. **Device Administrator (`DevicePolicyManager` & `DeviceAdminReceiver`)**:
   - Activating Monk Mode as a Device Administrator informs the Android OS Package Manager to disallow standard launcher drag-and-drop uninstallation.
   - The OS greys out the uninstall button and displays *"Can't uninstall active device administrator app"*.
2. **Accessibility Service (`FocusAccessibilityService`) Targeted Settings Interception**:
   - Device Administrator deactivation or App Info ("Force Stop" / "Clear Data") can still be accessed via Android Settings.
   - Rather than clumsily blocking the entire Android Settings application (which breaks Wi-Fi, Bluetooth, Display brightness, and Sound), `FocusAccessibilityService` performs fine-grained window node inspection.
   - When Strict Mode is active and Settings displays Monk Mode's App Info or Device Admin deactivation screens, the service immediately executes `GLOBAL_ACTION_HOME` and renders the tamper protection overlay.

## Decision
1. **Device Administrator Integration (`StayFocusedDeviceAdminReceiver`)**:
   - Provide `DevicePolicyManager` integration.
   - `onDisableRequested` returns a protective warning during active Strict Mode.
   - Expose helper `isDeviceAdminActive(context)` to check status.
2. **Targeted Settings & Installer Tamper Detection (`FocusAccessibilityService` & `InterceptionDecisionEngine`)**:
   - In `FocusAccessibilityService`, when in `com.android.settings`, `com.google.android.packageinstaller`, or `com.android.vending`:
     - Inspect the active window hierarchy for Monk Mode targets (`com.stayfocused.app` or "Monk Mode").
     - Detect specific dangerous settings classes: `DeviceAdminAdd`, `InstalledAppDetails`, `ManageApplications`.
     - When a tamper target is detected and Strict Mode is active: immediately trigger `GLOBAL_ACTION_HOME` and show the tamper overlay.
     - General system settings (Wi-Fi, Bluetooth, Battery, Display, Sound) remain completely accessible.
3. **UI Integration (`StrictLockScreen.kt` & `ProtectionHealthChecker.kt`)**:
   - Add an **Anti-Uninstall (Device Admin)** status card in `StrictLockScreen` and Protection Status.
   - If Device Admin is not granted, provide an immediate one-tap intent (`DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN`) to activate protection.
   - When arming Strict Mode, check whether Device Admin is active and encourage activation to guarantee uninstallation lock.
4. **Developer Failsafe Preservation**:
   - Developers can always uninstall via ADB (`adb uninstall com.stayfocused.app`) through USB debugging, ensuring the test device is never permanently locked.

## Consequences
- Impossible to bypass Strict Mode via standard launcher uninstallation or Settings data clearing.
- Normal system settings (Wi-Fi, Bluetooth, Audio) remain accessible without disruption.
- Completely adheres to Android security best practices with user-explicit Device Admin consent.
