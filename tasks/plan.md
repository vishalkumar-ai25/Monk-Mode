# Implementation Plan: Stay Focused (Personal MVP)

Based on locked specification in [docs/SPEC.md](../docs/SPEC.md).

---

## Dependency Graph

```
Phase 1: Gradle & Room Foundation
    │
    ├── Phase 2: Core Interception Engine (Pure Kotlin) & Compose Overlay
    │       │
    │       ├── Phase 3: UsageStats Tracking & Exact Midnight Alarms
    │       │
    │       └── Phase 4: Local VpnService DNS Proxy
    │
    └── Phase 5: Strict Mode, Multi-Tier Failsafes & OEM Survival
```

---

## Phase 1: Foundation, Build Variants & Room Data Layer

### Task 1.1: Gradle Build System & Build Variants Configuration
- **Description:** Initialize Gradle wrapper, project-level and app-level `build.gradle.kts` targeting `minSdk 26`, `targetSdk 34`, Kotlin 2.0+, Jetpack Compose, Room, Coroutines, and WorkManager. Configure `debug` (anti-tamper disabled) and `release` (anti-tamper enabled) build types sharing a single debug keystore so `adb install -r` preserves Room databases.
- **Acceptance criteria:**
  - `build.gradle.kts` files configure plugins and dependencies without syntax errors.
  - `debug` build defines `buildConfigField("boolean", "ANTI_TAMPER_ENABLED", "false")`.
  - `release` build defines `buildConfigField("boolean", "ANTI_TAMPER_ENABLED", "true")`.
  - Both build types share the debug signing config.
- **Verification:**
  - `./gradlew assembleDebug` succeeds.
- **Dependencies:** None
- **Files touched:**
  - `settings.gradle.kts`
  - `build.gradle.kts`
  - `app/build.gradle.kts`
  - `gradle.properties`

### Task 1.2: Room Database Entities & Post-MVP Schema Stubs
- **Description:** Implement Room entities for MVP (`AppLimitEntity`, `BlockedDomainEntity`, `FocusProfileEntity`, `StrictSessionEntity`, `RecoveryCodeEntity`) and schema stubs for post-MVP (`UnlockEventEntity`, `GeofenceProfileEntity`, `NotificationBlockRuleEntity`).
- **Acceptance criteria:**
  - All MVP entities have appropriate primary keys, non-null types, and defaults.
  - `AppLimitEntity` includes `dailyTimeLimitMinutes`, `dailyLaunchLimit`, `currentDayUsageMs`, `currentDayLaunches`.
  - `RecoveryCodeEntity` stores SHA-256 hashed code.
  - Stubs compile cleanly without breaking Room schema validation.
- **Verification:**
  - JVM unit test verifying entity construction and property defaults.
- **Dependencies:** Task 1.1
- **Files touched:**
  - `app/src/main/java/com/stayfocused/app/data/local/entities/*`

### Task 1.3: Room DAOs & StayFocusedDatabase
- **Description:** Implement Room DAOs (`AppLimitDao`, `BlockedDomainDao`, `FocusProfileDao`, `StrictSessionDao`, `RecoveryCodeDao`) providing reactive `Flow` queries and suspend mutation functions. Implement `StayFocusedDatabase` abstract class with fallbackToDestructiveMigration for personal dev velocity.
- **Acceptance criteria:**
  - DAOs expose CRUD queries and reactive observation flows.
  - In-memory database JVM unit tests verify insertion, retrieval, and updates for each DAO.
- **Verification:**
  - `./gradlew testDebugUnitTest --tests "com.stayfocused.app.data.*"` passes.
- **Dependencies:** Task 1.2
- **Files touched:**
  - `app/src/main/java/com/stayfocused/app/data/local/dao/*`
  - `app/src/main/java/com/stayfocused/app/data/local/StayFocusedDatabase.kt`

### Task 1.4: Config-Driven OEM & Browser Package Registry
- **Description:** Create a config-driven registry for recognized browser packages (Chrome, Firefox, Edge, Brave, Samsung Internet) and OEM system settings packages (AOSP, Samsung, MIUI, ColorOS). Expose via a clean repository interface.
- **Acceptance criteria:**
  - JSON asset / Kotlin registry providing sets of known browser and settings package names.
  - Easily extensible without modifying core interception logic.
