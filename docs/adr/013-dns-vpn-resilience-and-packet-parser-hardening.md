# ADR 013: DNS VPN Filter Resilience, Dynamic Upstream, LRU Caching, and Packet Parser Hardening

## Status
Accepted (Hardened via Adversarial Security Review)

## Context and Problem Statement
Stay Focused / Monk Mode enforces website blocking through an on-device loopback `VpnService` (`DnsVpnService`) capturing UDP DNS queries destined for `10.0.0.1:53`.
An adversarial security audit identified critical vulnerabilities and edge cases across Android 12+/14:
1. **Self-Referential DNS Loop (`10.0.0.1` Deadlock)**: Querying `ConnectivityManager.getLinkProperties(cm.activeNetwork)?.dnsServers` while the VPN is active causes Android to return `10.0.0.1` as the active DNS server. The upstream resolver then queries itself in an infinite loop, breaking all internet access.
2. **Negative Response Caching & Unbounded Cache**: Transitory upstream errors (`SERVFAIL`, `NXDOMAIN`) were cached for 60 seconds, poisoning legitimate domain lookups. Wire-format TTL parsing lacked RFC 1035 wire-pointer awareness.
3. **Android 12+/14 Background Service Crash on Boot**: Calling `context.startService(...)` from `BootCompletedReceiver` throws `BackgroundServiceStartNotAllowedException`. Furthermore, Android 14 FGS requires `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` within 5 seconds.
4. **Alert Suppression Inversion Bug in `onRevoke()`**: Setting `lastRedAlertTimestamp = now` in `ProtectionPreferences` without posting a notification triggers WatchdogWorker's 60-minute cooldown, suppressing all alerts for an hour!
5. **Reactive Desynchronization in `FocusProfileDao`**: `getActiveBlockedDomains()` was a `suspend` query rather than a reactive `Flow`, preventing real-time synchronization when focus profiles were toggled.
6. **Packet Parser Boundary Vulnerabilities**: `ihl < 20`, invalid `totalLength`, negative UDP lengths, and circular compression pointers risked buffer overreads or silent packet drops.

## Decision & Hardening Contract

### 1. Dynamic Physical Upstream DNS Resolution
- Maintain upstream DNS servers dynamically from underlying non-VPN physical networks:
  ```kotlin
  fun getPhysicalDnsServers(context: Context): List<String> {
      val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return DNS_UPSTREAMS
      val servers = mutableListOf<String>()
      for (network in cm.allNetworks) {
          val caps = cm.getNetworkCapabilities(network) ?: continue
          if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
              caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
              val lp = cm.getLinkProperties(network) ?: continue
              for (inetAddr in lp.dnsServers) {
                  val host = inetAddr.hostAddress ?: continue
                  // Exclude loopback, link-local, and VPN gateway (10.0.0.1)
                  if (!host.startsWith("10.0.0.") && !host.startsWith("127.") && !host.startsWith("169.254.") && !host.startsWith("fe80:")) {
                      servers.add(host)
                  }
              }
          }
      }
      return if (servers.isNotEmpty()) servers else DNS_UPSTREAMS
  }
  ```
- Support dual-stack fallbacks:
  `val DNS_UPSTREAMS = listOf("8.8.8.8", "1.1.1.1", "2001:4860:4860::8888", "2606:4700:4700::1111", "8.8.4.4", "9.9.9.9")`
- In `resolveUpstream`:
  - Check return value: `if (!protect(socket)) return null`.

### 2. RFC 1035 TTL Parsing & 512-Entry Synchronized LRU Cache
- In `DnsPacketParser`:
  - Implement `skipWireName(buffer, offset): Int` that correctly counts consumed bytes on the wire (2 bytes if pointer `0xC0`, or sum of label lengths + null terminator).
  - Terminate immediately after `ANCOUNT` records; never scan Authority or Additional sections (preventing EDNS0 OPT pollution).
  - Read TTL as unsigned 32-bit: `((b0.toLong() and 0xFF) shl 24) or ...`.
  - If `minTtl <= 0L`, do NOT cache.
  - Clamp TTL to 5s–300s (5 minutes).
  - Do NOT cache if `RCODE != 0` (e.g. `SERVFAIL` or `NXDOMAIN`) or `ANCOUNT == 0`.
