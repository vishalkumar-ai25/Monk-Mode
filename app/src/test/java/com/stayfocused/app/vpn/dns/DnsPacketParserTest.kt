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

    @Test
    fun testSkipWireNameUncompressedAndPointer() {
        // Uncompressed www.google.com: \x03www\x06google\x03com\x00 (16 bytes)
        val uncompressed = byteArrayOf(
            3, 'w'.code.toByte(), 'w'.code.toByte(), 'w'.code.toByte(),
            6, 'g'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte(), 'g'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(),
            3, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            0
        )
        val bytesConsumed = DnsPacketParser.skipWireName(uncompressed, 0)
        assertEquals(16, bytesConsumed)

        // Compression pointer 0xC0 0x0C (2 bytes)
        val pointer = byteArrayOf(0xC0.toByte(), 0x0C)
        val pointerConsumed = DnsPacketParser.skipWireName(pointer, 0)
        assertEquals(2, pointerConsumed)
    }

    @Test
    fun testExtractMinTtlSingleAndMultipleAnswers() {
        val out = ByteArrayOutputStream()
        // Header: ID=1, QR=1 (response), QDCOUNT=1, ANCOUNT=2, NSCOUNT=0, ARCOUNT=0
        out.write(byteArrayOf(0x12, 0x34, 0x81.toByte(), 0x80.toByte(), 0, 1, 0, 2, 0, 0, 0, 0))
        // Question: example.com, QTYPE=1, QCLASS=1
        out.write(byteArrayOf(7, 'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(), 3, 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(), 0))
        out.write(byteArrayOf(0, 1, 0, 1))

        // Answer 1: pointer 0xC0 0x0C, TYPE=A (1), CLASS=IN (1), TTL=120, RDLENGTH=4, RDATA=1.2.3.4
        out.write(byteArrayOf(0xC0.toByte(), 0x0C, 0, 1, 0, 1, 0, 0, 0, 120, 0, 4, 1, 2, 3, 4))

        // Answer 2: pointer 0xC0 0x0C, TYPE=A (1), CLASS=IN (1), TTL=45, RDLENGTH=4, RDATA=5.6.7.8
        out.write(byteArrayOf(0xC0.toByte(), 0x0C, 0, 1, 0, 1, 0, 0, 0, 45, 0, 4, 5, 6, 7, 8))

        val responsePayload = out.toByteArray()
        val minTtl = DnsPacketParser.extractMinTtl(responsePayload)
        assertEquals(45L, minTtl)
    }

    @Test
    fun testExtractMinTtlIgnoresOptAdditionalRecord() {
        val out = ByteArrayOutputStream()
        // Header: QDCOUNT=1, ANCOUNT=1, NSCOUNT=0, ARCOUNT=1
        out.write(byteArrayOf(0x56, 0x78, 0x81.toByte(), 0x80.toByte(), 0, 1, 0, 1, 0, 0, 0, 1))
        // Question
        out.write(byteArrayOf(4, 't'.code.toByte(), 'e'.code.toByte(), 's'.code.toByte(), 't'.code.toByte(), 0, 0, 1, 0, 1))
        // Answer: TTL = 300
        out.write(byteArrayOf(0xC0.toByte(), 0x0C, 0, 1, 0, 1, 0, 0, 1, 0x2C, 0, 4, 1, 1, 1, 1))
        // Additional (OPT record with pseudo-TTL = 0x80000000 / large value)
        out.write(byteArrayOf(0, 0, 41, 0x10, 0, 0x80.toByte(), 0, 0, 0, 0, 0))

        val responsePayload = out.toByteArray()
        val minTtl = DnsPacketParser.extractMinTtl(responsePayload)
        assertEquals("Must parse strictly ANCOUNT records and ignore OPT record", 300L, minTtl)
    }

    @Test
    fun testExtractMinTtlNullWhenNoAnswersOrTruncated() {
        // ANCOUNT = 0
        val noAnswers = byteArrayOf(0, 1, 0x81.toByte(), 0x83.toByte(), 0, 1, 0, 0, 0, 0, 0, 0)
        org.junit.Assert.assertNull(DnsPacketParser.extractMinTtl(noAnswers))

        // Truncated payload (< 12 bytes)
        org.junit.Assert.assertNull(DnsPacketParser.extractMinTtl(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun testParseIpPacketRejectsMalformedIhlAndLengths() {
        val validPayload = buildSampleDnsQueryPayload("google.com")
        val validPacket = buildSampleIpv4UdpPacket(dnsPayload = validPayload)

        // 1. IHL < 20 (e.g. IHL = 4 * 4 = 16)
        val badIhlLow = validPacket.copyOf()
        badIhlLow[0] = 0x44.toByte() // version 4, IHL 4 (16 bytes)
        org.junit.Assert.assertNull("IHL < 20 must be rejected", DnsPacketParser.parseIpPacket(badIhlLow))

        // 2. IHL > packet.size
        val badIhlHigh = validPacket.copyOf()
        badIhlHigh[0] = 0x4F.toByte() // version 4, IHL 15 (60 bytes on short packet)
        val shortPacket = badIhlHigh.copyOf(30)
        org.junit.Assert.assertNull("IHL > packet.size must be rejected", DnsPacketParser.parseIpPacket(shortPacket))

        // 3. totalLength < ihl + 8
        val badTotalLen = validPacket.copyOf()
        badTotalLen[2] = 0
        badTotalLen[3] = 24 // totalLength 24 < 20 + 8
        org.junit.Assert.assertNull("totalLength < ihl + UDP_HEADER_LEN must be rejected", DnsPacketParser.parseIpPacket(badTotalLen))

        // 4. UDP length < 8
        val badUdpLen = validPacket.copyOf()
        badUdpLen[24] = 0
        badUdpLen[25] = 4 // UDP length 4 < 8
        org.junit.Assert.assertNull("udpLength < 8 must be rejected", DnsPacketParser.parseIpPacket(badUdpLen))

        // 5. Short packet (< 28 bytes)
        org.junit.Assert.assertNull(DnsPacketParser.parseIpPacket(ByteArray(20)))
    }

    @Test
    fun testParseQNamePointerBoundaryAndLoopRejection() {
        // Forward pointer: byte 12 points forward to byte 25
        val forwardPointer = ByteArray(30)
        forwardPointer[0] = 0x12; forwardPointer[1] = 0x34
        forwardPointer[4] = 0; forwardPointer[5] = 1 // QDCOUNT = 1
        forwardPointer[12] = 0xC0.toByte(); forwardPointer[13] = 25
        org.junit.Assert.assertNull("Forward pointer must be rejected", DnsPacketParser.parseDnsQuery(forwardPointer))

        // Pointer into DNS header (< 12)
        val headerPointer = ByteArray(30)
        headerPointer[0] = 0x12; headerPointer[1] = 0x34
        headerPointer[4] = 0; headerPointer[5] = 1
        headerPointer[12] = 0xC0.toByte(); headerPointer[13] = 6 // points to byte 6
        org.junit.Assert.assertNull("Pointer into header must be rejected", DnsPacketParser.parseDnsQuery(headerPointer))

        // Circular pointer loop (offset 14 points to offset 14)
        val loopPayload = ByteArray(30)
        loopPayload[0] = 0x12; loopPayload[1] = 0x34
        loopPayload[4] = 0; loopPayload[5] = 1
        loopPayload[12] = 1; loopPayload[13] = 'a'.code.toByte()
        loopPayload[14] = 0xC0.toByte(); loopPayload[15] = 14
        org.junit.Assert.assertNull("Circular loop pointer must be rejected", DnsPacketParser.parseDnsQuery(loopPayload))
    }

    @Test
    fun testParseQNameRejectsUnterminatedDomain() {
        // Buffer ends right after label characters without root null terminator (0x00)
        val unterminated = byteArrayOf(
            0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            3, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte()
        )
        org.junit.Assert.assertNull("Unterminated domain name must be rejected", DnsPacketParser.parseDnsQuery(unterminated))
    }

    @Test
    fun testFuzzRandomByteArraysNeverCrashes() {
        val random = java.util.Random(42)
        for (i in 0 until 500) {
            val size = random.nextInt(1500)
            val randomBytes = ByteArray(size)
            random.nextBytes(randomBytes)

            // Neither parseIpPacket nor parseDnsQuery nor isTcpPort853 should ever throw
            DnsPacketParser.parseIpPacket(randomBytes)
            DnsPacketParser.parseDnsQuery(randomBytes)
            DnsPacketParser.isTcpPort853(randomBytes)
            DnsPacketParser.extractMinTtl(randomBytes)
            DnsPacketParser.skipWireName(randomBytes, 0)
        }
    }
}
