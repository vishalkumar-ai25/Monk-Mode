package com.stayfocused.app.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
        const val DNS_UPSTREAM = "1.1.1.1"
        const val DNS_PORT = 53

        @Volatile
        var isVpnRunning: Boolean = false
            private set

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
                Log.d(TAG, "Synthesizing NXDOMAIN for blocked domain: ${query.qname}")
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
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val blockedDomainsCache: MutableSet<String> = ConcurrentHashMap.newKeySet()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        if (action == ACTION_STOP) {
            stopVpn()
            return START_NOT_STICKY
        }

        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (vpnInterface != null) return

        startForegroundNotification()
        observeBlockedDomains()

        try {
            vpnInterface = Builder()
                .setSession("Stay Focused DNS Filter")
                .addAddress(VPN_IP, 32)
                .addDnsServer(VPN_IP)
                .addRoute(VPN_IP, 32) // Narrow routing: strictly DNS IP
                .setMtu(1500)
                .setBlocking(true)
                .establish()

            isVpnRunning = true
            Log.i(TAG, "DNS VPN TUN interface established with narrow route $VPN_IP/32")
            startPacketLoop()
        } catch (e: Exception) {
            Log.e(TAG, "Failed establishing VPN TUN interface", e)
            stopVpn()
        }
    }

    private fun startPacketLoop() {
        val pfd = vpnInterface ?: return
        serviceScope.launch {
            val inputStream = FileInputStream(pfd.fileDescriptor)
            val outputStream = FileOutputStream(pfd.fileDescriptor)
            val packetBuffer = ByteArray(1500)

            val upstreamSocket = DatagramSocket().apply {
                protect(this)
                soTimeout = 3000
            }
            val upstreamAddress = InetAddress.getByName(DNS_UPSTREAM)

            try {
                while (isVpnRunning) {
                    val bytesRead = inputStream.read(packetBuffer)
                    if (bytesRead <= 0) continue

                    val rawPacket = packetBuffer.copyOf(bytesRead)
                    val responsePacket = processDnsPacket(
                        rawPacket = rawPacket,
                        blockedDomains = blockedDomainsCache.toSet(),
                        upstreamResolver = { queryDnsPayload ->
                            try {
                                val sendPacket = DatagramPacket(queryDnsPayload, queryDnsPayload.size, upstreamAddress, DNS_PORT)
                                upstreamSocket.send(sendPacket)

                                val receiveBuffer = ByteArray(1500)
                                val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
                                upstreamSocket.receive(receivePacket)
                                receiveBuffer.copyOf(receivePacket.length)
                            } catch (e: Exception) {
                                null
                            }
                        }
                    )

                    if (responsePacket != null) {
                        outputStream.write(responsePacket)
                    }
                }
            } catch (e: Exception) {
                if (isVpnRunning) {
                    Log.e(TAG, "Packet loop encountered error", e)
                }
            } finally {
                upstreamSocket.close()
            }
        }
    }

    private fun observeBlockedDomains() {
        serviceScope.launch {
            try {
                val db = StayFocusedDatabase.getInstance(applicationContext)
                db.blockedDomainDao().getAllBlockedDomains()
                    .catch { e -> Log.e(TAG, "Error observing blocked domains", e) }
                    .collectLatest { domainEntities ->
                        blockedDomainsCache.clear()
                        blockedDomainsCache.addAll(domainEntities.filter { it.isBlocked }.map { it.domain.lowercase() })
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Database not available for VPN domain observation", e)
            }
        }
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
