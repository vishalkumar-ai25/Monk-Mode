# Stay Focused

> A native, private, anti-tamper Android digital wellbeing and distraction-elimination engine built for personal sideloaded use on **Realme C65 5G (realme UI 5.0 / Android 14)**.

---

## 1. Project Overview

**Stay Focused** is an open-source, native Kotlin application engineered to eliminate phone addiction, compulsive app switching, and distracting web browsing. Unlike commercial apps bound by Google Play Store policy constraints, Stay Focused enforces uncompromising strict mode controls, low-level DNS filtering, and anti-tamper protections designed exclusively for personal ownership.

### Core Principles
- **Code-Scoped Privacy & Zero Scraping on User Apps**: Zero external network telemetry, zero analytics/trackers, and strictly code-scoped accessibility inspection. Window content inspection (`canRetrieveWindowContent="true"`) is strictly confined in code to system Settings and package installer dialogs solely for anti-tamper enforcement (preventing force-stop, app data clearing, Device Admin deactivation, clock manipulation, and uninstallation during active Strict Mode). For all personal and third-party user applications (browsers, messaging, banking, social media), Monk Mode guarantees zero screen-scraping—only package transitions are observed.
- **Sub-10ms Interception**: Real-time app interception via an in-memory hot cache.
- **Universal Website Blocking**: Loopback DNS Proxy VPN intercepts web domains system-wide across Chrome, Firefox, Brave, and embedded WebViews.
- **Negligible Battery Overhead**: Narrow routing (`10.0.0.2/32`) ensures general web traffic (HTTPS, streaming, sockets) never enters the VPN tunnel.
- **Layered Failsafes**: Multi-tier architecture preventing impulsive overrides while guaranteeing against lockouts.

---

## 2. Layered Failsafe Architecture

Stay Focused utilizes a 4-layer defense-in-depth model:

| Layer | Mechanism | Scope & Behavior |
| :--- | :--- | :--- |
| **Layer 1** | **Time-Delayed Unlock** | 24–48 hour delay before release locks are relaxed. Requires double-confirmation. |
| **Layer 2** | **Emergency Recovery Code** | 16-character alphanumeric code (`XXXX-XXXX-XXXX-XXXX`) generated once during setup. Stored as a PBKDF2WithHmacSHA256 hash with 16-byte random salt. Entering code cancels all active strict sessions immediately. Single-use only. |
| **Layer 3** | **Scoped Boot Grace Period** | 5-minute post-reboot window suspending Settings-blocking and Device Admin lockout to resolve startup crashes. **Applies ONLY if no strict session was active at boot.** If a strict session was active across reboot, zero grace is granted (escape hatches: Layer 2 Emergency Code or Layer 4 ADB). Package updates (`MY_PACKAGE_REPLACED`) do not grant grace. App and website blocks remain 100% active. |
| **Layer 4** | **Developer ADB Escape Hatch** | Command-line emergency override via USB debugging without requiring device rooting. |

---

## 3. Developer ADB Escape Hatch

If you are ever locked out or need to disable Stay Focused during development or emergencies, run the following commands via `adb`:

```bash
# 1. Disable the application immediately
adb shell pm disable-user --user 0 com.stayfocused.app

# 2. Deactivate Device Administrator
adb shell dpm remove-active-admin com.stayfocused.app/.receiver.StayFocusedDeviceAdminReceiver

# 3. Force stop any running services
adb shell am force-stop com.stayfocused.app

# 4. Uninstall the application (optional)
adb shell pm uninstall com.stayfocused.app
```

To re-enable the application after disabling:
```bash
adb shell pm enable --user 0 com.stayfocused.app
```

---

## 4. Setup & Installation on Realme C65 5G (realme UI 5.0)

### Prerequisites
- Realme C65 5G connected via USB cable.
- Developer options & USB debugging enabled:
  - Settings $\to$ About device $\to$ Version $\to$ Tap **Build number** 7 times.
  - Settings $\to$ Additional settings $\to$ Developer options $\to$ Enable **USB debugging**.

### Step 1: Build & Install APK
```bash
# Build and install debug variant
./gradlew installDebug

# Or install manually via ADB:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> **Google Play Protect Notice**: If Android displays a "Blocked by Play Protect" warning for the sideloaded APK, tap **More details** $\to$ **Install anyway**.

### Step 2: Grant Core OS Permissions via ADB
To bypass manual UI dialogs on realme UI 5.0:

```bash
# 1. Grant Usage Access (Screen time and launch tracking)
adb shell appops set com.stayfocused.app GET_USAGE_STATS allow

