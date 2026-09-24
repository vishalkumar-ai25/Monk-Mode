# System Specification: Stay Focused (Personal Edition)

## 1. Project Context
- **Target App:** "Stay Focused" — a private, native digital wellbeing and anti-distraction Android app.
- **Audience & Deployment:** Personal use only, single device, open source, sideloaded via ADB. This app will NEVER be published to Google Play Store. All Play Console policy, declaration, and disclosure overhead is omitted.
- **Tech Stack:** Kotlin, Jetpack Compose, Material 3, Room Database, Coroutines/Flow, WorkManager, AlarmManager.
- **SDK Targets:** `minSdk 26` (Android 8.0 Oreo), `targetSdk 34+` (Android 14).
- **Core Principle:** Single source of truth. Scope is locked.

---

## 2. Scope Lock: MVP vs Post-MVP

### MVP (Phases 1–5, strictly built and verified in this order)
Each phase must be independently testable on-device with an explicit Definition of Done (DoD) before progressing to the next:
1. **Phase 1: Foundation, Build Variants & Room Data Layer**
   - Package-level app blocking foundation (whole-app block, daily time limit, launch-count limit).
   - `debug` vs `release` build variant split with shared debug signing keystore.
   - Room Database layer with all core entities, DAOs, and post-MVP schema stubs.
   - Config-driven OEM/browser package matching registry.
2. **Phase 2: Core Interception & WindowManager Overlay**
   - Pure-Kotlin `InterceptionDecisionEngine` (100% JVM unit test coverage).
   - Thin `FocusAccessibilityService` OS adapter for sub-10ms foreground package capture.
   - Full-screen floating `BlockOverlayManager` (WindowManager + Jetpack Compose).
3. **Phase 3: Usage Tracking & Exact Midnight Reset**
   - `UsageStatsManager` daily usage aggregator & launch count tracker.
   - `AlarmManager` with `USE_EXACT_ALARM` for exact 00:00:00 resets (runtime check + WorkManager fallback).
   - WorkManager 15-minute background watchdog.
4. **Phase 4: Website Blocking via Local VpnService DNS Proxy**
   - Pure-Kotlin `DnsPacketParser` (100% JVM unit test coverage).
   - Local loopback `VpnService` acting as a DNS proxy (narrow TUN route to VPN IP, synthesis of NXDOMAIN/0.0.0.0 for blocked domains, upstream forwarding to 1.1.1.1 for allowed domains).
   - Private DNS handling (primary onboarding instruction: turn Private DNS Off; secondary backstop: outbound TCP 853 block).
   - ADR 001 documentation.
5. **Phase 5: Strict Mode, Failsafes & OEM Survival**
   - 24–48 hour time-delayed, double-confirmed disable flow.
   - One-time setup emergency recovery code (SHA-256 hashed in `RecoveryCodeEntity`).
   - Scoped Boot Grace Period (3–5 min post-reboot suspends Settings-blocking and Device Admin lockout ONLY; app blocks remain strictly armed).
   - Config-driven Settings-blocking & `DeviceAdminReceiver` anti-uninstall.
   - Battery optimization exemption + OEM autostart deep-link registry.
   - Documented ADB escape hatch.

### Post-MVP (Explicitly Deferred — Do Not Build During MVP)
- Geofenced focus-profile triggers.
- Keyword and adult-content filtering.
- Instagram Reels / YouTube Shorts sub-component scraping.
- Notification blocking.
- Pomodoro ambient sounds.
- Device-unlock-pattern analytics / activity timeline charts.
*Rule:* If any task seems to require a post-MVP feature, stop and ask rather than improvising heuristics.

---

## 3. Website Blocking Architecture Decision (ADR 001 Summary)

### Primary Mechanism: Local VpnService DNS Proxy
- Advertises the tunnel's own address as the DNS server via `addDnsServer()`.
- Scopes routes narrowly to that address so general IP traffic never enters the TUN and does not need forwarding at all — only DNS traffic does.
- Parses incoming UDP (and TCP) port-53 packets for the query domain.
- **If domain is in `BlockedDomainEntity`:** Synthesizes an `NXDOMAIN` or `0.0.0.0` response packet (valid IP/UDP headers + checksums) and writes it back to the TUN file descriptor immediately.
- **If domain is allowed:** Forwards the query to an upstream resolver (`1.1.1.1` / `8.8.8.8`) and relays the real response back to TUN. Does not drop allowed queries.
- Packet construction follows established patterns from open-source local-VPN blockers (NetGuard, DNS66).

### Private DNS (DoT / DoH) Handling
- **Primary Fix (Required):** Onboarding instructs user to set Android's Private DNS setting to "Off".
- **Backstop:** The VPN blocks outbound TCP 853 to trigger graceful fallback to plaintext DNS when Private DNS is set to "Automatic" (with explicit user note that Strict mode pinned to a hostname requires manual toggle).

### Secondary / Supplementary Mechanism
- Optional best-effort `AccessibilityService` address-bar inspection for major browsers (Chrome, Firefox, Edge, Brave, Samsung Internet) solely to display a contextual "Why this site was blocked" overlay. Never the primary blocking layer.

---

## 4. Room Data Model

### MVP Entities
1. **`AppLimitEntity`**:
   - `packageName: String` (PK)
   - `appName: String`
   - `dailyTimeLimitMinutes: Int` (0 = no limit)
   - `dailyLaunchLimit: Int` (0 = no limit)
   - `isBlocked: Boolean`
   - `currentDayUsageMs: Long`
   - `currentDayLaunches: Int`
   - `lastResetTimestamp: Long`
