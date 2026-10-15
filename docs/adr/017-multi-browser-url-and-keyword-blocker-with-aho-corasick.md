# ADR 017: Multi-Browser URL Path, Aho-Corasick Keyword Blocker, and Dwell Browsing History

- **Status:** Accepted (Hardened via Adversarial Review)
- **Date:** 2026-10-16
- **Deciders:** Architecture Team, Security Team, Core Engineering, Adversarial Reviewer
- **Technical Context:** Android 8.0 (API 26) through Android 14 (API 34 / realme UI 5.0), Local DNS VPN, Accessibility Address Bar Inspection.

---

## 1. Context & Problem Statement

Monk Mode currently utilizes a local DNS proxy (`DnsVpnService`, ADR 001 / ADR 013) to intercept UDP port 53 DNS queries and return `NXDOMAIN` for blocked domains. While the DNS VPN provides an airtight, browser-agnostic, zero-screen-scraping foundation for domain blocking, it has intrinsic architectural limitations:
1. **Blindness to URL Paths:** DNS operates at the network resolution phase. It resolves `reddit.com` or `youtube.com`, but cannot inspect the HTTPS path (e.g., `reddit.com/r/all` vs. `reddit.com/r/androiddev`, or `youtube.com/feed/trending`).
2. **Blindness to Omnibox Search Queries & NSFW Keywords:** Users search for prohibited terms, gambling queries, or adult content directly in browser address bars without triggering a blocked domain query until after an external domain is resolved.
3. **Absence of Browsing History & Dwell Telemetry:** Commercial Stay Focused logs visited domains and dwell time (minutes spent on specific domains) so users can analyze their digital diet. A DNS proxy only sees momentary DNS lookups, not active reading time.

To achieve 1:1 commercial parity with the decompiled Stay Focused app, Monk Mode establishes a **Dual-Defense Architecture**:
- **Layer 1 (System-Wide Network Shield):** Local DNS VPN (`DnsVpnService`) remains the authoritative, un-bypassable domain-level blocker across all apps and WebViews.
- **Layer 2 (Application-Level Granular URL & Keyword Blocker):** `FocusAccessibilityService` inspects the browser address bar of 12+ supported browsers to enforce URL path limits, scan keywords via an $O(N+M)$ Aho-Corasick automaton with delimiter boundary verification, and record dwell times to local browsing history.

---

## 2. Privacy Scoping: 100% Zero Screen Scraping Guarantee

A non-negotiable architectural invariant in Monk Mode is user privacy:
1. **Registered Address Bar Nodes Only:** The inspector queries *strictly* the registered view ID for the browser package from `BrowserUrlBarRegistry` (e.g. `com.android.chrome:id/url_bar`, `org.mozilla.firefox:id/mozac_browser_toolbar_url_view`, `com.android.chrome:id/title_url`).
2. **Zero DOM / Body Traversal:** The service is strictly forbidden from recursively traversing WebView children, HTML DOM body nodes, or paragraph text. Open-ended structural DOM traversal heuristics are completely banned.
3. **No Password or Form Capture:** Nodes with `isPassword == true` or input types related to credentials or forms are strictly excluded.
4. **Omnibox Typing Protection:** While `urlNode.isFocused == true`, text extraction is deferred until focus is released or navigation commits, preventing capture of partial search queries.
5. **Incognito Privacy:** If an Incognito badge or indicator is detected on the browser window, blocks are enforced immediately, but writing to `browsing_history` is strictly suppressed to honor private browsing.

---

## 3. Browser Address Bar Registry (12+ Supported Browsers)

The address bar mapping is defined in a pure Kotlin registry (`BrowserUrlBarRegistry`):

