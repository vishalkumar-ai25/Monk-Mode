# ADR 001: Local VpnService DNS Proxy vs. Accessibility URL Scraping for Website Blocking

- **Status:** Accepted
- **Date:** 2026-09-24
- **Deciders:** Engineering / Architecture Team
- **Technical Context:** Android 8.0 (API 26) through Android 14 (API 34), Realme C65 5G (realme UI 5.0).

---

## 1. Context & Problem Statement

"Stay Focused" requires an airtight, low-latency, and privacy-preserving mechanism to enforce domain-level website blocking (e.g., blocking `reddit.com`, `instagram.com`, `twitter.com`, etc.) across all web browsers and WebViews without battery drain or user bypass loopholes.

Historically, Android digital wellbeing apps attempt website blocking using one of two techniques:
1. **Accessibility Service URL Scraping:** Periodically scraping the text inside the browser's address bar (`EditText` or `TextView` matching known browser view IDs).
2. **Local Loopback VpnService DNS Proxy:** Creating an on-device VPN interface that intercepts DNS requests (port 53) and returns synthetic `NXDOMAIN` or `0.0.0.0` responses for blocked domains.

We must formalize our architectural decision, evaluate the technical trade-offs, and define the packet-level specification.

---

## 2. Options Considered

### Option A: Accessibility Service Address-Bar Scraping
- **Mechanism:** Register for `TYPE_WINDOW_CONTENT_CHANGED` and query view hierarchy nodes using `AccessibilityNodeInfo.findAccessibilityNodeInfosByViewId` or heuristics on browser UI elements.
- **Critical Flaws & Rejection Rationale:**
  1. **Extreme Flakiness Across Browser Updates:** Major browsers (Chrome, Firefox, Brave, Edge, Opera, Samsung Internet) frequently rename resource IDs or mutate view hierarchies between weekly updates, breaking scrapers without warning.
  2. **Incognito & Private Tabs:** In Chrome and Firefox private browsing modes, address bar inspection is often locked or obscured by OEM flags, allowing users to bypass domain blocks trivially.
  3. **High Battery & CPU Overhead:** Continuous hierarchy dumps on every character typed or page scrolled consume substantial CPU cycles and cause UI jank.
  4. **WebView Blindness:** In-app WebViews (e.g. inside messaging or news apps) do not render standard browser address bars, allowing blocked websites to load uninhibited.
  5. **Privacy Degradation:** Requiring `canRetrieveWindowContent="true"` grants the app permission to read user text on-screen, violating our strict personal privacy guarantees.

### Option B: Local Loopback `VpnService` DNS Proxy (Selected)
- **Mechanism:** Establish a local Android `VpnService` TUN interface that acts as an in-device DNS proxy.
- **Strengths:**
  1. **100% Browser Agnostic:** Intercepts DNS queries system-wide at the network layer. Applies universally to Chrome, Firefox, DuckDuckGo, Brave, Samsung Internet, and embedded WebViews.
  2. **Zero Screen Scraping & Complete Privacy:** `canRetrieveWindowContent="false"` is preserved in the accessibility service. The app never reads what is displayed on the screen.
  3. **Sub-Millisecond Resolution:** DNS packet inspection and synthetic response injection execute in microseconds in memory.
  4. **Zero Battery Bottleneck (Narrow Tunnel Routing):** By configuring the VPN route strictly to the local DNS IP (`10.0.0.2/32`), general TCP/UDP internet traffic never enters the VPN TUN interface. Only port 53 DNS queries enter the tunnel.

---

## 3. Decision

We **adopt Option B: Local Loopback `VpnService` DNS Proxy** as the primary and authoritative website blocking mechanism for Stay Focused.

Accessibility Service inspection will **never** be used for blocking decisions. (If ever used post-MVP, it would strictly be an optional visual enhancement to display a contextual "Why this site was blocked" banner).

---

## 4. Technical Architecture & Implementation Details

```
+-------------------------------------------------------------+
|                     Android Application                     |
|                                                             |
|   +-------------------+              +------------------+   |
|   |   Web Browser     |              |  Embedded Web    |   |
|   |  (Chrome/Firefox) |              |      View        |   |
|   +---------+---------+              +--------+---------+   |
|             |                                 |             |
|             +----------------+----------------+             |
|                              |                              |
|                  DNS Query (UDP/TCP Port 53)                |
|                              v                              |
|             +---------------------------------+             |
|             | TUN Interface (10.0.0.2/32)    |             |
|             +----------------+----------------+             |
+------------------------------|------------------------------+
                               v
               +-------------------------------+
               |   Pure Kotlin DnsPacketParser |
               +---------------+---------------+
                               |
              [Is Domain in BlockedDomainEntity?]
                              / \
                             /   \
                       YES  /     \  NO
                           /       \
                          v         v
     +-----------------------+   +---------------------------+
     | Synthesize NXDOMAIN / |   | Forward to Upstream       |
     | 0.0.0.0 Response      |   | Resolver (1.1.1.1:53)     |
     +-----------+-----------+   +-------------+-------------+
                 |                             |
                 +--------------+--------------+
                                |
                                v
               [Relay Response to TUN fd]
```

### 1. Narrow TUN Routing
The `VpnService.Builder` establishes:
- VPN IP: `10.0.0.2/32`
- DNS Server: `10.0.0.2`
- Route: `10.0.0.2/32`

Because only `10.0.0.2/32` is routed through the TUN interface, standard web traffic (HTTPS port 443, HTTP port 80, video streaming, downloads) routes directly through the physical Wi-Fi or Cellular network interface without passing through our application code.

### 2. RFC 1035 Packet Parsing & Synthesis
- **Incoming:** Decodes UDP packets destined for port 53. Parses the DNS header and Question section to extract the QNAME (queried domain name).
- **Matching:** Normalizes the queried domain (e.g. `www.reddit.com` $\to$ `reddit.com`) and queries the Room database `BlockedDomainEntity`.
- **Response Synthesis:**
  - If **blocked**: Immediately synthesizes an RFC 1035 response packet with:
    - QR = 1 (Response)
    - AA = 1 (Authoritative)
    - RA = 1 (Recursion Available)
    - RCODE = 3 (`NXDOMAIN`) or RCODE = 0 with an `A` record answering `0.0.0.0`.
    - Swaps source/destination IP and UDP ports and recalculates IP and UDP checksums.
    - Writes the synthetic packet back to the TUN file descriptor.
  - If **allowed**: Forwards the raw query to an upstream public DNS resolver (`1.1.1.1` or `8.8.8.8`) via standard UDP socket, and writes the response back to the TUN file descriptor.

### 3. Private DNS (DoT / DoH) Strategy
Android 9.0+ introduced Private DNS (DNS-over-TLS via port 853).
- **Primary Configuration:** Onboarding displays a clear guidance notice instructing the user to set Android's Private DNS setting to "Off" (or "Automatic").
- **Port 853 Backstop:** The VPN drops outbound TCP port 853 to trigger graceful fallback to plaintext DNS when Private DNS is in "Automatic" mode.

---

## 5. Consequences

### Positive
- Universal protection across all web browsers and WebViews.
- Complete privacy preservation: screen content is never read or inspected.
- Negligible battery and CPU impact due to narrow routing.
- Sub-millisecond blocking latency.

### Negative / Mitigations
- Cannot run concurrently with third-party commercial VPN apps (Android allows only one active `VpnService` at a time).
  - *Mitigation:* Documented in user onboarding.
- Requires user to configure Private DNS to "Off" for 100% enforcement.
  - *Mitigation:* Explicit, step-by-step onboarding guide and TCP 853 drop backstop.