2. **`BlockedDomainEntity`**:
   - `domain: String` (PK, e.g. "reddit.com", "instagram.com")
   - `isBlocked: Boolean`
   - `category: String`
   - `createdAt: Long`
3. **`FocusProfileEntity`**:
   - `id: Long` (PK autoincrement)
   - `name: String`
   - `isActive: Boolean`
   - `isStrictMode: Boolean`
   - `blockedPackagesJson: String`
   - `blockedDomainsJson: String`
   - `scheduleStartTime: String?` (e.g. "09:00")
   - `scheduleEndTime: String?` (e.g. "17:00")
   - `activeDaysMask: Int` (bitmask for Mon-Sun)
4. **`StrictSessionEntity`**:
   - `id: Long` (PK autoincrement)
   - `profileId: Long`
   - `startTime: Long`
   - `targetEndTime: Long`
   - `delayedUnlockRequestTime: Long?` (null if no unlock requested)
   - `delayedUnlockDurationMs: Long` (24h or 48h)
   - `isActive: Boolean`
5. **`RecoveryCodeEntity`**:
   - `id: Int` (PK, singleton = 1)
   - `hashedCode: String` (SHA-256)
   - `isConsumed: Boolean`
   - `createdAt: Long`

### Post-MVP Schema Stubs (Schema Only, No DAOs/Wired Logic)
6. **`UnlockEventEntity`**: `id`, `timestamp`, `durationMs`
7. **`GeofenceProfileEntity`**: `id`, `profileId`, `latitude`, `longitude`, `radiusMeters`
8. **`NotificationBlockRuleEntity`**: `id`, `packageName`, `filterRegex`, `blockAll`

---

## 5. Build Variants & Developer Loop Protection

Two distinct build types:
- **`debug`**:
  - `BuildConfig.ANTI_TAMPER_ENABLED = false`
  - Device Admin activation, Settings-blocking, and hard overlay locks are no-ops (logging only).
  - Iterative rebuild and reinstall during development never locks out the developer.
- **`release`**:
  - `BuildConfig.ANTI_TAMPER_ENABLED = true`
  - Full anti-tamper enforcement live for daily self-control.
- **Shared Signing Keystore**: Both variants sign with a checked-in debug keystore so `adb install -r` updates in place without wiping Room databases.
- **Play Protect Notice**: Sideloaded APKs triggering "Unsafe app" due to Accessibility + Device Admin is documented in README with tap-through steps.

---

## 6. Platform & OEM Realities

1. **Foreground Service:**
   - Declared with `foregroundServiceType="specialUse"`.
   - Manifest includes `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` justification string for Android 14+.
   - Persistent non-dismissible notification while active.
2. **Exact Alarms:**
   - Declared `USE_EXACT_ALARM` in manifest (auto-granted at install on API 33+ without settings round-trip).
   - Defensively checks `AlarmManager.canScheduleExactAlarms()` at runtime; falls back to WorkManager if false on OEM skins.
3. **Overlay Permission:**
   - Implements Android 12+ runtime flow via `ACTION_MANAGE_OVERLAY_PERMISSION`.
4. **Battery / OEM Kill Resistance:**
   - Battery optimization exemption flow (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
   - Onboarding deep-links to autostart/protected-app settings for Xiaomi (MIUI/HyperOS), Oppo, Vivo, Samsung.
   - WorkManager periodic watchdog (15-min interval) detecting killed services and prompting re-enablement.
5. **Config-Driven Registry:**
   - System settings package names and browser package names are config-driven (JSON/Room), not hardcoded constants.

---

## 7. Failsafe Architecture (Layered)

- **Layer 1: Normal Disable Path:** 24–48 hour time-delayed, double-confirmed unlock request.
- **Layer 2: Emergency Recovery Code:** Generated and shown once during initial Strict Mode setup. Stored strictly as SHA-256 hash in `RecoveryCodeEntity`. Entering correct code force-disables active session immediately.
- **Layer 3: Scoped Boot Grace Period:** For 3–5 minutes after device restart, Settings-blocking and Device Admin lockout are suspended ONLY. All app/domain blocking rules remain strictly armed so reboots cannot be abused for unrestricted access.
- **Layer 4: Developer ADB Path:** Documented commands in README:
  ```bash
  adb shell pm disable-user --user 0 com.stayfocused.app
  adb shell dpm remove-active-admin com.stayfocused.app/.service.StayFocusedDeviceAdminReceiver
  ```

---

## 8. Testing Strategy & Separation of Concerns

- **Pure Kotlin Logic (100% JVM Unit Test Coverage):**
  - `InterceptionDecisionEngine`: Evaluates whether a package/domain should be blocked based on limits, active profiles, launch counts, and strict session state.
  - `DnsPacketParser`: Decodes raw UDP/TCP DNS bytes into query domains and synthesizes binary `NXDOMAIN` / `0.0.0.0` responses with valid IP/UDP headers and checksums.
- **OS Adapters (Instrumented UI Automator & Manual DoD):**
  - `FocusAccessibilityService`: Thin adapter calling `InterceptionDecisionEngine`.
  - `DnsVpnService`: Thin network loop calling `DnsPacketParser`.
  - `BlockOverlayManager`: Thin `WindowManager` view presenter.
- **Manual Device Matrix:** Stock/Pixel, Samsung One UI, Xiaomi MIUI/HyperOS.
