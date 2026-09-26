# Feature Specification: Phase 13 — Shareable Focus Summary

## 1. Context & Objectives
In Monk Mode (Stay Focused), user motivation is reinforced by celebrating daily focus achievements and mindful screen time control.
The objective of **Feature #8 (Shareable focus summary)** is to provide an on-device, privacy-preserving sharing feature:
1. **On-Device Bitmap Generation**: Renders the daily circular dial, screen time usage vs. daily target, blocked distractions count, and active profile into a standalone high-resolution `Bitmap` card matching Monk Mode's dark aesthetic (`MonkInk`, `MonkCard`, `MonkEmber`, `MonkSage`).
2. **OS Share Sheet Integration**: Exports the generated image to the application's secure cache directory and launches the Android system share sheet (`Intent.ACTION_SEND` with image stream) using `FileProvider`.
3. **Strict Privacy & Zero Network**:
   - Zero external servers, zero cloud uploads, zero user accounts, zero analytics trackers.
   - Operates with zero network permissions touched during generation or sharing.
   - Image is saved only to temporary local cache (`context.cacheDir/shared_images`) and accessed via scoped content URI.
4. **Subsystem Isolation**:
   - Strictly NO modifications to `vpn/`, `service/`, or `DeviceAdminReceiver`.

---

## 2. Subsystem Boundaries & Anti-Tamper Isolation
- **`vpn/` package**: **NO changes**.
- **`service/` package**: **NO changes**.
- **`strict/` & `receiver/`**: **NO changes**.
- **Data Layer (`data/local/`)**: Reads existing Room records (`app_limits`, `suppressed_notifications`, `focus_profiles`). Zero schema changes.
- **Domain Layer (`domain/`)**:
  - `domain/model/FocusSummaryModels.kt`: Data contracts for summary metrics.
  - `FocusSummaryBitmapGenerator`: Canvas-based bitmap rendering engine.
  - `FocusSummaryShareManager`: Image file storage and system `Intent.ACTION_SEND` dispatcher.
- **UI Layer (`ui/`)**:
  - "Share Today" action button on the Daily Usage Card on `DashboardScreen`.

---

## 3. Visual Layout of Generated Share Image (1080 x 1350)
1. **Header (Top)**:
   - "MONK MODE" in bold serif typography.
   - "Daily Focus Summary • [Date formatted, e.g. Sep 26, 2026]".
2. **Center Dial**:
   - Circular progress arc tracking screen time against daily budget.
   - Center text: `${usedMinutes}m` screen time, `${targetMinutes}m budget`.
   - Score: `${focusScorePercentage}% Focus Maintained`.
3. **Metric Cards (Bottom)**:
   - Blocked Distractions: `${blockedDistractionsCount} Distractions Blocked`.
   - App Limits Monitored: `${activeLimitsCount} App Limits Monitored`.
   - Focus Profile: `Active Profile: ${profileName}`.
4. **Footer**:
   - "Stay Focused • Built-in Monk Mode Protection".

---

## 4. Affected Files
1. `docs/phase13_shareable_focus_summary_spec.md` (this spec)
2. `tasks/todo.md` (Phase 13 task checklist)
3. `tasks/dod/phase13_dod.md` (DoD verification checklist)
4. `app/src/main/res/xml/file_paths.xml` (FileProvider path specification)
5. `app/src/main/AndroidManifest.xml` (FileProvider declaration)
6. `app/src/main/java/com/stayfocused/app/domain/model/FocusSummaryModels.kt` (data models)
7. `app/src/main/java/com/stayfocused/app/domain/FocusSummaryBitmapGenerator.kt` (Canvas drawing engine)
8. `app/src/test/java/com/stayfocused/app/domain/FocusSummaryBitmapGeneratorTest.kt` (unit tests)
9. `app/src/main/java/com/stayfocused/app/domain/FocusSummaryShareManager.kt` (cache writer & share intent)
10. `app/src/test/java/com/stayfocused/app/domain/FocusSummaryShareManagerTest.kt` (unit tests)
11. `app/src/main/java/com/stayfocused/app/ui/screens/DashboardScreen.kt` ("Share Today" button wire-in)
