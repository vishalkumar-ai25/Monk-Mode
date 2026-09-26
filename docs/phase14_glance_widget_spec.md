# Feature Specification: Phase 14 — Home-Screen Glance Widget

## 1. Overview
The Home-Screen Glance Widget provides a glanceable, read-only home-screen widget powered by Jetpack Glance. It mirrors the Dashboard's circular usage dial and displays today's remaining screen time minutes. The widget is refreshed on the existing 15-minute `WatchdogWorker` tick and opens `MainActivity` when tapped, with zero other interactive controls or buttons.

---

## 2. Invariants & Safety Constraints
- **Zero Modifications to Core Enforcers**: No modifications to `vpn/`, `service/`, or `DeviceAdminReceiver`.
- **Zero Network/Cloud Overhead**: Purely local Room database usage queries; no internet access, no remote analytics, no accounts.
- **Strict Read-Only Guarantee**: The widget is purely informative; tapping anywhere on the widget launches `MainActivity`. No toggle controls, no bypass routes, and no settings modifications can occur from the widget.
- **Battery-Conscious Refresh**: Uses `updatePeriodMillis="0"` in `appwidget-provider` metadata to avoid redundant Android OS polling alarms. Instead, it hooks into the existing 15-minute `WatchdogWorker` periodic cycle, plus on-demand refreshes when the user visits the app dashboard.

---

## 3. Architecture & Data Flow

```
+---------------------------------------------------------------+
|                        WatchdogWorker                         |
|             (Runs every 15 minutes in background)             |
+-------------------------------+-------------------------------+
                                |
                                v
+---------------------------------------------------------------+
|                   StayFocusedDatabase (Room)                  |
|                Queries daily usage from limits                |
+-------------------------------+-------------------------------+
                                |
                                v
+---------------------------------------------------------------+
|                   FocusWidgetDataEngine                       |
|   Pure Kotlin: computes remaining minutes, progress, tier    |
+-------------------------------+-------------------------------+
                                |
                                v
+---------------------------------------------------------------+
|                  FocusWidgetDialRenderer                      |
|       Renders circular ring dial Bitmap using Android Canvas   |
+-------------------------------+-------------------------------+
                                |
                                v
+---------------------------------------------------------------+
|                      FocusGlanceWidget                        |
|       Jetpack Glance composable displayed on Android Launcher  |
|            Tap action: Launches MainActivity                  |
+---------------------------------------------------------------+
```

---

## 4. Components & Files to Create / Modify

### 4.1 New Files
1. **`app/src/main/java/com/stayfocused/app/domain/model/FocusWidgetData.kt`**:
   - Immutable data model: `usedMinutes`, `targetMinutes`, `remainingMinutes`, `progress`, `tier` (`ProgressTier`), `remainingText`, `usedText`, `targetText`.
2. **`app/src/main/java/com/stayfocused/app/domain/FocusWidgetDataEngine.kt`**:
   - Pure Kotlin engine computing widget statistics from raw minute usage and daily target.
3. **`app/src/main/java/com/stayfocused/app/widget/FocusWidgetDialRenderer.kt`**:
   - Renders the circular dial progress arc (MonkCardAlt background track, Sage/Ember/Danger colored progress arc) onto an Android `Bitmap`.
4. **`app/src/main/java/com/stayfocused/app/widget/FocusGlanceWidget.kt`**:
   - Jetpack Glance `GlanceAppWidget` composable layout: dark themed card, dial bitmap image, remaining time header, and click action to open `MainActivity`.
5. **`app/src/main/java/com/stayfocused/app/widget/FocusGlanceWidgetReceiver.kt`**:
   - `GlanceAppWidgetReceiver` handling widget lifecycle and triggering updates.
6. **`app/src/main/res/xml/focus_glance_widget_info.xml`**:
   - Widget provider metadata (size, initial layout, preview image).
7. **`app/src/test/java/com/stayfocused/app/domain/FocusWidgetDataEngineTest.kt`**:
   - Unit tests covering zero usage, normal progress, warning tier, critical/exceeded usage, zero target edge cases.
8. **`app/src/test/java/com/stayfocused/app/widget/FocusWidgetDialRendererTest.kt`**:
   - Unit tests verifying bitmap dimensions, format, non-empty rasterization across different progress percentages.

### 4.2 Modified Files
1. **`gradle/libs.versions.toml` & `app/build.gradle.kts`**:
   - Add Jetpack Glance dependencies: `androidx.glance:glance-appwidget` and `androidx.glance:glance-material3`.
2. **`app/src/main/AndroidManifest.xml`**:
   - Register `FocusGlanceWidgetReceiver` as a broadcast receiver with `android.appwidget.action.APPWIDGET_UPDATE`.
3. **`app/src/main/java/com/stayfocused/app/worker/WatchdogWorker.kt`**:
   - Invoke widget refresh callback during the 15-minute periodic watchdog execution.

---

## 5. Edge Cases & Mitigations
1. **Usage Exceeds Target**:
   - If `usedMinutes > targetMinutes`, `remainingMinutes` evaluates to `0`, `progress` is clamped to `1.0f`, tier is `CRITICAL` (MonkDanger), and text indicates `0m left (Goal Exceeded)`.
2. **Zero or Invalid Target**:
   - If `targetMinutes <= 0`, engine defaults gracefully to `120m` fallback without division-by-zero crashes.
3. **Device Boot**:
   - `BootCompletedReceiver` kicks off `WatchdogWorker.enqueuePeriodicWatchdog()`, ensuring the widget is populated immediately following restart.
4. **App Not Initialized**:
   - Database queries return empty list; engine returns `0m used of 120m target (100% remaining)` in `NORMAL` (MonkSage) tier.