- **Verification:**
  - Unit tests verifying package matching logic for common browsers and OEM settings.
- **Dependencies:** Task 1.1
- **Files touched:**
  - `app/src/main/java/com/stayfocused/app/data/registry/PackageRegistry.kt`
  - `app/src/main/assets/package_registry.json`

### Task 1.5: Phase 1 Definition of Done (DoD) Verification
- **Description:** Document and execute the manual on-device checklist for Phase 1 (clean install, database creation, build variant verification).
- **Acceptance criteria:**
  - `tasks/dod/phase1_dod.md` checked and approved.
- **Dependencies:** Tasks 1.1 – 1.4

---

## Phase 2: Core Interception & WindowManager Overlay

### Task 2.1: Pure Kotlin InterceptionDecisionEngine (TDD)
- **Description:** Implement pure Kotlin `InterceptionDecisionEngine` with zero Android framework dependencies. It evaluates incoming package name, current time, active focus profiles, daily usage time, launch count, and strict mode state to produce an `InterceptionResult` (`ALLOW`, `BLOCK_LIMIT_REACHED`, `BLOCK_PROFILE_ACTIVE`, `BLOCK_SETTINGS_TAMPER`).
- **Acceptance criteria:**
  - 100% JVM unit test coverage across all boundary conditions (time expired, launch limit hit, profile schedule active, settings app launched during strict mode vs normal mode).
  - Runs in sub-second time on JVM.
- **Verification:**
  - `./gradlew testDebugUnitTest --tests "com.stayfocused.app.domain.InterceptionDecisionEngineTest"` passes.
- **Dependencies:** Phase 1
- **Files touched:**
  - `app/src/main/java/com/stayfocused/app/domain/InterceptionDecisionEngine.kt`
  - `app/src/test/java/com/stayfocused/app/domain/InterceptionDecisionEngineTest.kt`

### Task 2.2: WindowManager Floating Block Overlay
- **Description:** Build `BlockOverlayManager` displaying a full-screen Compose overlay (`TYPE_APPLICATION_OVERLAY`) with blocked package details, reason, motivational quote, and a "Return to Home" button triggering `GLOBAL_ACTION_HOME`.
- **Acceptance criteria:**
  - Renders cleanly using Material 3 Compose theme.
  - Dismiss button triggers home navigation.
  - Handles overlay permission check safely via `Settings.canDrawOverlays()`.
- **Dependencies:** Task 2.1
- **Files touched:**
  - `app/src/main/java/com/stayfocused/app/ui/overlay/BlockOverlayManager.kt`
  - `app/src/main/java/com/stayfocused/app/ui/overlay/BlockOverlayView.kt`

### Task 2.3: FocusAccessibilityService Thin OS Adapter
- **Description:** Implement `FocusAccessibilityService` registering `TYPE_WINDOW_STATE_CHANGED` events. Forwards foreground package changes to `InterceptionDecisionEngine` and calls `BlockOverlayManager` or `performGlobalAction(GLOBAL_ACTION_HOME)` if blocked.
- **Acceptance criteria:**
  - `accessibility_service_config.xml` configured with `eventTypes="typeWindowStateChanged"`.
  - Respects `BuildConfig.ANTI_TAMPER_ENABLED` (no-op in debug build).
- **Dependencies:** Tasks 2.1, 2.2
- **Files touched:**
  - `app/src/main/java/com/stayfocused/app/service/FocusAccessibilityService.kt`
  - `app/src/main/res/xml/accessibility_service_config.xml`
  - `app/src/main/AndroidManifest.xml`

### Task 2.4: Phase 2 Definition of Done (DoD) Verification
- **Description:** Document and execute manual on-device checklist for Phase 2: verify sub-10ms overlay display when launching blocked app.
- **Acceptance criteria:**
  - `tasks/dod/phase2_dod.md` checked and approved.
- **Dependencies:** Tasks 2.1 – 2.3

