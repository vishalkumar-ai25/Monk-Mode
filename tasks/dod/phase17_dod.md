# Definition of Done: Phase 17

## Verification Checklist
- [x] `UsageStatsTracker`:
  - `queryTotalDeviceScreenTimeMs` accurately aggregates today's foreground usage.
  - `queryTopUsedApps` returns top apps with package name, label, and usage duration.
  - Unit tests verify queries with mock usage provider.
- [x] `ProtectionStatusEngine` & `ProtectionHealthChecker`:
  - `ProtectionCheckType.USAGE_ACCESS` properly verifies AppOps `GET_USAGE_STATS`.
  - Fix intent triggers `Settings.ACTION_USAGE_ACCESS_SETTINGS`.
- [x] `DashboardScreen`:
  - Shows prominent "Usage Access Required" banner if permission not granted.
  - Displays actual total device screen time (e.g. 2h 19m) and target dial.
  - Displays "Usage Overview" with top used apps today and quick "Set Limit" button.
- [x] `ArmStrictSessionDialog`:
  - Supports hour presets: `1h`, `2h`, `4h`, `8h`, `12h`.
  - Supports day presets: `1d`, `2d`, `7d`, `15d`, `30d`, `45d`, `60d`, `75d`, `90d`.
  - Supports Exact Expiration Date & Time picker matching Stay Focused app.
- [x] `TimeFormatter`:
  - Formats multi-day remaining times (`X d Y h Z m`).
  - Formats exact expiration dates (`Sat, Oct 3, 02:21 AM`).
  - Unit tests verify boundary cases.
- [x] `AppLimitsScreen`:
  - Preset limit chips (`15 min`, `30 min`, `1 hr`, `2 hr`, `Custom`).
- [x] `FocusAccessibilityService`:
  - Blocks immediately on limit expiry during continuous app usage.
- [x] All unit tests pass (`./gradlew testDebugUnitTest`).
- [x] App builds and runs on physical phone (`V49TW4RWQOZ5IFBA`).
