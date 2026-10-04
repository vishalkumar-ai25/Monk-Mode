package com.stayfocused.app.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.stayfocused.app.data.local.StayFocusedDatabase
import com.stayfocused.app.ui.MainActivity
import com.stayfocused.app.vpn.dns.DnsPacketParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Local loopback VpnService acting as an on-device DNS filter.
 * Configured with narrow routing (10.0.0.2/32) so that general internet traffic
 * never enters the tunnel, consuming zero battery overhead.
 */
class DnsVpnService : VpnService() {

    companion object {
        private const val TAG = "DnsVpnService"
        const val NOTIFICATION_CHANNEL_ID = "stayfocused_vpn_channel"
        const val NOTIFICATION_ID = 3001

        const val ACTION_START = "com.stayfocused.app.vpn.ACTION_START"
        const val ACTION_STOP = "com.stayfocused.app.vpn.ACTION_STOP"

        const val VPN_IP = "10.0.0.2"
        const val DNS_GATEWAY = "10.0.0.1"
        val DNS_UPSTREAMS = listOf(
            "8.8.8.8", "1.1.1.1", "8.8.4.4", "9.9.9.9",
            "2001:4860:4860::8888", "2606:4700:4700::1111"
        )
        const val DNS_PORT = 53

        @Volatile
        var isVpnRunning: Boolean = false
            private set

        @Suppress("DEPRECATION")
        fun getPhysicalDnsServers(context: Context): List<String> {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return DNS_UPSTREAMS
            val servers = mutableListOf<String>()
            try {
                val activeNetwork = cm.activeNetwork
                if (activeNetwork != null) {
                    val caps = cm.getNetworkCapabilities(activeNetwork)
                    if (caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
                        val lp = cm.getLinkProperties(activeNetwork)
                        lp?.dnsServers?.forEach { inetAddr ->
                            val host = inetAddr.hostAddress
                            if (host != null && !inetAddr.isLoopbackAddress && !inetAddr.isLinkLocalAddress && isAllowedUpstreamDnsHost(host)) {
                                servers.add(host)
                            }
                        }
                    }
                }

                if (servers.isEmpty()) {
                    for (network in cm.allNetworks) {
                        val caps = cm.getNetworkCapabilities(network) ?: continue
                        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        ) {
                            val lp = cm.getLinkProperties(network) ?: continue
                            for (inetAddr in lp.dnsServers) {
                                val host = inetAddr.hostAddress ?: continue
                                if (!inetAddr.isLoopbackAddress && !inetAddr.isLinkLocalAddress && isAllowedUpstreamDnsHost(host)) {
                                    servers.add(host)
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error discovering physical DNS servers", e)
            }
            return if (servers.isNotEmpty()) servers.distinct() else DNS_UPSTREAMS
        }

        private fun isAllowedUpstreamDnsHost(host: String): Boolean {
            return !host.startsWith("10.0.0.") &&
                    !host.startsWith("127.") &&
                    !host.startsWith("169.254.") &&
                    !host.startsWith("fe80:") &&
                    host != "::1"
        }

        fun isDomainBlocked(qname: String, blockedDomains: Set<String>): Boolean {
            val normalized = qname.trim().lowercase().removePrefix("www.")
            return blockedDomains.any { blocked ->
                val cleanBlocked = blocked.trim().lowercase().removePrefix("www.")
                normalized == cleanBlocked || normalized.endsWith(".$cleanBlocked")
            }
        }

        fun processDnsPacket(
            rawPacket: ByteArray,
            blockedDomains: Set<String>,
            upstreamResolver: (ByteArray) -> ByteArray?
        ): ByteArray? {
            // Drop outbound TCP 853 (DNS-over-TLS / DoT) to trigger graceful fallback to plaintext UDP 53
            if (DnsPacketParser.isTcpPort853(rawPacket)) {
                Log.d(TAG, "Dropping outbound TCP 853 (DoT) packet to force plaintext DNS fallback")
                return null
            }

            val parsed = DnsPacketParser.parseIpPacket(rawPacket) ?: return null
            val query = parsed.query ?: return null

            return if (isDomainBlocked(query.qname, blockedDomains)) {
                Log.i(TAG, "🚫 Synthesizing NXDOMAIN for BLOCKED domain: ${query.qname}")
                DnsPacketParser.createNxDomainResponse(rawPacket)
            } else {
                val upstreamResponse = upstreamResolver(parsed.dnsPayload)
                if (upstreamResponse != null && upstreamResponse.isNotEmpty()) {
                    DnsPacketParser.wrapDnsResponse(
                        dnsResponsePayload = upstreamResponse,
                        srcIp = parsed.destIp,
                        dstIp = parsed.sourceIp,
                        srcPort = parsed.destPort,
                        dstPort = parsed.sourcePort
                    )
                } else null
            }
        }

        /**
         * Detects if Private DNS is set to "Strict Mode" (hostname configured).
         * Note: In Private DNS strict mode, Android forces DNS-over-TLS (port 853) directly to the configured
         * hostname outside of the loopback VPN tunnel (bypassing 10.0.0.1:53).
         */
        fun isPrivateDnsStrictMode(context: Context): Boolean {
            return try {
                android.provider.Settings.Global.getString(context.contentResolver, "private_dns_mode") == "hostname"
            } catch (e: Exception) {
                false
            }
        }
    }

    class DnsLruCache(private val maxEntries: Int = 512) {
        data class CacheEntry(val payload: ByteArray, val expiryTimestamp: Long)
        private val map = object : LinkedHashMap<String, CacheEntry>(maxEntries, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean {
                return size > maxEntries
            }
        }

        @Synchronized
        fun get(key: String, now: Long): ByteArray? {
            val entry = map[key] ?: return null
            if (now >= entry.expiryTimestamp) {
                map.remove(key)
                return null
            }
            return entry.payload
        }

        @Synchronized
        fun put(key: String, payload: ByteArray, expiryTimestamp: Long) {
            map[key] = CacheEntry(payload, expiryTimestamp)
        }

        @Synchronized
        fun clear() {
            map.clear()
        }

        @Synchronized
        fun size(): Int = map.size
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var blockedDomains: Set<String> = emptySet()

    var upstreamDnsProvider: () -> List<String> = {
        getPhysicalDnsServers(applicationContext)
    }

    val dnsResponseCache = DnsLruCache(512)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        if (action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }

        // Call startForeground immediately to honor the 5-second Android foreground service SLA
        startForegroundNotification()
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return

        observeBlockedDomains()

        try {
            vpnInterface = Builder()
                .setSession("Stay Focused DNS Filter")
                .addAddress(VPN_IP, 24)
                .addDnsServer(DNS_GATEWAY)
                .addRoute(DNS_GATEWAY, 32) // Route DNS queries for 10.0.0.1 into TUN
                .setMtu(1500)
                .setBlocking(true)
                .establish()

            isVpnRunning = true
            Log.i(TAG, "DNS VPN TUN interface established with gateway $DNS_GATEWAY/32")
            startPacketLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Failed establishing VPN TUN interface", e)
            stopVpn()
        }
    }

    private fun startPacketLoop() {
        val pfd = vpnInterface ?: return
        serviceScope.launch(Dispatchers.IO) {
            val inputStream = FileInputStream(pfd.fileDescriptor)
            val outputStream = FileOutputStream(pfd.fileDescriptor)
            val packetBuffer = ByteArray(1500)
            val writeLock = Any()

            try {
                while (isVpnRunning) {
                    val bytesRead = inputStream.read(packetBuffer)
                    if (bytesRead < 0) break // TUN interface closed / EOF reached
                    if (bytesRead == 0) continue

                    val rawPacket = packetBuffer.copyOf(bytesRead)

                    // Launch concurrent worker for each DNS packet so no query blocks another
                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            val responsePacket = processDnsPacket(
                                rawPacket = rawPacket,
                                blockedDomains = blockedDomains,
                                upstreamResolver = { queryPayload ->
                                    resolveUpstream(queryPayload)
                                }
                            )

                            if (responsePacket != null) {
                                synchronized(writeLock) {
                                    outputStream.write(responsePacket)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error handling DNS query packet", e)
                        }
                    }
                }
            } catch (e: Exception) {
                if (isVpnRunning) {
                    Log.e(TAG, "Packet loop encountered error", e)
                }
            }
        }
    }

    private fun resolveUpstream(queryPayload: ByteArray): ByteArray? {
        val parsed = DnsPacketParser.parseDnsQuery(queryPayload)
        val now = System.currentTimeMillis()

        // 1. Check in-memory DNS LRU cache (case-insensitive lookup)
        if (parsed != null) {
            val cacheKey = "${parsed.qname.lowercase()}:${parsed.qtype}"
            val cached = dnsResponseCache.get(cacheKey, now)
            if (cached != null) {
                // Reuse response with current transaction ID
                val cachedPayload = cached.copyOf()
                cachedPayload[0] = (parsed.transactionId shr 8).toByte()
                cachedPayload[1] = parsed.transactionId.toByte()
                return cachedPayload
            }
        }

        // 2. Query upstream DNS servers with failover (capped to 4 distinct endpoints)
        val upstreams = upstreamDnsProvider().distinct().take(4)
        for (upstreamIp in upstreams) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                if (!protect(socket)) {
                    socket.close()
                    continue
                }
                socket.soTimeout = 1200

                val upstreamAddress = InetAddress.getByName(upstreamIp)
                socket.connect(upstreamAddress, DNS_PORT)

                val sendPacket = DatagramPacket(queryPayload, queryPayload.size, upstreamAddress, DNS_PORT)
                socket.send(sendPacket)

                val receiveBuffer = ByteArray(1500)
                val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
                socket.receive(receivePacket)

                val responsePayload = receiveBuffer.copyOf(receivePacket.length)
                if (responsePayload.size < 12) continue

                // Verify Transaction ID and QR response bit (byte 2 & 0x80 != 0)
                val respTxId = ((responsePayload[0].toInt() and 0xFF) shl 8) or (responsePayload[1].toInt() and 0xFF)
                val isResponse = (responsePayload[2].toInt() and 0x80) != 0
                if (parsed != null && (respTxId != parsed.transactionId || !isResponse)) {
                    continue
                }

                if (parsed != null) {
                    val rcode = responsePayload[3].toInt() and 0x0F
                    val ancount = ((responsePayload[6].toInt() and 0xFF) shl 8) or (responsePayload[7].toInt() and 0xFF)
                    // Do not cache SERVFAIL (2), NXDOMAIN (3), error rcodes, or empty answers
                    if (rcode == 0 && ancount > 0) {
                        val rawTtl = DnsPacketParser.extractMinTtl(responsePayload)
                        if (rawTtl != null && rawTtl > 0L) {
                            val clampedTtlSec = rawTtl.coerceIn(5L, 300L)
                            val expiry = now + (clampedTtlSec * 1000L)
                            val cacheKey = "${parsed.qname.lowercase()}:${parsed.qtype}"
                            dnsResponseCache.put(cacheKey, responsePayload, expiry)
                        }
                    }
                }
                if (responsePayload.isNotEmpty()) {
                    return responsePayload
                }
            } catch (e: Exception) {
                // Failover to next DNS upstream
                continue
            } finally {
                try {
                    socket?.close()
                } catch (e: Exception) {
                    // Ignored
                }
            }
        }

        return null
    }

    private fun observeBlockedDomains() {
        serviceScope.launch {
            try {
                val db = StayFocusedDatabase.getInstance(applicationContext)
                val globalFlow = db.blockedDomainDao().getAllBlockedDomains()
                val profileFlow = db.focusProfileDao().getActiveBlockedDomainsFlow()

                kotlinx.coroutines.flow.combine(globalFlow, profileFlow) { globalEntities, profileDomains ->
                    val globalSet = globalEntities.filter { it.isBlocked }.map { it.domain.lowercase() }
                    val profileSet = profileDomains.map { it.lowercase() }
                    (globalSet + profileSet).toSet()
                }
                    .catch { e -> Log.e(TAG, "Error observing blocked domains union", e) }
                    .collectLatest { unionBlockedDomains ->
                        blockedDomains = unionBlockedDomains
                        Log.i(TAG, "Updated blocked domains (union of global + active profiles): $blockedDomains")
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Database not available for VPN domain observation", e)
            }
        }
    }

    override fun onRevoke() {
        Log.w(TAG, "VPN service revoked by system or user")
        stopVpn()

        // 1. Mark protection RED and post watchdog alert immediately (force = true)
        val prefs = com.stayfocused.app.util.ProtectionPreferences(applicationContext)
        prefs.lastRedAlertTimestamp = System.currentTimeMillis()
        try {
            com.stayfocused.app.worker.WatchdogWorker.postProtectionAlertNotification(
                context = applicationContext,
                force = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed posting protection alert notification on VPN revoke", e)
        }

        // 2. Log FailsafeLog event in background without getting cancelled by serviceScope
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = StayFocusedDatabase.getInstance(applicationContext)
                db.failsafeLogDao().insertLog(
                    com.stayfocused.app.data.local.entities.FailsafeLogEntity(
                        timestamp = System.currentTimeMillis(),
                        eventType = "VPN_REVOKED",
                        details = "DnsVpnService revoked by system or user in settings",
                        success = false
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed logging VPN_REVOKED failsafe event", e)
            }
        }

        super.onRevoke()
    }

    private fun stopVpn() {
        isVpnRunning = false
        serviceScope.cancel()
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            // Ignored
        }
        vpnInterface = null
        dnsResponseCache.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun startForegroundNotification() {
        createNotificationChannel()

        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Website Filter Active")
            .setContentText("Focus sessions and blocked websites are protected.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Website Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows persistent status while website blocking DNS tunnel is active"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