---

## Phase 3: Usage Tracking & Exact Midnight Reset

### Task 3.1: UsageStatsTracker Engine
- **Description:** Implement `UsageStatsTracker` wrapping Android's `UsageStatsManager`. Computes cumulative foreground time from 00:00:00 to now and increments launch counts on package transition. Updates `AppLimitEntity`.
- **Acceptance criteria:**
  - Correctly aggregates usage intervals from midnight.
  - JVM unit test with mock data; defensive runtime check for `PACKAGE_USAGE_STATS`.
- **Dependencies:** Phase 2
- **Files touched:**
  - `app/src/main/java/com/stayfocused/app/tracker/UsageStatsTracker.kt`

### Task 3.2: MidnightResetScheduler (Exact Alarms)
- **Description:** Implement `MidnightResetScheduler` using `AlarmManager.setExactAndAllowWhileIdle` targeted at 00:00:00. Resets `currentDayUsageMs` and `currentDayLaunches` in Room DB. Uses `USE_EXACT_ALARM` with runtime check `canScheduleExactAlarms()` falling back to WorkManager if false.
- **Acceptance criteria:**
  - Resets all daily counters at midnight.
  - Automatically reschedules the next midnight alarm upon trigger or device reboot.
- **Dependencies:** Task 3.1
- **Files touched:**
  - `app/src/main/java/com/stayfocused/app/scheduler/MidnightResetScheduler.kt`
  - `app/src/main/java/com/stayfocused/app/scheduler/MidnightResetReceiver.kt`

### Task 3.3: WatchdogWorker & Background Reconciler
- **Description:** WorkManager periodic worker (15-minute interval) that reconciles daily usage stats, checks if `FocusAccessibilityService` is alive, and prompts notification if disabled by OEM.
- **Acceptance criteria:**
  - Runs every 15 minutes; detects dead accessibility service.
- **Dependencies:** Tasks 3.1, 3.2
- **Files touched:**
  - `app/src/main/java/com/stayfocused/app/worker/WatchdogWorker.kt`

### Task 3.4: Phase 3 Definition of Done (DoD) Verification
- **Description:** Verify midnight reset and usage accumulation on device.
- **Dependencies:** Tasks 3.1 – 3.3

---

## Phase 4: Website Blocking via Local VpnService DNS Proxy

### Task 4.1: Architecture Decision Record ADR 001
- **Description:** Document ADR 001 in `docs/adr/001-vpn-dns-proxy-vs-scraping.md` capturing the tradeoff analysis, RFC 1035 packet synthesis, and Private DNS realities.
- **Dependencies:** Phase 1
- **Files touched:**
  - `docs/adr/001-vpn-dns-proxy-vs-scraping.md`

### Task 4.2: Pure Kotlin DnsPacketParser (TDD)
- **Description:** Pure Kotlin UDP/TCP DNS packet parser and serializer. Decodes question domain name; constructs valid DNS response packet with `NXDOMAIN` (RCODE 3) or `0.0.0.0` (A record) including valid IP/UDP headers and checksum calculation.
- **Acceptance criteria:**
  - 100% JVM unit tests parsing sample raw DNS query bytes and asserting synthesized response packet correctness.
- **Dependencies:** Task 4.1
- **Files touched:**
  - `app/src/main/java/com/stayfocused/app/vpn/dns/DnsPacketParser.kt`
  - `app/src/test/java/com/stayfocused/app/vpn/dns/DnsPacketParserTest.kt`

### Task 4.3: Local Loopback DnsVpnService
- **Description:** Implement `DnsVpnService` extending Android's `VpnService`. Configures TUN interface with narrow routing (only DNS address), loops on TUN packets, matches domain against Room `BlockedDomainEntity`, writes forged `NXDOMAIN` or forwards to `1.1.1.1`.
- **Acceptance criteria:**
  - General web traffic never enters TUN interface.
  - Blocked domains fail resolution immediately; allowed domains resolve via upstream.
- **Dependencies:** Task 4.2
- **Files touched:**
  - `app/src/main/java/com/stayfocused/app/vpn/DnsVpnService.kt`

