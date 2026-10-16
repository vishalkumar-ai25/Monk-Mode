# ADR 021: Strict Mode Anti-Bypass Hardening: Multi-Window Eviction, Anti-OCR/Paste Challenge Shield, and Power Menu Defense

## Status
Accepted (Hardened via Adversarial Review)

## Context
Monk Mode provides deep work and addiction-breaking controls through Strict Mode. When a user arms Strict Mode, they commit to focus for an intentional duration. However, compulsive phone addiction creates strong psychological urges to find bypasses. Reverse engineering of commercial blockers (like Stay Focused, Freedom, and BlockSite) and empirical testing on real-world Android 14 devices (including Realme C65 5G / ColorOS 14) reveals three critical evasion vectors:

1. **OCR and Clipboard Bypass on Random Text Stoic Challenge**:
   - When Strict Mode is armed with the `RANDOM_TEXT` deactivation challenge, the user must type a 60–120 character Stoic quote verbatim to confirm intentional disarm.
   - *Display Buffer vs Accessibility Tree*: While `FLAG_SECURE` blanks out screenshots and screen recording, accessibility services, macro automation apps, and Google Assistant / AssistStructure can still parse plain text from Compose Semantics unless explicitly shielded.
   - *Clipboard & IME Insertion*: Context menus, keyboard clipboard bars (e.g. Gboard ribbon), and hardware shortcuts (`Ctrl+V`) can paste the quote in a split-second.

2. **Multi-Window / Split-Screen / Floating Bubble Evasion**:
   - Modern Android (API 26 through API 34) supports multi-window splits, freeform floating windows, and Picture-in-Picture (PiP).
   - *Attack Vector*: The user opens an allowed app (e.g., Monk Mode, Calculator, or allowed Settings) in the primary window, and opens a blocked distracting app (e.g., YouTube, Instagram) in the secondary split, floating bubble, or PiP window.
   - Previously, `FocusAccessibilityService` evaluated only the single active package reported by `AccessibilityEvent.packageName`.

3. **Power Menu Soft-Reboot / Shutdown Evasion ("Block Power Off")**:
   - When caught in a strict lock, compulsive users often hold down the hardware power button to shut down or restart the phone, hoping to reboot into Safe Mode, clear temporary foreground state, or exploit boot transitions.
   - Commercial Stay Focused offers an optional "Block Power Off" feature in Strict Mode that intercepts the system power dialog and instantly dismisses it.

4. **Life-Safety and Legal Boundary (Emergency SOS)**:
   - Modern power menus house the Emergency / SOS Call button (112, 911, 999, Medical ID). Unconditional suppression of emergency services violates FCC/CE/TRAI regulations and Google Play policy. All emergency packages, SOS intents, and hardware 5-press SOS must remain strictly unblocked.

---

## Decision & Hardened Architecture Contracts

### 1. Stoic Challenge Shield: Anti-OCR, Semantics Shield, and Multi-Layer Paste Defense

#### A. Window Capture & Accessibility Semantics Shield
1. **Dialog Window Security**:
   - Set `DialogProperties(securePolicy = SecureFlagPolicy.SecureOn)` on the Compose `AlertDialog`.
2. **Host Activity Window Security**:
   - Attach a `DisposableEffect` that applies `WindowManager.LayoutParams.FLAG_SECURE` to the hosting `Activity.window` while the dialog is visible, and restores previous flags on dispose.
   - Ensures SurfaceFlinger composites black frames for screenshots (`Power + Vol Down`), Realme UI 3-finger swipe (`com.oplus.screenshot`), and Circle to Search.
3. **Accessibility Semantics Shielding (Anti-Assist Scraper)**:
   - The Stoic challenge quote text is rendered with:
     ```kotlin
     DisableSelection {
         Text(
             text = "\"${quote.text}\"",
             modifier = Modifier.clearAndSetSemantics { /* zero accessibility text exposure */ }
         )
     }
     ```
   - This prevents macro apps, auto-clickers, and AssistStructure parsers from extracting the quote from the view hierarchy.

#### B. Multi-Layer Paste & Automation Defense
To avoid false positives on predictive text, IME autocorrect, and multilingual transliteration while eliminating bypasses:
1. **IME Configuration**:
   ```kotlin
   keyboardOptions = KeyboardOptions(
       keyboardType = KeyboardType.Text,
       autoCorrect = false,
       capitalization = KeyboardCapitalization.None
   )
   ```
2. **Context Menu Stripping**:
   Enforce `NoPasteTextToolbar` via `LocalTextToolbar`:
   ```kotlin
   class NoPasteTextToolbar(private val base: TextToolbar) : TextToolbar {
       override val status: TextToolbarStatus get() = base.status
       override fun hide() = base.hide()
       override fun showMenu(
           rect: Rect,
           onCopyRequested: (() -> Unit)?,
           onPasteRequested: (() -> Unit)?,
           onCutRequested: (() -> Unit)?,
           onSelectAllRequested: (() -> Unit)?
       ) {
           base.showMenu(rect, onCopyRequested, null, onCutRequested, onSelectAllRequested)
       }
   }
   ```
