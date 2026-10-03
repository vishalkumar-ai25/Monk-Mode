# Phase 21 Tasks: Monk Mode Light Theme & UI/UX Redesign

- [x] Task 1: Light Theme Tokens & Material3 Color Scheme (`MonkModeTheme.kt`)
  - Define `MonkCanvas = Color(0xFFF8F6F2)`, `MonkCard = Color(0xFFFFFFFF)`, `MonkCardAlt = Color(0xFFEFECE6)`, `MonkLine = Color(0xFFE5E0D8)`.
  - Define `MonkText = Color(0xFF1A1815)`, `MonkMuted = Color(0xFF767167)`.
  - Define `MonkEmber = Color(0xFFC4681A)`, `MonkEmberDim = Color(0xFFFCEFDE)`.
  - Define `MonkSage = Color(0xFF3D7946)`, `MonkDanger = Color(0xFFC24135)`, `MonkPanel = Color(0xFFFFFFFF)`.
  - Set `MonkInk = Color(0xFFFFFFFF)` as on-accent/button content token.
  - Reconfigure `MonkModeColorScheme` using `lightColorScheme(...)`.

- [x] Task 2: System Bars & Navigation Chrome Polish (`MainActivity.kt`)
  - Configure `WindowInsetsControllerCompat` in `MainActivity` with `isAppearanceLightStatusBars = true` and `isAppearanceLightNavigationBars = true`.
  - Polish `NavigationBar` styling: `containerColor = MonkPanel`, top border with `MonkLine`, and `indicatorColor = MonkEmberDim`.

- [x] Task 3: Circular Usage Dial, Widget & Share Generator Polish
  - Update `FocusWidgetDialRenderer.kt` ARGB constants to light theme palette.
  - Update `FocusSummaryBitmapGenerator.kt` constants to light canvas and sumi ink palette.
  - Verify `DailyUsageDial.kt` track and progress arc rendering.

- [x] Task 4: Screen & Component Harmony Audit
  - Verify `DashboardScreen.kt`, `AppLimitsScreen.kt`, `WebBlockerScreen.kt`, `NotificationVaultScreen.kt`, `StrictLockScreen.kt`.
  - Verify dialogs: `StrictScheduleDialogs.kt`, `BackupRestoreDialogs.kt`, `ArmStrictSessionDialog`.
  - Verify cards: `ProtectionStatusCard.kt`, `TakeABreakCard.kt`, `FocusSchedulesCard.kt`, `QuickProfileChipRow.kt`, `FailsafeLogCard.kt`.

- [x] Task 5: Testing, Build & Senior Code Review
  - [x] Run `./gradlew testDebugUnitTest` and `./gradlew assembleDebug` with `BypassSandbox: true`.
  - [x] Invoke `code-reviewer` subagent, resolve all findings, and obtain approval.
  - [x] Install debug APK on connected device `V49TW4RWQOZ5IFBA` and capture verification screenshot.