### Task 4.4: Private DNS Handling & TCP 853 Backstop
- **Description:** Onboarding banner alerting user to toggle Android Private DNS to "Off". VPN drops outbound TCP 853 to trigger graceful fallback when Private DNS is in "Automatic" mode.
- **Dependencies:** Task 4.3
- **Files touched:**
  - `app/src/main/java/com/stayfocused/app/vpn/DnsVpnService.kt`
  - `app/src/main/java/com/stayfocused/app/ui/onboarding/PrivateDnsNotice.kt`

### Task 4.5: Phase 4 Definition of Done (DoD) Verification
- **Description:** On-device verification of blocked domains (e.g. reddit.com) failing across Chrome, Firefox, and WebViews while google.com continues working.
- **Dependencies:** Tasks 4.1 – 4.4

---

## Phase 5: Strict Mode, Failsafes & OEM Survival

### Task 5.1: Layered Failsafe: Time-Delayed Unlock & Recovery Code
- **Description:** Implement 24–48h time-delayed unlock double-confirmation flow. Implement emergency recovery code generator displaying 16-character alphanumeric code once, storing SHA-256 hash in `RecoveryCodeEntity`. Entering code cancels strict session immediately.
- **Acceptance criteria:**
  - Recovery code cannot be retrieved once dismissed; hash verification unit-tested.
  - Time-delayed unlock requires explicit elapsed duration before releasing locks.
- **Dependencies:** Phase 3
- **Files touched:**
  - `app/src/main/java/com/stayfocused/app/strict/FailsafeManager.kt`
  - `app/src/test/java/com/stayfocused/app/strict/FailsafeManagerTest.kt`

### Task 5.2: Scoped Boot Grace Period Receiver
- **Description:** Implement `BootCompletedReceiver` that activates a 3–5 minute grace period upon device restart. During this window, Settings-blocking and Device Admin lockout are suspended ONLY; app and website blocks remain strictly active.
- **Acceptance criteria:**
  - Grace period expires after 5 minutes, automatically restoring Settings lock.
  - App blocking rules are unaffected during the grace period.
- **Dependencies:** Task 5.1
- **Files touched:**
  - `app/src/main/java/com/stayfocused/app/receiver/BootCompletedReceiver.kt`
  - `app/src/main/java/com/stayfocused/app/strict/GracePeriodManager.kt`

### Task 5.3: DeviceAdminReceiver & Settings App Interception
- **Description:** Implement `StayFocusedDeviceAdminReceiver` preventing app uninstallation. In `FocusAccessibilityService`, monitor `com.android.settings` during active Strict Mode and immediately trigger Home or block overlay. In `debug` variant, this remains a no-op log statement.
- **Acceptance criteria:**
  - Active admin prevents uninstallation in release variant.
  - Opening settings redirects home while Strict Mode is active (outside grace period).
- **Dependencies:** Tasks 5.1, 5.2
- **Files touched:**
  - `app/src/main/java/com/stayfocused/app/receiver/StayFocusedDeviceAdminReceiver.kt`
  - `app/src/main/res/xml/device_admin.xml`

### Task 5.4: OEM Survival & ADB Escape Hatch Documentation
- **Description:** Battery optimization exemption intent flow (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) + deep-links to OEM autostart screens (Samsung, Xiaomi, Oppo, Vivo). Document ADB recovery commands in `README.md`.
- **Acceptance criteria:**
  - Tested deep-links for major OEM skins.
  - README clearly documents `adb shell pm disable-user` recovery path.
- **Dependencies:** Task 5.3
- **Files touched:**
  - `app/src/main/java/com/stayfocused/app/ui/oem/OemHelper.kt`
  - `README.md`

### Task 5.5: Phase 5 Definition of Done (DoD) Verification
- **Description:** Complete end-to-end audit on physical device across stock, Samsung, and Xiaomi.
- **Dependencies:** Tasks 5.1 – 5.4

---

## Phase 6: Distraction Defense & Quick Breaks (Commercial Parity)

