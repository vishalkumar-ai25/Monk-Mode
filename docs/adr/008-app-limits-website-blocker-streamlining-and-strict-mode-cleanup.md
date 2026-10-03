# ADR 008: App Limits & Website Blocker Streamlining, Real Screen Time Hardening, and Strict Mode Cleanup

## Status
Accepted

## Context
User feedback highlighted four key usability and architectural requirements:
1. **Remove Failsafe Integrity Audit Log & Emergency Recovery Code from Strict Mode**:
   - The user does not want the audit log displayed on the Strict Mode screen.
   - The user requested complete removal of the "Emergency Recovery Code" option from "Defense-in-Depth Safety Valves" to prevent easy backdoors and impulsive unlocking.
2. **App Limits & Website Blocker Accessibility**:
   - Users were unable to easily set daily time limits (e.g. 15 min, 30 min, 1 hr) for apps like YouTube.
   - Previously, adding an app from search defaulted to a full block (0m) without presenting duration presets, and installed apps were hidden unless the user searched.
   - Users expect a unified or seamless tabbed experience between Apps and Websites where they can search or browse any installed app (YouTube, WhatsApp, Instagram, etc.), tap it, and immediately pick from standard limit presets (**15 min**, **30 min**, **1 hr**, **2 hr**, **Block Only**, or custom slider).
3. **Daily Usage Time Tracking & 12:00 AM Reset Hardening**:
   - The user requested robust daily screen time tracking that reliably resets to 0 at 12:00 AM midnight.
   - Ensure `UsageStatsTracker` multi-bucket fallback (`INTERVAL_DAILY`, `INTERVAL_BEST`, `queryAndAggregateUsageStats`) handles OEM quirks, and `syncUsageWithDatabase` automatically catches midnight transitions even if OEM battery optimization delays the exact alarm.

## Decision
1. **Strict Mode Cleanup (`StrictLockScreen.kt`)**:
   - Remove `FailsafeLogCard` and audit log UI from `StrictLockScreen`.
   - Remove "Emergency Recovery Code" generator and redemption fields from `Defense-in-Depth Safety Valves`.
   - Re-index remaining safety valves: (1) Time-Delayed Unlock, (2) Scoped Boot Grace Period, (3) Developer ADB Escape Hatch.
2. **App Limits & Website Blocker Redesign (`AppLimitsScreen.kt`)**:
   - Add a top selector: **Apps** vs **Websites**.
   - Under **Apps**:
     - Quick preset chips for top distracting apps: **YouTube**, **Instagram**, **WhatsApp**, **Chrome**, **Reddit**.
     - Prominent search bar with instant filtering.
     - Full scrollable list of installed launcher apps (showing app icon, label, package name, current limit status).
     - Tapping any app or "Set Limit" opens the `AppLimitConfigDialog` featuring presets: **15 min**, **30 min**, **1 hr**, **2 hr**, **Block Only (0 min)**, and minute slider.
   - Under **Websites**:
     - Quick preset chips for top distracting websites: **youtube.com**, **instagram.com**, **reddit.com**, **twitter.com**, **tiktok.com**, **facebook.com**, **netflix.com**.
     - Custom domain entry with one-tap addition to `blocked_domains`.
     - DNS Shield status badge and Start/Stop control.
3. **Screen Time & 12:00 AM Reset Hardening (`UsageStatsTracker.kt`)**:
   - Enhance `queryForegroundUsage()` with defensive fallbacks: try `queryAndAggregateUsageStats`, fallback to `queryUsageStats(INTERVAL_DAILY)`, fallback to `queryUsageStats(INTERVAL_BEST)`.
   - In `syncUsageWithDatabase(appLimitDao)`, check if `lastResetTimestamp < getStartOfToday()` and automatically reset `currentDayLaunches = 0` and sync `currentDayUsageMs` from today's foreground query.

## Consequences
- Clean, focused Strict Mode UI without cluttered audit logs or recovery code backdoors.
- Instant, intuitive app and website limit configuration for YouTube, WhatsApp, and all installed apps.
- Bulletproof 12:00 AM midnight usage reset and resilient screen time tracking.
