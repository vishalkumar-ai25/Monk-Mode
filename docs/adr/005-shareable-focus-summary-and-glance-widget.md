# ADR 005: On-Device Shareable Focus Summary Card and Glance Home Screen Widget

## Status
Accepted

## Context and Problem Statement
Stay Focused (Monk Mode) is designed to cultivate intentional device usage through strict cognitive boundaries and anti-relapse mechanics.
Two user-facing visibility and motivation features were proposed in the community contribution (PR #1):
1. **Shareable Focus Summary Card (Phase 13)**: Users want to share their daily discipline and mindfulness achievements (screen time vs. budget, distractions blocked, active profile) on messaging or social apps without exposing sensitive private app names, browsing logs, or personal usage history.
2. **Glance Home Screen Widget (Phase 14)**: Users want at-a-glance visibility into their daily screen time allowance and protection status directly from their Android home screen without having to open the app.

Integrating these capabilities introduces architectural, security, and battery efficiency constraints:
- **Zero Cloud / 100% Offline Privacy**: How do we render high-resolution social cards without sending any user data to external servers or cloud rendering APIs?
- **Scoped File Sharing**: How do we dispatch images to external apps via `Intent.ACTION_SEND` without leaking private app directories, avoiding world-readable file modes, and preventing external apps from accessing persistent app storage?
- **Battery Preservation**: How do we keep the home-screen widget updated throughout the day without setting frequent OS alarm polls that drain battery or violate Android vitals guidelines?
- **Strict Mode Anti-Relapse Invariant**: How do we guarantee that home-screen widget interactions cannot be leveraged as an escape hatch to bypass active Strict Mode sessions or alter app limits?

## Decision Drivers
- **Strict Mode Anti-Relapse Invariant**: The home-screen widget must be strictly read-only and informative. Tapping anywhere on the widget must route exclusively to `MainActivity`. It must contain zero action buttons, bypass controls, or profile toggle toggles.
- **100% On-Device Rendering & Privacy**: Card generation and widget rendering must execute 100% locally on-device. The shared card must display aggregate metrics only (used minutes, target minutes, blocked distractions, profile status) and strictly redact individual package names, notifications, or domain queries.
- **Minimal Battery Footprint**: The widget provider must declare `android:updatePeriodMillis="0"` to disable Android OS widget polling alarms. Updates must occur opportunistically: piggybacking on the existing 15-minute `WatchdogWorker` reconciliation cycle and on foreground resume.
- **Secure Scoped FileProvider Sharing**: Generated card images must be stored exclusively in a dedicated temporary cache directory (`context.cacheDir/shared_images`) and shared using `androidx.core.content.FileProvider` with `FLAG_GRANT_READ_URI_PERMISSION`.
- **Pure Domain Decoupling & Testability**: Business logic (progress computation, warning tier evaluation, duration formatting) must reside in a pure Kotlin engine (`FocusWidgetDataEngine`), completely separated from Android Canvas and Jetpack Glance UI components for millisecond JVM unit testing.

## Considered Options

### 1. Focus Summary Image Generation
- **Option A: View/Composable Snapshotting (`View.draw()` or Compose capture)**:
  - *Cons*: Requires an attached, measured window; results vary widely across device screen sizes, aspect ratios, and font scales; potential UI thread stutter during capture.
- **Option B: Dedicated Canvas-Based Bitmap Renderer (`FocusSummaryBitmapGenerator`)**:
  - *Pros*: Generates a standardized, high-resolution 1080x1350 (4:5 social aspect ratio) card deterministically regardless of device screen size; renders off-thread without UI jank; easily testable in Robolectric.
  - *Decision*: **Option B**.

### 2. File Sharing Mechanism
- **Option A: Public external storage (`Environment.getExternalStoragePublicDirectory`)**:
  - *Cons*: Requires storage permissions; leaves permanent orphaned files in user media; accessible to all apps on device.
- **Option B: Internal Cache Directory with AndroidX `FileProvider`**:
  - *Pros*: Scoped to `context.cacheDir/shared_images/`; temporary and automatically purged by OS under storage pressure; grants short-lived, read-only content URIs via `FLAG_GRANT_READ_URI_PERMISSION`; requires zero storage permissions.
  - *Decision*: **Option B**.

### 3. Home Screen Widget Framework & Refresh Strategy
- **Option A: Legacy RemoteViews with 30-minute system alarm polling (`updatePeriodMillis="1800000"`)**:
  - *Cons*: Inflexible XML layout limitations; system polling alarms wake up device unnecessarily, causing battery drain; delayed synchronization with actual usage stats.
- **Option B: Jetpack Glance Material 3 with `updatePeriodMillis="0"` + Watchdog Piggybacking**:
  - *Pros*: Modern declarative Glance composables with Material 3 theming; zero background wake-up alarms (`updatePeriodMillis="0"`); synchronized every 15 minutes by `WatchdogWorker` and immediately upon returning to the dashboard.
  - *Decision*: **Option B**.

## Decision Outcome
1. **Shareable Focus Summary (Phase 13)**:
   - `DailyFocusSummaryData`: Immutable domain model computing focus score percentage and budget status.
   - `FocusSummaryBitmapGenerator`: Canvas renderer generating 1080x1350 ARGB_8888 bitmap with Monk Mode design tokens (`MonkInk`, `MonkCard`, `MonkEmber`, `MonkSage`, `MonkDanger`).
   - `FocusSummaryShareManager`: Writes bitmap to `cacheDir/shared_images/daily_focus_summary.png` and launches `Intent.createChooser` with `Intent.ACTION_SEND` and `FLAG_GRANT_READ_URI_PERMISSION`.
   - FileProvider declared in `AndroidManifest.xml` referencing `@xml/file_paths` (`<cache-path name="shared_images" path="shared_images/" />`).
   - "Share Today" action button integrated into the Daily Usage card in `DashboardScreen`.

2. **Home-Screen Glance Widget (Phase 14)**:
   - `FocusWidgetData` & `FocusWidgetDataEngine`: Pure Kotlin model and decision engine computing progress, remaining minutes, and `ProgressTier` (`NORMAL`, `WARNING`, `CRITICAL`).
   - `FocusWidgetDialRenderer`: Canvas renderer generating circular ring dial bitmap with colored progress arc.
   - `FocusGlanceWidget` & `FocusGlanceWidgetReceiver`: Glance AppWidget displaying dial bitmap, remaining time header, and subtitle. Read-only: tapping the widget executes `actionStartActivity<MainActivity>()`.
   - Provider XML: `res/xml/focus_glance_widget_info.xml` configured with `updatePeriodMillis="0"` and initial layout `@layout/widget_loading`.
   - `WatchdogWorker` triggers `FocusGlanceWidgetReceiver.triggerUpdateAsync(applicationContext)` on every 15-minute background run.