### Task 6.1: Notification Interception Engine & Room Storage (TDD)
- **Description:** Implement `SuppressedNotificationEntity`, `SuppressedNotificationDao`, and pure Kotlin `NotificationDecisionEngine`. Decides whether an incoming notification should be canceled based on active app blocks, active profiles, and safety exceptions (ongoing, foreground service, calls, and system alerts are NEVER canceled).
- **Acceptance criteria:**
  - `NotificationDecisionEngineTest` achieves 100% JVM test coverage across normal, blocked, ongoing, and call-type notifications.
  - `SuppressedNotificationDao` handles insert, query, mark-as-viewed, and age-based purge.
- **Verification:**
  - `./gradlew testDebugUnitTest --tests "com.stayfocused.app.notification.*"` passes.
- **Dependencies:** Phase 5
- **Files touched:**
  - `app/src/main/java/com/stayfocused/app/data/local/entities/SuppressedNotificationEntity.kt`
  - `app/src/main/java/com/stayfocused/app/data/local/dao/SuppressedNotificationDao.kt`
  - `app/src/main/java/com/stayfocused/app/data/local/StayFocusedDatabase.kt`
  - `app/src/main/java/com/stayfocused/app/notification/NotificationDecisionEngine.kt`
  - `app/src/test/java/com/stayfocused/app/notification/NotificationDecisionEngineTest.kt`

### Task 6.2: FocusNotificationListenerService OS Adapter
- **Description:** Implement `FocusNotificationListenerService` extending Android's `NotificationListenerService`. Captures `onNotificationPosted`, checks with `NotificationDecisionEngine`, executes `cancelNotification(sbn.key)` for distracting notifications, and stores summary in the Room vault.
- **Acceptance criteria:**
  - Service registered in `AndroidManifest.xml` with `BIND_NOTIFICATION_LISTENER_SERVICE`.
  - Non-blocking, coroutine-backed Room persistence for intercepted notifications.
- **Verification:**
  - Unit tests verifying service lifecycle and notification processing callbacks.
- **Dependencies:** Task 6.1
- **Files touched:**
  - `app/src/main/java/com/stayfocused/app/service/FocusNotificationListenerService.kt`
  - `app/src/main/AndroidManifest.xml`
  - `app/src/test/java/com/stayfocused/app/service/FocusNotificationListenerServiceTest.kt`

### Task 6.3: Break Engine & Interception Bypass (TDD)
- **Description:** Implement `BreakSessionEntity`, `BreakSessionDao`, and pure Kotlin `BreakDecisionEngine`. Allows taking 5m, 10m, or 15m breaks during non-strict focus sessions. When a break is active, `InterceptionDecisionEngine` grants temporary `ALLOW` to apps.
- **Acceptance criteria:**
  - `BreakDecisionEngineTest` verifies break start, active expiration calculation, early cancellation, and rejection during active strict sessions.
  - Integration with `InterceptionDecisionEngine` verified.
- **Verification:**
  - `./gradlew testDebugUnitTest --tests "com.stayfocused.app.break.*"` passes.
- **Dependencies:** Task 6.1
- **Files touched:**
  - `app/src/main/java/com/stayfocused/app/data/local/entities/BreakSessionEntity.kt`
  - `app/src/main/java/com/stayfocused/app/data/local/dao/BreakSessionDao.kt`
  - `app/src/main/java/com/stayfocused/app/data/local/StayFocusedDatabase.kt`
  - `app/src/main/java/com/stayfocused/app/break/BreakDecisionEngine.kt`
  - `app/src/main/java/com/stayfocused/app/domain/InterceptionDecisionEngine.kt`
  - `app/src/test/java/com/stayfocused/app/break/BreakDecisionEngineTest.kt`

### Task 6.4: Quick Settings Tile (TakeABreakTileService)
- **Description:** Implement `TakeABreakTileService` extending Android's `TileService`. Shows "Take a Break" when idle and updates dynamically with remaining break time when active. Tapping toggles break or initiates break selection.
- **Acceptance criteria:**
  - Registered in manifest with `BIND_QUICK_SETTINGS_TILE`.
  - Accurately synchronizes `qsTile.state` with `BreakDecisionEngine`.