| Browser Name | Android Package Name | Primary Resource ID | Secondary / CCT ID |
| :--- | :--- | :--- | :--- |
| **Google Chrome** | `com.android.chrome` | `com.android.chrome:id/url_bar` | `com.android.chrome:id/title_url` (CCT) |
| **Mozilla Firefox** | `org.mozilla.firefox` | `org.mozilla.firefox:id/mozac_browser_toolbar_url_view` | `org.mozilla.firefox:id/url_bar_title` |
| **Brave Browser** | `com.brave.browser` | `com.brave.browser:id/url_bar` | `com.brave.browser:id/title_url` |
| **Samsung Internet** | `com.sec.android.app.sbrowser` | `com.sec.android.app.sbrowser:id/location_bar_edit_text` | `com.sec.android.app.sbrowser:id/location_bar` |
| **Microsoft Edge** | `com.microsoft.emmx` | `com.microsoft.emmx:id/url_bar` | `com.microsoft.emmx:id/search_box` |
| **Opera Browser** | `com.opera.browser` | `com.opera.browser:id/url_field` | `com.opera.browser:id/url_bar` |
| **Opera Mini** | `com.opera.mini.native` | `com.opera.mini.native:id/url_field` | `url_field` |
| **DuckDuckGo** | `com.duckduckgo.mobile.android` | `com.duckduckgo.mobile.android:id/omnibarTextInput` | `omnibarTextInput` |
| **Kiwi Browser** | `com.kiwibrowser.browser` | `com.kiwibrowser.browser:id/url_bar` | `url_bar` |
| **Vivaldi** | `com.vivaldi.browser` | `com.vivaldi.browser:id/url_bar` | `url_bar` |
| **Yandex Browser** | `com.yandex.browser` | `com.yandex.browser:id/bro_omnibox_address_title_text` | `bro_omnibox_address_title_text` |
| **Ecosia** | `com.ecosia.android` | `com.ecosia.android:id/url_bar` | `url_bar` |
| **Tor Browser** | `org.torproject.torbrowser` | `org.torproject.torbrowser:id/mozac_browser_toolbar_url_view` | `url_bar_title` |

---

## 4. URL Normalization & Sanitization (`UrlNormalizer`)

To prevent `Uri.parse()` crashes when `scheme` is omitted by browsers (e.g. `reddit.com/r/all`), `UrlNormalizer` implements deterministic normalization:
1. Trims whitespace and extracts the raw string.
2. Identifies search queries: if the text contains whitespace and lacks a protocol or dot, it is classified as `OmniboxContent.SearchQuery(query)`.
3. Normalizes schemeless URLs: if text lacks `://`, checks if the authority matches domain syntax `^[a-zA-Z0-9-]+\.[a-zA-Z]{2,}(/.*)?$`. If so, prepends `https://`.
4. URL-decoding: safely decodes percent-encoded characters using `runCatching` to prevent `IllegalArgumentException` on malformed `%` escapes.
5. Strict Path Matching: matches paths using delimiter boundaries (`/`, `?`, `#`) to avoid false prefix positives (e.g. `/r/all` does not match `/r/all-inclusive-resorts`).

---

## 5. $O(N+M)$ Aho-Corasick Keyword Matching Engine with Boundary Verification

To prevent the **Scunthorpe Catastrophe** (where substring `ass` blocks `class`, `passwords.google.com`, `glass`, or `anal` blocks `analytics`), the Aho-Corasick engine (`AhoCorasickKeywordMatcher`) enforces mandatory word-boundary delimiter checks on every emitted match:

### Delimiter Boundary Invariant:
When keyword $K$ of length $|K|$ matches at index range $[start, end)$ in input text $S$:
1. **Left Boundary:** Must be $start == 0$ OR $S[start - 1]$ is one of:
   `' ', '/', '?', '&', '=', '+', '%', '.', '_', '-', '#', ':', ';', '~', '\\'`
2. **Right Boundary:** Must be $end == S.length$ OR $S[end]$ is one of:
   `' ', '/', '?', '&', '=', '+', '%', '.', '_', '-', '#', ':', ';', '~', '\\'`
3. If either boundary condition fails, the candidate match is discarded.

### Pre-Compiled Categories:
- `ADULT`: Built-in dictionary of explicit adult/NSFW terms (e.g. `porn`, `xxx`, `hentai`, `nsfw`, `erotic`, `xhamster`, etc.).
- `CUSTOM`: User-defined prohibited keywords.
- `PATH`: Prohibited URL paths.

---

## 6. Dwell Time Telemetry & Browsing History Engine

