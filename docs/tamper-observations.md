# Settings Tamper Observation Log (Realme UI 5.0 / Android 14)

This diagnostic document is used to record the exact `packageName`, `className`, and `windowTexts` observed on the **Realme C65 5G (realme UI 5.0 / ColorOS 14 / Android 14)** when navigating into tamper-sensitive settings and installer screens.

## How to Capture Diagnostic Logs

1. Connect your Realme device via USB and ensure USB debugging is active (`adb devices`).
2. Run logcat filtered by the accessibility service tag:
   ```bash
   adb logcat -s FocusAccessibilityService:D SettingsTamperInspector:D
   ```
3. Navigate to each target screen listed below on the device.
4. Note the output logged in the format:
   `Settings/Installer window: pkg=<packageName>, cls=<className>, texts=<windowTexts>`
5. Fill in the observed values in the table below.

---

## Observation Table

| Target Flow / Screen | Expected Action / Tamper Risk | Observed `packageName` (`pkg`) | Observed `className` (`cls`) | Observed `windowTexts` (`texts`) | Observed Behavior | Realme UI 5.0 Notes |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **1. App Info**<br>*(Settings > Apps > App management > Monk Mode)* | "Force stop", "Uninstall", "Clear data", "Clear storage", "Permissions" | `com.android.settings` or `com.oplus.settings` | `com.android.settings.SubSettings` or `*AppInfo*` | `[Monk Mode, App info, Force stop, Uninstall, Storage usage]` | Block overlay shown, returned to Home | Deep links from launcher / recents open here directly |
| **2. Force Stop Dialog**<br>*(Tapping "Force stop" button)* | Process termination, deactivating AccessibilityService & DnsVpnService | `com.android.settings` or `com.oplus.settings` | `com.coui.appcompat.dialog.COUIDialog` or `*AlertDialog*` | `[Force stop?, If you force stop an app, it may misbehave, Cancel, Force stop]` | Block overlay shown, returned to Home | Confirmation dialog popup |
| **3. Uninstall Dialog**<br>*(Tapping "Uninstall" button or from Launcher drag)* | Package uninstallation | `com.android.permissioncontroller` or `com.google.android.packageinstaller` | `com.android.permissioncontroller.permission.ui.UninstallerActivity` | `[Do you want to uninstall this app?, Cancel, OK]` | Block overlay shown, returned to Home | Handled by PermissionController on Android 14 |
| **4. Device Admin Screen**<br>*(Settings > Security / Special access > Device admin apps > Monk Mode)* | Deactivating Device Administrator to enable uninstallation | `com.android.settings` or `com.oplus.settings` | `com.android.settings.DeviceAdminAdd` or `*DeviceAdmin*` | `[Device admin apps, Monk Mode, Deactivate this device admin app]` | Block overlay shown, returned to Home | Required to prevent launcher uninstallation |
| **5. Accessibility Service Page**<br>*(Settings > Accessibility > Monk Mode)* | Toggling off Accessibility Service | `com.android.settings` or `com.oplus.settings` | `com.android.settings.SubSettings` or `*Accessibility*` | `[Monk Mode, Use Monk Mode, Stop Monk Mode?, Stop]` | Block overlay shown, returned to Home | Critical engine deactivation vector |
| **6. Date & Time**<br>*(Settings > Additional settings > Date & time)* | Setting system clock forward to bypass timer limits | `com.android.settings` or `com.oplus.settings` | `com.android.settings.SubSettings` or `*DateTime*` | `[Date & time, Set time automatically, Date, Time]` | Block overlay shown, returned to Home | Quick settings clock shortcut may also link here |
| **7. VPN Settings**<br>*(Settings > Connection & sharing > VPN)* | Revoking VPN profile or stopping DNS loopback proxy | `com.android.settings` or `com.android.vpndialogs` | `com.android.settings.SubSettings` or `*VpnSettings*` | `[VPN, Monk Mode, Always-on VPN, Disconnect]` | Block overlay shown, returned to Home | Note: Monk Mode's own `ConfirmDialog` must NOT be blocked |
| **8. Private DNS**<br>*(Settings > Connection & sharing > Private DNS)* | Pointing DNS to external provider to bypass loopback filter | `com.android.settings` or `com.oplus.settings` | `com.coui.appcompat.dialog.COUIDialog` or `*PrivateDns*` | `[Private DNS, Off, Auto, Designated Private DNS, Cancel, Save]` | Block overlay shown, returned to Home | Modal dialog on realme UI |

---

## Allowed Screens Baseline (Verification)

Ensure the following screens are **NEVER blocked** and remain completely functional:

| Safe Screen | Target Package | Expected Class | Verified Working? | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **Wi-Fi / Internet** | `com.android.settings` | `*Wifi*` or `*NetworkProvider*` | [ ] Yes / [ ] No | Android 12+ uses `NetworkProviderSettings` |
| **Bluetooth** | `com.android.settings` | `*Bluetooth*` | [ ] Yes / [ ] No | Paired devices, Bluetooth toggle |
| **Display & Brightness** | `com.android.settings` | `*Display*`, `*Brightness*` | [ ] Yes / [ ] No | Brightness slider, Dark mode |
| **Sound & Vibration** | `com.android.settings` | `*Sound*`, `*Volume*` | [ ] Yes / [ ] No | Volume sliders, Ringtone |