3. **Hardware & Shortcut Paste Interception**:
   Attach `Modifier.onPreviewKeyEvent` on the text field to intercept `Key.V` with `isCtrlPressed == true` or `Key.Insert` with `isShiftPressed == true`.
4. **Active Clipboard Matching**:
   On every text update, check if `newText` matches or contains the current `ClipboardManager.primaryClip?.getItemAt(0)?.text`. If the user copied the quote or a chunk of it, the update is rejected.
5. **Keystroke Cadence Telemetry (Flight-Time Analysis in `RandomTextChallengeEngine.kt`)**:
   - Rather than merely measuring total elapsed time at the end (which is bypassed by waiting 10s after pasting), track:
     a) `keystrokeCount`: Number of discrete typing events. Enforce `keystrokeCount >= (targetQuote.length * 0.6).toInt()`.
     b) `interKeystrokeIntervals`: Time deltas between consecutive keystrokes. Human manual typing exhibits realistic typing flight times ($60\text{ms} \le \Delta t \le 1200\text{ms}$). A paste has $\Delta t \approx 0\text{ms}$ and zero variance.
     c) Maximum physiological typing rate: Clamped at $\le 15$ characters per second over the active typing duration.

---

### 2. Multi-Window and Split-Screen Eviction (`MultiWindowInspector.kt`)

#### A. Window Hierarchy Inspection in `FocusAccessibilityService`
- In `accessibility_service_config.xml`: Add `typeWindowsChanged` to `android:accessibilityEventTypes`:
  `"typeWindowStateChanged|typeWindowContentChanged|typeViewClicked|typeWindowsChanged"`
- **Strict Rate-Limiting Invariant**:
  - `getWindows()` is a synchronous IPC Binder call across processes.
  - To prevent accessibility thread starvation:
    1. Do **NOT** invoke `getWindows()` on 120Hz `TYPE_WINDOW_CONTENT_CHANGED` events.
    2. Enforce a monotonic cooldown of at least 300ms between `getWindows()` queries.
    3. On Android 13+ (API 33+), invoke `window.getRoot(AccessibilityNodeInfo.FLAG_PREFETCH_NO_PREFETCH)` to eliminate recursive tree prefetching.

#### B. Pure Kotlin Evaluator (`MultiWindowInspector.kt`)
```kotlin
data class WindowSnapshot(
    val id: Int,
    val packageName: String?,
    val isFocused: Boolean,
    val isVisible: Boolean,
    val isApplication: Boolean,
    val isInPictureInPicture: Boolean
)

sealed interface MultiWindowDecision {
    data object Allow : MultiWindowDecision
    data class EvictBlockedWindow(
        val packageName: String,
        val isPictureInPicture: Boolean,
        val reason: BlockReason
    ) : MultiWindowDecision
}

class MultiWindowInspector(
    private val ruleEvaluator: (packageName: String) -> BlockReason?
) {
    fun evaluate(windows: List<WindowSnapshot>): MultiWindowDecision {
        val appWindows = windows.filter { it.isApplication && it.isVisible && it.packageName != null }
        if (appWindows.isEmpty()) return MultiWindowDecision.Allow

        // Check if multiple application windows are visible simultaneously or PiP is active
        val hasMultiAppWindows = appWindows.size > 1 || appWindows.any { it.isInPictureInPicture }
        if (!hasMultiAppWindows) return MultiWindowDecision.Allow

        for (win in appWindows) {
            val pkg = win.packageName!!.lowercase()
            if (pkg == "com.stayfocused.app" || pkg == "com.android.systemui") continue

            val blockReason = ruleEvaluator(win.packageName)
            if (blockReason != null) {
                return MultiWindowDecision.EvictBlockedWindow(
                    packageName = win.packageName,
                    isPictureInPicture = win.isInPictureInPicture,
                    reason = blockReason
                )
            }
        }
        return MultiWindowDecision.Allow
    }
}
```

#### C. Multi-Window & PiP Eviction Actions
When `MultiWindowDecision.EvictBlockedWindow` is returned:
1. **Split-Screen Eviction**:
   - Dispatch `performGlobalAction(GLOBAL_ACTION_HOME)`.
   - Display overlay: `BlockOverlayManager.showOverlay(reason = decision.reason)`.
2. **Picture-in-Picture (PiP) Eviction**:
   - In Android, `GLOBAL_ACTION_HOME` does not dismiss active PiP windows.
   - When `isPictureInPicture == true`:
     a) Show `BlockOverlayManager` overlay (window type `TYPE_APPLICATION_OVERLAY` renders on top of PiP).
     b) Launch an explicit intent to bring the blocked app to the foreground and immediately execute `GLOBAL_ACTION_BACK` + `GLOBAL_ACTION_HOME`, terminating the PiP session.

