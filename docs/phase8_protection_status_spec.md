# Feature Specification: Phase 8 — Protection Status Card & Watchdog Health Alerts

## 1. Context & Objectives
In Stay Focused (Monk Mode), background enforcement depends on four critical OS subsystems:
1. **FocusAccessibilityService**: Foreground app detection and WindowManager block overlay interception.
2. **DnsVpnService**: Local loopback DNS proxy intercepting blocked domains and enforcing DNS-level blocking.
3. **Battery Optimization Exemption**: Protection against OEM aggressive background process killers (Samsung, Xiaomi MIUI/HyperOS, Oppo, etc.).
4. **WorkManager Watchdog (`WatchdogWorker`)**: 15-minute periodic watchdog reconciler enforcing daily usage sync, missed midnight resets, and service survival.

The objective of **Feature #1 (Protection Status card)** is to provide:
- A unified, calm, reactive **Protection Status card** on the Dashboard displaying aggregate system health (Green / Amber / Red).
- An itemized status breakdown for each of the 4 subsystems with a **one-tap fix action** whenever a check fails.
- Guaranteed background monitoring: both on Dashboard open and on every 15-minute WorkManager watchdog tick.
- High-priority system alert notification posted within 15 minutes whenever the system drops into a **RED** protection failure state.

---

## 2. Subsystem Boundaries & Anti-Tamper Isolation
- **`vpn/` package**: **NO changes**. Reads `DnsVpnService.isVpnRunning` and Android `VpnService.prepare(context)`. No VPN logic or DNS packet handling is touched.
- **`service/` package**: **NO changes**. Reads accessibility status via standard Android `AccessibilityManager` and `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`.
- **`receiver/` & `strict/`**: **NO changes**. Device Admin and strict mode lockouts are untouched.
- **`worker/` package**: `WatchdogWorker` is extended to record its execution timestamp and evaluate protection health on every tick, triggering an alert notification if in a RED state.

---

## 3. State & Evaluation Matrix

### Check Evaluation Logic:
1. **Accessibility Shield (`ACCESSIBILITY`)**:
   - **PASS**: `AccessibilityManager` or `Settings.Secure` reports `FocusAccessibilityService` enabled.
   - **FAIL (CRITICAL)**: Service disabled or killed by OS/user.
   - *Fix action*: Launch `Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)`.

2. **DNS VPN Tunnel (`VPN`)**:
   - **PASS**: `DnsVpnService.isVpnRunning == true` AND `VpnService.prepare(context) == null`.
   - **WARN / FAIL**: If blocked domains exist in DB but VPN is not running, or if another VPN app superseded `DnsVpnService` (`VpnService.prepare(context) != null`). If no blocked domains are configured, non-running VPN is considered PASS / neutral.
   - *Fix action*: Launch VPN prepare intent or start `DnsVpnService`.

3. **Battery Optimization Exemption (`BATTERY_OPTIMIZATION`)**:
   - **PASS**: `PowerManager.isIgnoringBatteryOptimizations(packageName) == true`.
   - **WARN**: Exemption not granted (system at risk of OEM killing background services).
   - *Fix action*: Launch `Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))`.

4. **Watchdog Liveness (`WATCHDOG`)**:
   - **PASS**: Last watchdog execution occurred within the last 30 minutes (`<= 30 min`).
   - **WARN**: Watchdog has never run yet (`lastRun == 0`, first launch) or ran between 30 and 60 minutes ago.
   - **FAIL (CRITICAL)**: Watchdog has not run for > 60 minutes (WorkManager periodic job killed or stalled by system).
   - *Fix action*: Enqueue immediate one-time `WatchdogWorker` execution via WorkManager.

### Overall Status Aggregation:
- **RED**: ANY critical check FAILS (e.g. Accessibility disabled, or Watchdog stalled > 60m, or VPN superseded while domains blocked).
- **AMBER**: No checks FAIL, but at least one check is in WARN state (e.g. Battery optimization not exempted, or initial watchdog pending).
- **GREEN**: All checks PASS.

---

## 4. Notification SLA on Watchdog Tick
- Every 15 minutes, `WatchdogWorker.doWork()` performs the 4 health checks using `ProtectionStatusEngine`.
- If the evaluated status is **RED**, `WatchdogWorker` posts a high-priority alert notification (`NOTIFICATION_ID = 2002`) alerting the user that protection is compromised, with a `PendingIntent` directing to `MainActivity` / Dashboard to resolve it.
- This ensures any background failure (e.g., OEM killing accessibility service) triggers a notification within 15 minutes of the failure occurring.

---

## 5. Architecture & Components
1. **Pure Kotlin Domain Models & Decision Engine** (`app/src/main/java/com/stayfocused/app/domain/`):
   - `ProtectionModels.kt`: Data classes for `ProtectionCheckType`, `ProtectionCheckStatus`, `ProtectionCheckResult`, `ProtectionOverallStatus`, `ProtectionStatusSnapshot`.
   - `ProtectionStatusEngine.kt`: Pure Kotlin evaluation functions (`evaluate(checks): ProtectionStatusSnapshot`). Fully decoupled from Android framework for 100% JVM testability.
2. **Persistence / State Tracking** (`app/src/main/java/com/stayfocused/app/util/ProtectionPreferences.kt`):
   - Tracks `lastWatchdogRunTimestamp`, `lastNotifiedRedStatusTimestamp`.
3. **OS Health Checker** (`app/src/main/java/com/stayfocused/app/domain/ProtectionHealthChecker.kt`):
   - Inspects Android OS services (`AccessibilityManager`, `PowerManager`, `VpnService`, `ProtectionPreferences`) and compiles `List<ProtectionCheckResult>`.
4. **UI Presentation** (`app/src/main/java/com/stayfocused/app/ui/components/ProtectionStatusCard.kt`):
   - Monk Mode card styled with `MonkCard`, `18.dp` corners, `1.dp MonkLine` border, displaying overall status badge (`sage` for Green, `ember` for Amber, `danger` for Red), expandable or flat subsystem status list with one-tap fix buttons.
