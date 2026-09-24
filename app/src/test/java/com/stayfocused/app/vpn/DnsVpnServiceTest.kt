package com.stayfocused.app.vpn

import com.stayfocused.app.vpn.dns.DnsPacketParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DnsVpnServiceTest {

    @Test
    fun testDomainMatchingExactAndSubdomains() {
        val blocked = setOf("reddit.com", "instagram.com", "tiktok.com")

        // Exact matches
        assertTrue(DnsVpnService.isDomainBlocked("reddit.com", blocked))
        assertTrue(DnsVpnService.isDomainBlocked("instagram.com", blocked))
        assertTrue(DnsVpnService.isDomainBlocked("tiktok.com", blocked))

        // www prefix matches
        assertTrue(DnsVpnService.isDomainBlocked("www.reddit.com", blocked))
        assertTrue(DnsVpnService.isDomainBlocked("www.instagram.com", blocked))

        // Subdomain matches
        assertTrue(DnsVpnService.isDomainBlocked("api.reddit.com", blocked))
        assertTrue(DnsVpnService.isDomainBlocked("m.instagram.com", blocked))
        assertTrue(DnsVpnService.isDomainBlocked("v2.api.tiktok.com", blocked))

        // Non-blocked domains
        assertFalse(DnsVpnService.isDomainBlocked("google.com", blocked))
        assertFalse(DnsVpnService.isDomainBlocked("wikipedia.org", blocked))
        assertFalse(DnsVpnService.isDomainBlocked("notreddit.com", blocked))
        assertFalse(DnsVpnService.isDomainBlocked("myinstagram.com", blocked))
    }

    @Test
    fun testProcessPacketForBlockedDomainGeneratesNxDomain() {
        val blockedDomains = setOf("twitter.com")
        val dnsPayload = buildSampleDnsQueryPayload("twitter.com", txId = 0x3333)
        val rawQueryPacket = buildSampleIpv4UdpPacket(dnsPayload)

        var upstreamForwarded = false
        val resultPacket = DnsVpnService.processDnsPacket(
            rawPacket = rawQueryPacket,
            blockedDomains = blockedDomains,
            upstreamResolver = { _ ->
                upstreamForwarded = true
                byteArrayOf()
            }
        )

        assertNotNull("Result packet must be returned for blocked domain", resultPacket)
        assertFalse("Upstream resolver must NOT be queried for blocked domain", upstreamForwarded)

        val parsed = DnsPacketParser.parseIpPacket(resultPacket!!)
        assertNotNull(parsed)
        val flags = ((parsed!!.dnsPayload[2].toInt() and 0xFF) shl 8) or (parsed.dnsPayload[3].toInt() and 0xFF)
        val rcode = flags and 0x0F
        assertEquals("RCODE must be NXDOMAIN (3)", 3, rcode)
    }

    @Test
    fun testProcessPacketForAllowedDomainQueriesUpstream() {
        val blockedDomains = setOf("twitter.com")
        val dnsPayload = buildSampleDnsQueryPayload("github.com", txId = 0x4444)
        val rawQueryPacket = buildSampleIpv4UdpPacket(dnsPayload)

        var upstreamQueried = false
        val mockUpstreamResponse = byteArrayOf(0x44, 0x44.toByte(), 0x81.toByte(), 0x80.toByte(), 0, 1, 0, 1, 0, 0, 0, 0)

        val resultPacket = DnsVpnService.processDnsPacket(
            rawPacket = rawQueryPacket,
            blockedDomains = blockedDomains,
            upstreamResolver = { queryPayload ->
                upstreamQueried = true
                mockUpstreamResponse
            }
        )

        assertTrue("Upstream resolver MUST be queried for allowed domain", upstreamQueried)
        assertNotNull(resultPacket)

        val parsed = DnsPacketParser.parseIpPacket(resultPacket!!)
        assertNotNull(parsed)
        assertEquals(53, parsed?.sourcePort)
        assertEquals(54321, parsed?.destPort)
    }

    private fun buildSampleDnsQueryPayload(domain: String, txId: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf((txId shr 8).toByte(), txId.toByte()))
        out.write(byteArrayOf(0x01, 0x00))
        out.write(byteArrayOf(0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
        for (label in domain.split(".")) {
            out.write(label.length)
            out.write(label.toByteArray(Charsets.US_ASCII))
        }
        out.write(0)
        out.write(byteArrayOf(0x00, 0x01, 0x00, 0x01))
        return out.toByteArray()
    }

    private fun buildSampleIpv4UdpPacket(dnsPayload: ByteArray): ByteArray {
        val totalLength = 20 + 8 + dnsPayload.size
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x45, 0x00))
        out.write(byteArrayOf((totalLength shr 8).toByte(), totalLength.toByte()))
        out.write(byteArrayOf(0x00, 0x01, 0x40, 0x00, 64, 17, 0, 0))
        out.write(byteArrayOf(10, 0, 0, 2)) // src
        out.write(byteArrayOf(1, 1, 1, 1))  // dst
        out.write(byteArrayOf((54321 shr 8).toByte(), 54321.toByte())) // src port
        out.write(byteArrayOf(0x00, 0x35)) // dst port 53
        val udpLen = 8 + dnsPayload.size
        out.write(byteArrayOf((udpLen shr 8).toByte(), udpLen.toByte(), 0, 0))
        out.write(dnsPayload)

        val packet = out.toByteArray()
        val csum = DnsPacketParser.computeIpChecksum(packet, 0, 20)
        packet[10] = (csum shr 8).toByte()
        packet[11] = csum.toByte()
        return packet
    }
}
