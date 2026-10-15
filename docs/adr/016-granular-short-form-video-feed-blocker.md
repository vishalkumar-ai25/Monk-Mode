# ADR 016: Granular Short-Form Video Feed Blocker (YouTube Shorts, Instagram Reels/Stories, Snapchat Spotlight)

## Status
Accepted (Hardened via Adversarial Review)

## Context and Problem Statement
Personal productivity and screen-time telemetry reveal that user time is predominantly lost not to entire applications, but to algorithmic, infinite-scroll vertical short-form video feeds:
1. **YouTube Shorts** (`com.google.android.youtube`)
2. **Instagram Reels & Stories** (`com.instagram.android`)
3. **Facebook Reels** (`com.facebook.katana`)
4. **Snapchat Spotlight** (`com.snapchat.android`)

Whole-package blocking is often too blunt for users who legitimately need YouTube for educational lectures, Instagram for direct messaging with family/colleagues, or Facebook for local community groups. Blocking the entire package drives users to disarm Strict Mode or uninstall the app out of practical necessity.

We require a surgical, sub-app interception mechanism that detects when a user enters an addictive short-form video player or feed, immediately evicts them back to the safe main feed or search screen via `GLOBAL_ACTION_BACK` or safe tab switching, tracks daily limits/scrolls, and keeps the remainder of the parent application fully functional.

---

## Architectural Analysis & Trade-Offs

### Option 1: Accessibility View Tree Inspection & Resource ID Matching (Selected)
- **Mechanism**:
  - `FocusAccessibilityService` listens for `TYPE_WINDOW_STATE_CHANGED`, `TYPE_VIEW_CLICKED`, and `TYPE_WINDOW_CONTENT_CHANGED` specifically on targeted media packages.
  - Dual-phase event filtering: drops events for non-media packages with zero IPC. Drops content changes without `CONTENT_CHANGE_TYPE_SUBTREE` bitmask.
  - Resource ID and Structural Matching: performs fast lookup via `rootNode.findAccessibilityNodeInfosByViewId(viewId)` and tab bar selection state (`role == ROLE_TAB` or `BottomNavigationView`).
  - Upon detection, dispatches `performGlobalAction(GLOBAL_ACTION_BACK)` or clicks the verified safe Home tab.
  - Strict node lifecycle: every retrieved `AccessibilityNodeInfo` is eagerly `.recycle()`-d to avoid Binder leaks.
- **Strengths**:
  - 100% on-device, local-first, zero network overhead.
  - Differentiates video player fragments from chat messages, comments, and long-form video playback.
  - Does not occupy the OS VPN slot or break DNS routing.
  - 100% Zero Screen Scraping Guarantee: never touches user comments, video captions, DMs, or personal post text.

### Option 2: Network / DNS Sub-Domain Blocking
- **Mechanism**: Block DNS lookups for video streaming domains via `DnsVpnService`.
- **Rejection Rationale**: YouTube Shorts and long-form videos share identical streaming CDNs (`googlevideo.com`). Blocking the CDN breaks all video playback completely, making granular short-form isolation impossible at the network layer without HTTPS MITM decryption.

---

## Technical Specification & Decision

We adopt **Option 1: Accessibility View Tree Inspection & Resource ID Matching** with hardened invariants resulting from the adversarial stress test.

### 1. Target Package & View Signature Mapping Table

| Short-Form Target | Android Package Name | Primary View Resource IDs | Navigation / Tab Signatures | Safe Eviction Action |
|---|---|---|---|---|
| **YouTube Shorts** | `com.google.android.youtube` | `com.google.android.youtube:id/reel_recycler`<br>`com.google.android.youtube:id/reel_watch_fragment_root`<br>`com.google.android.youtube:id/reel_player_page_container` | Bottom nav item with `contentDescription == "Shorts"` and `isSelected == true` | Attempt 1: `performGlobalAction(GLOBAL_ACTION_BACK)`.<br>Fallback: Click tab with `contentDescription == "Home"` |
| **Instagram Reels** | `com.instagram.android` | `com.instagram.android:id/clips_viewer_view_pager`<br>`com.instagram.android:id/clips_video_container` | Bottom tab `com.instagram.android:id/clips_tab` with `isSelected == true` | Attempt 1: `performGlobalAction(GLOBAL_ACTION_BACK)`.<br>Fallback: Click `com.instagram.android:id/feed_tab` |
| **Instagram Stories** | `com.instagram.android` | `com.instagram.android:id/reel_viewer_root`<br>`com.instagram.android:id/story_viewer_container` | Story tray container overlay | `performGlobalAction(GLOBAL_ACTION_BACK)` |
| **Snapchat Spotlight** | `com.snapchat.android` | `com.snapchat.android:id/spotlight_view_container` | Spotlight navigation tab selected | `performGlobalAction(GLOBAL_ACTION_BACK)` |
| **Facebook Reels** | `com.facebook.katana` | `com.facebook.katana:id/fb_shorts_viewer_container` | Reels player fragment | `performGlobalAction(GLOBAL_ACTION_BACK)` |