---

### 3. Power Menu Defense ("Block Power Off") (`PowerMenuInspector.kt`)

#### A. Pipeline Placement (Bypassing `TransientWindowFilter`)
- `PowerMenuInspector.evaluate()` must execute **BEFORE** the `transientFilter.isTransient()` guard in `FocusAccessibilityService.kt`.
- This ensures power dialogs originating from `com.android.systemui` or `com.oplus.systemui` are not prematurely dropped as ordinary transient system UI.

#### B. Pure Kotlin Evaluator & Life-Safety Exemption (`PowerMenuInspector.kt`)
```kotlin
sealed interface PowerMenuDecision {
    data object Allow : PowerMenuDecision
    data object DismissPowerMenu : PowerMenuDecision
}

class PowerMenuInspector {
    companion object {
        val POWER_DIALOG_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.internal.app",
            "com.coloros.globalactions",
            "com.oplus.globalactions",
            "com.samsung.android.globalactions",
            "com.miui.powerkeeper"
        )

        val POWER_DIALOG_CLASSES = setOf(
            "globalactionsdialog",
            "globalactions",
            "shutdownactivity",
            "shutdowndialog",
            "poweroffdialog",
            "rebootdialog"
        )

        // Strict power/reboot action tokens.
        // NOTICE: "emergency", "sos", "medical" are STRICTLY EXCLUDED to preserve life safety!
        val POWER_ACTION_TOKENS = setOf(
            "power off",
            "turn off",
            "restart",
            "reboot",
            "poweroff"
        )

        // Explicit life-safety package allowlist: NEVER intercept or dismiss emergency dialers
        val EMERGENCY_PACKAGES = setOf(
            "com.android.emergency",
            "com.google.android.apps.safetyhub",
            "com.android.phone",
            "com.coloros.emergency",
            "com.oplus.sos",
            "com.sec.android.app.safetyassurance"
        )
    }

    fun evaluate(
        packageName: String,
        className: String?,
        windowTexts: List<String>,
        isStrictActive: Boolean,
        isBlockPowerOffEnabled: Boolean
    ): PowerMenuDecision {
        if (!isStrictActive || !isBlockPowerOffEnabled) return PowerMenuDecision.Allow

        val lowerPkg = packageName.lowercase()
        if (EMERGENCY_PACKAGES.contains(lowerPkg)) {
            return PowerMenuDecision.Allow
        }

        val lowerCls = className?.lowercase() ?: ""
        val isTargetPkg = POWER_DIALOG_PACKAGES.contains(lowerPkg)
        val isTargetCls = POWER_DIALOG_CLASSES.any { lowerCls.contains(it) }

        val hasPowerTokens = windowTexts.any { text ->
            val lower = text.lowercase()
            POWER_ACTION_TOKENS.any { token -> lower.contains(token) }
        }

        if ((isTargetCls && hasPowerTokens) || (isTargetPkg && hasPowerTokens && (lowerCls.contains("dialog") || lowerCls.contains("global")))) {
            return PowerMenuDecision.DismissPowerMenu
        }

        return PowerMenuDecision.Allow
    }
}
```

#### C. Neutralization & Failsafe Invariants
1. When `PowerMenuDecision.DismissPowerMenu` triggers:
   - Dispatch `performGlobalAction(GLOBAL_ACTION_BACK)` immediately.
   - Record in `FailsafeLogDao`: `POWER_MENU_DISMISSED`.
2. **PMIC Silicon Boundary**:
   - Software accessibility intercept can only dismiss user-space UI dialogs (`GlobalActionsDialog`).
   - Hardware-level PMIC reset (holding `Power + Vol Down` for 10–15 seconds) cuts battery power directly at the silicon rail level and cannot be intercepted by any Android userland app.
   - This boundary is defended post-reboot by ADR 010 (TrustedClock monotonic time clamping and Strict Boot Grace Period).
3. **Statutory Safety Opt-In**:
   - "Block Power Off" defaults to `false`.
   - Enabling it in Strict Mode settings displays a mandatory disclaimer:
     *"Warning: Blocking the power menu disables the on-screen Power Off and Restart options. In an emergency, your phone's hardware 5-press Power Button Emergency SOS remains functional."*

---

### 4. Home Launcher Category Decision: Excluded
Per adversarial review:
- Declaring `<category android:name="android.intent.category.HOME" />` in an activity-alias flags the APK as a home launcher in Google Play ingestion pipelines.
- Because Monk Mode does not provide a functional launcher desktop, this would violate Google Play's Broken Functionality Policy.
- **Decision**: Home launcher category is completely omitted from `AndroidManifest.xml`. Device Admin, battery optimization exemption (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`), and accessibility keep-alive (`aiengine_keep_alive`) provide 100% sufficient resilience without launcher risks.
