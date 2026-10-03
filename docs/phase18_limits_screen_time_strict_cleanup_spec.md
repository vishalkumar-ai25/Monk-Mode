# Phase 18 Specification: App Limits & Website Blocker Streamlining, Screen Time Tracking, and Strict Mode Cleanup

## 1. Objectives
1. **Strict Mode Cleanup**:
   - Completely remove `Failsafe Integrity Audit Log` card from the Strict Mode screen (`StrictLockScreen.kt`).
   - Completely remove `Emergency Recovery Code` option from `Defense-in-Depth Safety Valves` in `StrictLockScreen.kt`.
2. **App & Website Limits Experience**:
   - Provide an intuitive, full-featured App Limits & Website Blocker screen (`AppLimitsScreen.kt`).
   - Enable users to easily pick or search any installed app (specifically YouTube, Instagram, WhatsApp, Chrome, Reddit, etc.) and configure a daily usage limit with one-tap presets (**15 min**, **30 min**, **1 hr**, **2 hr**, **Block Only**) and custom slider.
   - Provide direct website blocking (e.g. `youtube.com`, `instagram.com`, `facebook.com`) so users can prevent browser-based bypassing.
3. **Daily Usage Tracking & 12:00 AM Reset Hardening**:
   - Ensure accurate screen time measurement starting at `00:00:00` today.
   - Ensure reliable reset at 12:00 AM midnight via exact alarm and opportunistic synchronization when `lastResetTimestamp < getStartOfToday()`.

## 2. Technical Design

### 2.1 StrictLockScreen.kt
- Remove `failsafeLogs` Flow collection.
- Remove `FailsafeLogCard` item.
- Remove `generatedRecoveryCode`, `enteredRecoveryCode`, and `Layer 2: Emergency Recovery Code` UI and dialogs.
- Re-index remaining layers to:
  - 1: Time-Delayed Unlock
  - 2: Scoped Boot Grace Period
  - 3: Developer ADB Escape Hatch

### 2.2 AppLimitsScreen.kt
- Dual-segment selector: `[ Apps ]` and `[ Websites ]`.
- **Apps Segment**:
  - Quick distraction presets: **YouTube**, **Instagram**, **WhatsApp**, **Chrome**, **Reddit**. Tapping any opens the limit dialog immediately.
  - Search field with real-time matching against app name and package name.
  - Installed launcher apps list with app icon, app label, package name, and current limit badge.
  - "Set Limit" / "Edit Limit" button on each row opening `AppLimitConfigDialog`.
  - `AppLimitConfigDialog`:
    - Preset chips: `15 min`, `30 min`, `1 hr`, `2 hr`, `Block Only (0 min)`.
    - Granular slider for custom minute selection.
    - Saves directly to Room DB (`appLimitDao.upsertAppLimit`).
- **Websites Segment**:
  - Quick domain chips: `youtube.com`, `instagram.com`, `reddit.com`, `twitter.com`, `tiktok.com`, `facebook.com`, `netflix.com`.
  - Custom domain text field with "Block Domain" button.
  - DNS Shield status indicator with toggle.
  - Blocked domains list with remove/unblock actions (guarded by Strict Mode invariant).

### 2.3 UsageStatsTracker.kt
- Defensive multi-tier query for foreground usage:
  1. `queryAndAggregateUsageStats(startTime, endTime)`
  2. `queryUsageStats(INTERVAL_DAILY, startTime, endTime)`
  3. `queryUsageStats(INTERVAL_BEST, startTime, endTime)`
- Automatic midnight catch-up in `syncUsageWithDatabase`:
  If `limit.lastResetTimestamp < startOfToday`, reset `currentDayLaunches = 0` and update `lastResetTimestamp = startOfToday`.