- **Verification:**
  - Unit tests for tile state transitions.
- **Dependencies:** Task 6.3
- **Files touched:**
  - `app/src/main/java/com/stayfocused/app/tile/TakeABreakTileService.kt`
  - `app/src/main/AndroidManifest.xml`
  - `app/src/test/java/com/stayfocused/app/tile/TakeABreakTileServiceTest.kt`

### Task 6.5: Phase 6 DoD Verification & On-Device Testing
- **Description:** Complete end-to-end DoD verification and install on connected physical device (`V49TW4RWQOZ5IFBA`).
- **Acceptance criteria:**
  - `tasks/dod/phase6_dod.md` completed.
  - All unit tests pass across debug and release build variants.
  - Deployed to device via `adb install -r`.
- **Dependencies:** Tasks 6.1 – 6.4

---

## Phase 7: Production-Grade Jetpack Compose UI (Commercial Parity)

### Task 7.1: UI Navigation Framework & App Tabs
- **Description:** Implement a clean Material 3 `NavigationBar` with 5 dedicated screens:
  1. `DashboardScreen`: Daily screen time dial, quick break card, active focus profile banner.
  2. `AppLimitsScreen`: Searchable list with installed application icons, limit sliders, and quick toggle.
  3. `WebBlockerScreen`: Local DNS VPN status, custom domain rules, and one-tap categories.
  4. `NotificationVaultScreen`: Suppressed notification history feed with timestamp, search, and bulk purge.
  5. `StrictLockScreen`: Emergency recovery code generator, anti-uninstall status, and 24–48h delayed unlock.
- **Acceptance criteria:**
  - Responsive tab navigation with state preservation.
  - Clean separation into modular `@Composable` screen files under `com.stayfocused.app.ui.screens`.
- **Verification:**
  - Preview & unit test compiling without errors.
- **Dependencies:** Phase 6
- **Files touched:**
  - `app/src/main/java/com/stayfocused/app/ui/screens/*`
  - `app/src/main/java/com/stayfocused/app/ui/MainActivity.kt`

### Task 7.2: Daily Usage Dial & Take a Break Card
- **Description:** Implement `DailyUsageDial` with smooth Canvas circular progress ring tracking today's screen time against daily target. Implement `TakeABreakCard` observing `BreakSessionDao` with live countdown timer and 5m/10m/15m quick break start buttons.
- **Acceptance criteria:**
  - Correct percentage calculation and color gradation (green -> amber -> red).
  - Tapping break button starts break in Room database; UI updates immediately.
- **Verification:**
  - Unit tests verifying progress math and break state presentation.
- **Dependencies:** Task 7.1

### Task 7.3: Notification History Vault Screen
- **Description:** Implement `NotificationVaultScreen` showing list of notifications suppressed during focus sessions. Includes app label, notification title, message snippet, and timestamp. Provides "Mark all as read" and "Clear all" buttons.
- **Acceptance criteria:**
  - Observes `SuppressedNotificationDao.getAll()` reactively.
  - Shows clean empty state illustration when zero notifications are suppressed.
- **Verification:**
  - Unit test verifying list rendering with sample suppressed notifications.
- **Dependencies:** Task 7.1

### Task 7.4: App Limits Manager with Async Icon Loading
- **Description:** Implement `AppLimitsScreen` with search bar, real app icon loading via coroutines (`PackageManager.getApplicationIcon`), and interactive configuration dialog for daily minutes and launch limits.
- **Acceptance criteria:**
  - Smooth scrolling LazyColumn without UI jank.
  - Saving limits updates Room database immediately.
- **Verification:**
  - Unit test verifying search filter and limit mutations.
- **Dependencies:** Task 7.1

### Task 7.5: Phase 7 DoD Verification & Device Deployment
- **Description:** Complete end-to-end DoD verification and install on physical device (`V49TW4RWQOZ5IFBA`).
- **Acceptance criteria:**
  - `tasks/dod/phase7_dod.md` completed.
  - All unit tests pass across debug and release build variants.
  - Deployed to device via `adb install -r`.
- **Dependencies:** Tasks 7.1 – 7.4
