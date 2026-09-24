package com.stayfocused.app.vpn.dns

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class DnsPacketParserTest {

    private fun buildSampleDnsQueryPayload(domain: String, txId: Int = 0x1234): ByteArray {
        val out = ByteArrayOutputStream()
        // DNS Header (12 bytes)
        out.write(byteArrayOf((txId shr 8).toByte(), txId.toByte())) // ID
        out.write(byteArrayOf(0x01, 0x00)) // Flags: Standard query, RD=1
        out.write(byteArrayOf(0x00, 0x01)) // QDCOUNT = 1
        out.write(byteArrayOf(0x00, 0x00)) // ANCOUNT = 0
        out.write(byteArrayOf(0x00, 0x00)) // NSCOUNT = 0
        out.write(byteArrayOf(0x00, 0x00)) // ARCOUNT = 0

        // Question: QNAME
        for (label in domain.split(".")) {
            out.write(label.length)
            out.write(label.toByteArray(Charsets.US_ASCII))
        }
        out.write(0) // End of QNAME

        // QTYPE = 1 (A), QCLASS = 1 (IN)
        out.write(byteArrayOf(0x00, 0x01, 0x00, 0x01))
        return out.toByteArray()
    }

    private fun buildSampleIpv4UdpPacket(
        srcIp: ByteArray = byteArrayOf(10, 0, 0, 2),
        dstIp: ByteArray = byteArrayOf(1, 1, 1, 1),
        srcPort: Int = 54321,
        dstPort: Int = 53,
        dnsPayload: ByteArray
    ): ByteArray {
        val totalLength = 20 + 8 + dnsPayload.size
        val out = ByteArrayOutputStream()

        // IPv4 Header (20 bytes)
        out.write(byteArrayOf(0x45, 0x00)) // Version 4, IHL 5, DSCP/ECN 0
        out.write(byteArrayOf((totalLength shr 8).toByte(), totalLength.toByte()))
        out.write(byteArrayOf(0x1a, 0x2b)) // Identification
        out.write(byteArrayOf(0x40, 0x00)) // Flags: Don't fragment
        out.write(byteArrayOf(64, 17)) // TTL 64, Protocol UDP (17)
        out.write(byteArrayOf(0x00, 0x00)) // Checksum placeholder
        out.write(srcIp)
        out.write(dstIp)

        // UDP Header (8 bytes)
        val udpLength = 8 + dnsPayload.size
        out.write(byteArrayOf((srcPort shr 8).toByte(), srcPort.toByte()))
        out.write(byteArrayOf((dstPort shr 8).toByte(), dstPort.toByte()))
        out.write(byteArrayOf((udpLength shr 8).toByte(), udpLength.toByte()))
        out.write(byteArrayOf(0x00, 0x00)) // UDP checksum placeholder

        // DNS Payload
        out.write(dnsPayload)

        val packet = out.toByteArray()
        // Compute and inject real IPv4 checksum
        val checksum = DnsPacketParser.computeIpChecksum(packet, 0, 20)
        packet[10] = (checksum shr 8).toByte()
        packet[11] = checksum.toByte()

        return packet
    }

    @Test
    fun testParseDnsQuestionDomainFromPayload() {
        val dnsPayload = buildSampleDnsQueryPayload("reddit.com", txId = 0x4321)
        val query = DnsPacketParser.parseDnsQuery(dnsPayload)

        assertNotNull("DnsQuery should be parsed", query)
        assertEquals(0x4321, query?.transactionId)
        assertEquals("reddit.com", query?.qname)
        assertEquals(1, query?.qtype)
        assertEquals(1, query?.qclass)
    }

    @Test
    fun testParseSubdomainDnsQuestion() {
        val dnsPayload = buildSampleDnsQueryPayload("api.v2.instagram.com", txId = 0x7777)
        val query = DnsPacketParser.parseDnsQuery(dnsPayload)

        assertNotNull(query)
        assertEquals("api.v2.instagram.com", query?.qname)
        assertEquals(0x7777, query?.transactionId)
    }

    @Test
    fun testParseFullIpUdpPacket() {
        val dnsPayload = buildSampleDnsQueryPayload("tiktok.com", txId = 0x9999)
        val rawPacket = buildSampleIpv4UdpPacket(
            srcIp = byteArrayOf(10, 0, 0, 2),
            dstIp = byteArrayOf(8, 8, 8, 8),
            srcPort = 60000,
            dstPort = 53,
            dnsPayload = dnsPayload
        )

        val parsed = DnsPacketParser.parseIpPacket(rawPacket)
        assertNotNull(parsed)
        assertEquals("10.0.0.2", parsed?.sourceIp)
        assertEquals("8.8.8.8", parsed?.destIp)
        assertEquals(60000, parsed?.sourcePort)
        assertEquals(53, parsed?.destPort)
        assertEquals("tiktok.com", parsed?.query?.qname)
        assertEquals(0x9999, parsed?.query?.transactionId)
    }

    @Test
    fun testSynthesizeNxDomainResponse() {
        val dnsPayload = buildSampleDnsQueryPayload("blocked-site.com", txId = 0x1234)
        val rawQueryPacket = buildSampleIpv4UdpPacket(
            srcIp = byteArrayOf(10, 0, 0, 2),
            dstIp = byteArrayOf(1, 1, 1, 1),
            srcPort = 55555,
            dstPort = 53,
            dnsPayload = dnsPayload
        )

        val responsePacket = DnsPacketParser.createNxDomainResponse(rawQueryPacket)
        assertNotNull("Response packet should be synthesized", responsePacket)

        // Parse synthesized response
        val parsedResponse = DnsPacketParser.parseIpPacket(responsePacket!!)
        assertNotNull(parsedResponse)

        // IPs and Ports should be swapped
        assertEquals("1.1.1.1", parsedResponse?.sourceIp)
        assertEquals("10.0.0.2", parsedResponse?.destIp)
        assertEquals(53, parsedResponse?.sourcePort)
        assertEquals(55555, parsedResponse?.destPort)

        // Verify DNS header in response: QR=1 (response), RCODE=3 (NXDOMAIN)
        val dnsResponse = parsedResponse?.dnsPayload ?: byteArrayOf()
        val flags = ((dnsResponse[2].toInt() and 0xFF) shl 8) or (dnsResponse[3].toInt() and 0xFF)
        val qr = (flags shr 15) and 0x01
        val rcode = flags and 0x0F
        val ancount = ((dnsResponse[6].toInt() and 0xFF) shl 8) or (dnsResponse[7].toInt() and 0xFF)

        assertEquals("QR flag should be 1 (response)", 1, qr)
        assertEquals("RCODE should be 3 (NXDOMAIN)", 3, rcode)
        assertEquals("ANCOUNT should be 0", 0, ancount)

        // Verify IP checksum is valid
        val computedChecksum = DnsPacketParser.computeIpChecksum(responsePacket, 0, 20)
        assertEquals("IP checksum over valid header should be 0", 0, computedChecksum)
    }

    @Test
    fun testSynthesizeSinkholeResponseWithZeroIp() {
        val dnsPayload = buildSampleDnsQueryPayload("gambling.com", txId = 0x5678)
        val rawQueryPacket = buildSampleIpv4UdpPacket(
            srcIp = byteArrayOf(10, 0, 0, 2),
            dstIp = byteArrayOf(1, 1, 1, 1),
            srcPort = 44444,
            dstPort = 53,
            dnsPayload = dnsPayload
        )

        val responsePacket = DnsPacketParser.createSinkholeResponse(
            rawQueryPacket,
            sinkholeIp = byteArrayOf(0, 0, 0, 0)
        )
        assertNotNull(responsePacket)

        val parsedResponse = DnsPacketParser.parseIpPacket(responsePacket!!)
        assertNotNull(parsedResponse)

        // Verify DNS header: QR=1, RCODE=0 (NoError), ANCOUNT=1
        val dnsResponse = parsedResponse?.dnsPayload ?: byteArrayOf()
        val flags = ((dnsResponse[2].toInt() and 0xFF) shl 8) or (dnsResponse[3].toInt() and 0xFF)
        val rcode = flags and 0x0F
        val ancount = ((dnsResponse[6].toInt() and 0xFF) shl 8) or (dnsResponse[7].toInt() and 0xFF)

        assertEquals("RCODE should be 0 (NoError)", 0, rcode)
        assertEquals("ANCOUNT should be 1", 1, ancount)

        // Check A record IP in answer matches 0.0.0.0 (last 4 bytes of packet)
        val last4 = responsePacket.copyOfRange(responsePacket.size - 4, responsePacket.size)
        assertEquals(0.toByte(), last4[0])
        assertEquals(0.toByte(), last4[1])
        assertEquals(0.toByte(), last4[2])
        assertEquals(0.toByte(), last4[3])
    }

    @Test
    fun testIsTcpPort853Detection() {
        val tcp853Packet = buildSampleIpv4TcpPacket(dstPort = 853)
        assertTrue("TCP 853 packet should be detected", DnsPacketParser.isTcpPort853(tcp853Packet))

        val tcp443Packet = buildSampleIpv4TcpPacket(dstPort = 443)
        org.junit.Assert.assertFalse("TCP 443 packet should not be detected as 853", DnsPacketParser.isTcpPort853(tcp443Packet))

        val udpPacket = buildSampleIpv4UdpPacket(dnsPayload = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12))
        org.junit.Assert.assertFalse("UDP packet should not be detected as TCP 853", DnsPacketParser.isTcpPort853(udpPacket))

        org.junit.Assert.assertFalse("Short packet should return false", DnsPacketParser.isTcpPort853(byteArrayOf(1, 2, 3)))
    }

    private fun buildSampleIpv4TcpPacket(
        srcIp: ByteArray = byteArrayOf(10, 0, 0, 2),
        dstIp: ByteArray = byteArrayOf(1, 1, 1, 1),
        srcPort: Int = 49152,
        dstPort: Int = 853
    ): ByteArray {
        val totalLength = 20 + 20
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x45, 0x00))
        out.write(byteArrayOf((totalLength shr 8).toByte(), totalLength.toByte()))
        out.write(byteArrayOf(0x1a, 0x2b, 0x40, 0x00))
        out.write(byteArrayOf(64, 6)) // TTL 64, Protocol TCP (6)
        out.write(byteArrayOf(0x00, 0x00))
        out.write(srcIp)
        out.write(dstIp)

        out.write(byteArrayOf((srcPort shr 8).toByte(), srcPort.toByte()))
        out.write(byteArrayOf((dstPort shr 8).toByte(), dstPort.toByte()))
        out.write(byteArrayOf(0x00, 0x00, 0x00, 0x01))
        out.write(byteArrayOf(0x00, 0x00, 0x00, 0x00))
        out.write(byteArrayOf(0x50, 0x02))
        out.write(byteArrayOf(0x72, 0x10.toByte()))
        out.write(byteArrayOf(0x00, 0x00))
        out.write(byteArrayOf(0x00, 0x00))

        val packet = out.toByteArray()
        val checksum = DnsPacketParser.computeIpChecksum(packet, 0, 20)
        packet[10] = (checksum shr 8).toByte()
        packet[11] = checksum.toByte()
        return packet
    }
}
