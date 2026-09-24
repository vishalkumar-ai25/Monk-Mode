# Stay Focused

> A native, private, anti-tamper Android digital wellbeing and distraction-elimination engine built for personal sideloaded use on **Realme C65 5G (realme UI 5.0 / Android 14)**.

---

## 1. Project Overview

**Stay Focused** is an open-source, native Kotlin application engineered to eliminate phone addiction, compulsive app switching, and distracting web browsing. Unlike commercial apps bound by Google Play Store policy constraints, Stay Focused enforces uncompromising strict mode controls, low-level DNS filtering, and anti-tamper protections designed exclusively for personal ownership.

### Core Principles
- **Absolute Privacy**: Zero external network telemetry, zero trackers, and zero screen-scraping (`canRetrieveWindowContent="false"`).
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
| **Layer 3** | **Scoped Boot Grace Period** | For 5 minutes after device restart, Settings-blocking and Device Admin lockout are suspended **ONLY**. App blocks and website blocks remain 100% active to prevent reboot abuse. |
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