---

## 2. Hardened Guardrails & Invariant Protections (Adversarial Resolutions)

1. **Zero Screen Scraping Guarantee for User Content**:
   - Node inspection is strictly limited to view resource IDs, class names (`ViewPager2`, `RecyclerView`), and navigation tabs (`BottomNavigationView`, `TabWidget`).
   - The service NEVER traverses leaf nodes or inspects `node.text` or `node.contentDescription` inside scrolling containers, ensuring user comments, chat overlays, subtitles, and post text are completely unread and unlogged.
   - Text token matching (`"Shorts"`, `"Reels"`, `"Remix"`, `"Original audio"`) inside body containers is strictly prohibited, preventing false positives in search results, channel tabs, and home feeds.

2. **Dual-Phase Event Filtering & Binder Saturation Defense (120Hz Displays)**:
   - **Phase 0 (Zero Binder IPC)**: Check `event.packageName`. If not in `SHORT_FORM_PACKAGES`, drop immediately.
   - **Phase 1 (Bitmask Gate)**: For `TYPE_WINDOW_CONTENT_CHANGED`, verify `(event.contentChangeTypes and AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE) != 0`. All text, caption, timer, and description events are dropped with zero IPC.
   - **Phase 2 (Debounce & Cooldown)**:
     - `TYPE_WINDOW_STATE_CHANGED` and `TYPE_VIEW_CLICKED` (nav tab clicks) are **never** debounced (0ms latency).
     - `TYPE_WINDOW_CONTENT_CHANGED` is throttled with a 150ms atomic cooldown per package.
   - **Explicit Node Recycling**: All queried `AccessibilityNodeInfo` instances and list results are eagerly released in `finally` blocks via `recycle()`.

3. **Loop Protection & Ban on `GLOBAL_ACTION_HOME` Escalation**:
   - To prevent involuntary launcher ejections during legitimate long-form YouTube lectures or Instagram DMs:
     - **Attempt 1**: `performGlobalAction(GLOBAL_ACTION_BACK)`
     - **Attempt 2 (if still visible after 350ms)**: Click the verified safe Home tab (`contentDescription == "Home"` or `id/feed_tab`).
     - Under NO circumstance does the service escalate to `GLOBAL_ACTION_HOME`.

4. **Multi-Window & Picture-in-Picture (PiP) Scoping**:
   - Active inspection applies when target package is in foreground.
   - Uses `getWindows()` when multi-window is reported to inspect visible application windows.

5. **State Contract & Persistence Integration (Room Migration v6 $\to$ v7)**:
   - `ShortFormRuleEntity`:
     - `id: String` (e.g. `youtube_shorts`, `instagram_reels`, `instagram_stories`, `snapchat_spotlight`)
     - `packageName: String`
     - `displayName: String`
     - `isBlocked: Boolean` (true = instant ejection upon open)
     - `dailyScrollLimit: Int` (0 = no limit / block immediately if `isBlocked`; >0 = allow up to N scrolls/videos)
     - `currentDayScrolls: Int`
     - `lastEvictionTimestamp: Long`
   - In-memory `StateFlow<Map<String, ShortFormRuleEntity>>` cache ensures 0ms lock-free rule lookup on the accessibility thread.
   - Eviction counting and scroll increments are persisted asynchronously via coroutines on `Dispatchers.IO` (zero main thread database I/O).
   - Daily scroll counts reset at midnight via `MidnightResetReceiver`.

---

## Consequences
- Users retain 100% full access to educational long-form YouTube and Instagram DMs.
- Short-form video rabbit holes are automatically evicted within $< 100\text{ ms}$ of opening without home-screen dumping.
- Zero screen scraping guarantee is preserved without reading comments, captions, or DMs.
- Binder IPC saturation is eliminated via subtree bitmask gating and eager node recycling.