- Thread-Safe Bounded LRU Cache:
  - Synchronized `LinkedHashMap` capped at 512 entries with `removeEldestEntry { size > 512 }`.

### 3. Safe Autostart in BootCompletedReceiver
- Use `androidx.core.content.ContextCompat.startForegroundService(context, intent)`.
- Ensure autostart runs on boot when VPN permission is already prepared (`VpnService.prepare(context) == null`).
- In `DnsVpnService.onStartCommand()`: Call `startForegroundNotification()` immediately on the first line to guarantee compliance with the 5-second Android foreground service SLA.

### 4. Robust onRevoke() Override
- On VPN revocation:
  - Insert log using `NonCancellable` IO coroutine:
    `FailsafeLogEntity(timestamp = System.currentTimeMillis(), eventType = "VPN_REVOKED", details = "DNS VPN revoked", success = false)`
  - Mark protection red and post high-priority alert notification immediately (preventing alert suppression inversion).

### 5. Reactive Focus Profile Flow
- In `FocusProfileDao`:
  Add reactive Flow:
  ```kotlin
  @Query("""
      SELECT DISTINCT d.domain 
      FROM profile_blocked_domains d
      INNER JOIN focus_profiles f ON d.profileId = f.id
      WHERE f.isActive = 1
  """)
  fun getActiveBlockedDomainsFlow(): Flow<List<String>>
  ```
- In `DnsVpnService`:
  Combine `blockedDomainDao.getAllBlockedDomains()` and `focusProfileDao.getActiveBlockedDomainsFlow()`.

### 6. Domain Normalization at DAO Boundary
- Normalize domains before insert/lookup:
  `cleanDomain = domain.trim().trimEnd('.').lowercase()`
  Reject empty, single-label (no dot), or wildcard `*`.

### 7. DnsPacketParser Hardening & Fuzz Defense
- Validate IPv4 header:
  `ihl in 20..packet.size`, `totalLength in (ihl + 8)..packet.size`, `udpLength in 8..(totalLength - ihl)`.
- Cap compression pointer jumps at 8, enforce `pointerOffset >= 12` and backward-only pointers (`pointerOffset < offset`).
- Enforce domain wire-termination with root null byte (`len == 0`).
- Add comprehensive fuzz tests.

### 8. Narrow TUN Routing, Dead DoT Drop, and Private DNS Strict Mode Detection
- **Narrow TUN Scope**: By using `.addRoute("10.0.0.1", 32)`, only IP packets explicitly addressed to the synthetic DNS gateway `10.0.0.1` enter the TUN interface. General internet traffic and outbound TCP 853 packets to external DoT servers bypass the TUN interface completely and flow over physical interfaces.
- **Dead DoT Drop Branch**: Because external TCP 853 traffic never enters the `10.0.0.1/32` tunnel, inspecting or dropping TCP 853 inside `processDnsPacket` cannot intercept external DNS-over-TLS connections.
- **Mitigation & Detection**: `DnsVpnService.isPrivateDnsStrictMode(context)` checks `Settings.Global.getString(cr, "private_dns_mode") == "hostname"`. If Android's Private DNS strict mode is active, the system routes DNS queries over encrypted TLS to the configured hostname, bypassing local UDP port 53. Monk Mode detects this state and surfaces a high-priority warning in `ProtectionStatusCard` prompting the user to disable or set Private DNS to Automatic. Browser-level DNS-over-HTTPS (DoH) is similarly governed through browser-level settings tamper inspection.