1. **Hardware-Monotonic Timing:**
   - Dwell sessions are tracked in memory using `SystemClock.elapsedRealtime()`:
     `ActiveDwellSession(browserPackage, domain, fullUrl, startTimestampMs, startElapsedRealtime)`
   - When navigation changes or the user leaves the browser, dwell time is computed as:
     `dwellMs = SystemClock.elapsedRealtime() - startElapsedRealtime - screenOffElapsedDelta`
   - This ensures static reading sessions (where 0 accessibility events fire for minutes) are accurately credited.
2. **Threshold & Sanitization:**
   - Sessions $< 3000\text{ ms}$ (redirects, instant taps) are ignored.
   - Sensitive query parameters (`?token=...`, `?key=...`) are stripped from `fullUrl` before Room persistence.
   - Incognito sessions are NEVER written to `browsing_history`.
3. **Database Maintenance:**
   - 30-day rolling TTL: entries older than 30 days are pruned automatically during midnight maintenance.

---

## 7. Neutralization Sequence for Blocked URLs & Keywords

When a restricted URL path or keyword is detected in the address bar:
1. **Eviction:**
   - Attempt 1: Call `performGlobalAction(GLOBAL_ACTION_BACK)` to pop the tab to the prior safe page.
   - Attempt 2 (if URL remains blocked within 1000ms): Call `performGlobalAction(GLOBAL_ACTION_HOME)`.
   - *Note:* `Intent(ACTION_VIEW, Uri.parse("about:blank"))` is strictly banned because standard Android browsers throw `ActivityNotFoundException` on the `about` scheme.
2. **Block Overlay:**
   - Attach the system overlay (`BlockOverlayManager.showOverlay(BlockReason.UrlBlocked(url, rule))`).
   - If the overlay is dismissed, `GLOBAL_ACTION_HOME` is dispatched first to prevent re-entering the blocked tab.
3. **Audit:**
   - Persist a blocked record to `browsing_history` (`wasBlocked = true`, `blockReason = matchedRule`).

---

## 8. Event Filtering & Performance Protection on 120Hz Displays

1. **Dual Content Change Filter:** The event handler accepts:
   - `AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED`
   - `AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED` matching `CONTENT_CHANGE_TYPE_SUBTREE or CONTENT_CHANGE_TYPE_TEXT`
2. **Package Gate:** Discard all events immediately if `packageName` is not in `BrowserUrlBarRegistry.SUPPORTED_PACKAGES` (0 IPC overhead).
3. **Throttle:** Address bar inspection is throttled to 250ms per browser package, and identical consecutive URLs are deduplicated.
4. **Node Recycling:** Every queried `AccessibilityNodeInfo` is recycled in `finally` blocks.

---

## 9. Room Schema Evolution: Migration 7 $\to$ 8

Increment database version to 8 and add tables:

```sql
-- 1. Browsing History
CREATE TABLE IF NOT EXISTS browsing_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    timestampMs INTEGER NOT NULL,
    browserPackage TEXT NOT NULL,
    domain TEXT NOT NULL,
    fullUrl TEXT NOT NULL,
    dwellTimeMs INTEGER NOT NULL,
    wasBlocked INTEGER NOT NULL,
    blockReason TEXT
);
CREATE INDEX IF NOT EXISTS index_browsing_history_timestampMs ON browsing_history(timestampMs);
CREATE INDEX IF NOT EXISTS index_browsing_history_domain_timestamp ON browsing_history(domain, timestampMs);

-- 2. Blocked Keywords
CREATE TABLE IF NOT EXISTS blocked_keywords (
    keyword TEXT PRIMARY KEY NOT NULL,
    isBlocked INTEGER NOT NULL DEFAULT 1,
    category TEXT NOT NULL DEFAULT 'custom',
    createdAt INTEGER NOT NULL
);

-- 3. Blocked URL Paths
CREATE TABLE IF NOT EXISTS blocked_url_paths (
    pathPattern TEXT PRIMARY KEY NOT NULL,
    isBlocked INTEGER NOT NULL DEFAULT 1,
    createdAt INTEGER NOT NULL
);
```