# 2. Grant Overlay Permission (Floating block overlay)
adb shell appops set com.stayfocused.app SYSTEM_ALERT_WINDOW allow

# 3. Grant Notification Permission (Android 13+)
adb shell pm grant com.stayfocused.app android.permission.POST_NOTIFICATIONS

# 4. Request Battery Optimization Exemption
adb shell dumpsys deviceidle whitelist +com.stayfocused.app

# 5. Enable Accessibility Service
adb shell settings put secure enabled_accessibility_services com.stayfocused.app/.service.FocusAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

### Step 3: Configure Android Private DNS
Android's Private DNS (DNS-over-TLS via port 853) encrypts queries to external servers, bypassing local VPN loopback filtering.
1. Open phone **Settings** $\to$ **Connection & sharing** (or **Network & internet**).
2. Tap **Private DNS**.
3. Select **Off**.
*(Note: If Private DNS is left on "Automatic", Stay Focused drops outbound TCP 853 to trigger graceful fallback to UDP 53).*

### Step 4: Configure OEM Autostart & Background Running (realme UI 5.0)
To prevent realme UI's aggressive battery manager from terminating background workers:
1. Open **Settings** $\to$ **Battery** $\to$ **More settings** $\to$ **App battery management** $\to$ **Stay Focused**:
   - Turn ON **Allow background activity**.
   - Turn ON **Allow auto-launch**.
2. Lock Stay Focused in the Recent Apps carousel:
   - Swipe up to Recent Apps $\to$ Tap the three dots (⋮) above Stay Focused $\to$ Tap **Lock**.

---

## 5. Architecture Summary

```
                      +-----------------------------------+
                      |       FocusAccessibilityService   |
                      |  (Foreground Package Transition)  |
                      +-----------------+-----------------+
                                        |
               +------------------------+------------------------+
               v                                                 v
+-----------------------------+                   +-----------------------------+
|  UsageStatsTracker Engine   |                   |  InterceptionDecisionEngine |
|  - Daily foreground time    |                   |  - In-memory hot cache      |
|  - Midnight reset (Alarm)   |                   |  - Anti-tamper & limits     |
+-----------------------------+                   +--------------+--------------+
                                                                 |
                                                                 v
                                                  +-----------------------------+
                                                  |     BlockOverlayManager     |
                                                  | (WindowManager + Compose M3)|
                                                  +-----------------------------+

                      +-----------------------------------+
                      |      DnsVpnService (Local TUN)    |
                      |     Narrow Route: 10.0.0.2/32     |
                      +-----------------+-----------------+
                                        |
               +------------------------+------------------------+
               v                                                 v
    [Blocked Domain]                                      [Allowed Domain]
           |                                                     |
           v                                                     v
+-----------------------------+                   +-----------------------------+
| Pure Kotlin DnsPacketParser |                   | Protected Upstream Resolver |
| Synthesizes NXDOMAIN/0.0.0.0|                   |   Forward to 1.1.1.1:53     |
+-----------------------------+                   +-----------------------------+
```

### Privacy Architecture & Accessibility Scoping Evaluation

During security architecture evaluation ([ADR 012](docs/adr/012-settings-tamper-detection-and-accessibility-scoping.md)), configuring a static `android:packageNames` filter in `accessibility_service_config.xml` (e.g. limiting the service exclusively to settings and installer packages) was thoroughly evaluated. When `android:packageNames` is defined statically in the service configuration XML, the Android OS drops window state accessibility events for all unlisted packages. This would completely break App Limits and foreground time tracking for user applications (Instagram, Chrome, YouTube, etc.).

To resolve this while preserving ironclad user privacy:
1. **Global Event Listening, Zero User Content Scraping**: The accessibility configuration omits static `android:packageNames` so package transitions can be tracked across all user apps to enforce usage limits.
2. **Code-Level Privacy Boundary**: `FocusAccessibilityService` checks `isTargetSettingsOrInstaller` before ever querying `rootInActiveWindow`.
   - **Settings & Installer Windows**: Inspected strictly for tamper detection (Force stop buttons, Clear data actions, Device admin deactivation, Date/Time changes, and Uninstallation prompts).
   - **User & Personal Apps**: Window content, text, views, and inputs are **NEVER** inspected, queried, or scraped. Only the package name (`event.packageName`) is evaluated against local database limits.

---

## 6. Build & Test Commands

```bash
# Run complete unit test suite across both debug and release variants
./gradlew test

# Run tests with clean rerun
./gradlew test --rerun-tasks

# Build Debug APK
./gradlew assembleDebug

# Build Signed Release APK
./gradlew assembleRelease
```

---

## 7. License
Apache 2.0 / Private Personal Use.
