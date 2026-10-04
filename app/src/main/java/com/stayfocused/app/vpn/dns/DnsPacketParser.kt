package com.stayfocused.app.vpn.dns

import java.io.ByteArrayOutputStream

data class DnsQuery(
    val transactionId: Int,
    val qname: String,
    val qtype: Int,
    val qclass: Int
)

data class ParsedIpPacket(
    val sourceIp: String,
    val destIp: String,
    val sourcePort: Int,
    val destPort: Int,
    val query: DnsQuery?,
    val dnsPayload: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ParsedIpPacket

        if (sourceIp != other.sourceIp) return false
        if (destIp != other.destIp) return false
        if (sourcePort != other.sourcePort) return false
        if (destPort != other.destPort) return false
        if (query != other.query) return false
        if (!dnsPayload.contentEquals(other.dnsPayload)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sourceIp.hashCode()
        result = 31 * result + destIp.hashCode()
        result = 31 * result + sourcePort
        result = 31 * result + destPort
        result = 31 * result + (query?.hashCode() ?: 0)
        result = 31 * result + dnsPayload.contentHashCode()
        return result
    }
}

/**
 * High-performance, pure Kotlin RFC 1035 DNS, RFC 768 UDP, and RFC 791 IPv4 packet parser and serializer.
 * Zero Android framework dependencies for 100% JVM testability.
 */
object DnsPacketParser {

    private const val IPV4_HEADER_MIN_LEN = 20
    private const val UDP_HEADER_LEN = 8
    private const val DNS_HEADER_LEN = 12

    /**
     * Checks if an IPv4 packet is TCP destined for port 853 (DNS-over-TLS / DoT).
     */
    fun isTcpPort853(packet: ByteArray): Boolean {
        if (packet.size < IPV4_HEADER_MIN_LEN + 4) return false

        val version = (packet[0].toInt() and 0xFF) shr 4
        if (version != 4) return false

        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (ihl < IPV4_HEADER_MIN_LEN || ihl > packet.size || packet.size < ihl + 4) return false

        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 6) return false // TCP only

        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)
        return dstPort == 853
    }

    /**
     * Parses an IPv4/UDP packet and extracts network endpoints and DNS query payload.
     * Enforces strict boundaries against malformed/truncated packets:
     * - packet size >= 28 bytes (20 IP + 8 UDP)
     * - IPv4 version == 4
     * - ihl in 20..packet.size
     * - totalLength in (ihl + 8)..packet.size
     * - protocol == 17 (UDP)
     * - udpLength in 8..(totalLength - ihl)
     * - dnsLen >= 12 (DNS header)
     */
    fun parseIpPacket(packet: ByteArray): ParsedIpPacket? {
        if (packet.size < IPV4_HEADER_MIN_LEN + UDP_HEADER_LEN) return null

        val version = (packet[0].toInt() and 0xFF) shr 4
        if (version != 4) return null

        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (ihl < IPV4_HEADER_MIN_LEN || ihl > packet.size || packet.size < ihl + UDP_HEADER_LEN) return null

        val totalLength = ((packet[2].toInt() and 0xFF) shl 8) or (packet[3].toInt() and 0xFF)
        if (totalLength < ihl + UDP_HEADER_LEN || totalLength > packet.size) return null

        val protocol = packet[9].toInt() and 0xFF
        if (protocol != 17) return null // UDP only

        val udpLength = ((packet[ihl + 4].toInt() and 0xFF) shl 8) or (packet[ihl + 5].toInt() and 0xFF)
        if (udpLength < UDP_HEADER_LEN || udpLength > totalLength - ihl) return null

        val srcIp = "${packet[12].toUByte()}.${packet[13].toUByte()}.${packet[14].toUByte()}.${packet[15].toUByte()}"
        val dstIp = "${packet[16].toUByte()}.${packet[17].toUByte()}.${packet[18].toUByte()}.${packet[19].toUByte()}"

        val srcPort = ((packet[ihl].toInt() and 0xFF) shl 8) or (packet[ihl + 1].toInt() and 0xFF)
        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)

        val dnsOffset = ihl + UDP_HEADER_LEN
        val dnsLen = udpLength - UDP_HEADER_LEN
        if (dnsLen < DNS_HEADER_LEN || dnsOffset + dnsLen > packet.size) return null

        val dnsPayload = packet.copyOfRange(dnsOffset, dnsOffset + dnsLen)
        val query = parseDnsQuery(dnsPayload)

        return ParsedIpPacket(
            sourceIp = srcIp,
            destIp = dstIp,
            sourcePort = srcPort,
            destPort = dstPort,
            query = query,
            dnsPayload = dnsPayload
        )
    }

    /**
     * Parses a raw DNS payload and extracts the Question domain name (QNAME) and transaction ID.
     */
    fun parseDnsQuery(dnsPayload: ByteArray): DnsQuery? {
        if (dnsPayload.size < DNS_HEADER_LEN) return null

        val txId = ((dnsPayload[0].toInt() and 0xFF) shl 8) or (dnsPayload[1].toInt() and 0xFF)
        val qdCount = ((dnsPayload[4].toInt() and 0xFF) shl 8) or (dnsPayload[5].toInt() and 0xFF)
        if (qdCount < 1) return null

        val parsed = parseQName(dnsPayload, DNS_HEADER_LEN) ?: return null
        val qname = parsed.first
        val offset = DNS_HEADER_LEN + parsed.second

        if (offset + 4 > dnsPayload.size) return null
        val qtype = ((dnsPayload[offset].toInt() and 0xFF) shl 8) or (dnsPayload[offset + 1].toInt() and 0xFF)
        val qclass = ((dnsPayload[offset + 2].toInt() and 0xFF) shl 8) or (dnsPayload[offset + 3].toInt() and 0xFF)

        return DnsQuery(txId, qname, qtype, qclass)
    }

    /**
     * Synthesizes an IPv4/UDP packet containing an RFC 1035 NXDOMAIN (RCODE 3) response.
     */
    fun createNxDomainResponse(rawQueryPacket: ByteArray): ByteArray? {
        val parsed = parseIpPacket(rawQueryPacket) ?: return null
        val dnsQuery = parsed.query ?: return null

        val questionBytes = extractQuestionSection(parsed.dnsPayload) ?: return null

        // Synthesize DNS NXDOMAIN payload
        val dnsOut = ByteArrayOutputStream()
        dnsOut.write(byteArrayOf((dnsQuery.transactionId shr 8).toByte(), dnsQuery.transactionId.toByte()))
        dnsOut.write(byteArrayOf(0x81.toByte(), 0x83.toByte())) // QR=1, RD=1, RA=1, RCODE=3 (NXDOMAIN)
        dnsOut.write(byteArrayOf(0x00, 0x01)) // QDCOUNT = 1
        dnsOut.write(byteArrayOf(0x00, 0x00)) // ANCOUNT = 0
        dnsOut.write(byteArrayOf(0x00, 0x00)) // NSCOUNT = 0
        dnsOut.write(byteArrayOf(0x00, 0x00)) // ARCOUNT = 0
        dnsOut.write(questionBytes)

        val dnsResponse = dnsOut.toByteArray()
        return wrapInIpUdp(
            dnsPayload = dnsResponse,
            srcIp = parseIpStringToBytes(parsed.destIp),
            dstIp = parseIpStringToBytes(parsed.sourceIp),
            srcPort = parsed.destPort,
            dstPort = parsed.sourcePort
        )
    }

    /**
     * Synthesizes an IPv4/UDP packet containing an RFC 1035 A record response pointing to sinkhole IP (e.g. 0.0.0.0).
     */
    fun createSinkholeResponse(
        rawQueryPacket: ByteArray,
        sinkholeIp: ByteArray = byteArrayOf(0, 0, 0, 0)
    ): ByteArray? {
        val parsed = parseIpPacket(rawQueryPacket) ?: return null
        val dnsQuery = parsed.query ?: return null

        val questionBytes = extractQuestionSection(parsed.dnsPayload) ?: return null

        val dnsOut = ByteArrayOutputStream()
        dnsOut.write(byteArrayOf((dnsQuery.transactionId shr 8).toByte(), dnsQuery.transactionId.toByte()))
        dnsOut.write(byteArrayOf(0x81.toByte(), 0x80.toByte())) // QR=1, RD=1, RA=1, RCODE=0 (NoError)
        dnsOut.write(byteArrayOf(0x00, 0x01)) // QDCOUNT = 1
        dnsOut.write(byteArrayOf(0x00, 0x01)) // ANCOUNT = 1
        dnsOut.write(byteArrayOf(0x00, 0x00)) // NSCOUNT = 0
        dnsOut.write(byteArrayOf(0x00, 0x00)) // ARCOUNT = 0
        dnsOut.write(questionBytes)

        // Answer Section: A Record
        dnsOut.write(byteArrayOf(0xC0.toByte(), 0x0C)) // Pointer to QNAME at byte 12
        dnsOut.write(byteArrayOf(0x00, 0x01)) // TYPE = A
        dnsOut.write(byteArrayOf(0x00, 0x01)) // CLASS = IN
        dnsOut.write(byteArrayOf(0x00, 0x00, 0x00, 0x3C)) // TTL = 60s
        dnsOut.write(byteArrayOf(0x00, 0x04)) // RDLENGTH = 4
        dnsOut.write(sinkholeIp)

        val dnsResponse = dnsOut.toByteArray()
        return wrapInIpUdp(
            dnsPayload = dnsResponse,
            srcIp = parseIpStringToBytes(parsed.destIp),
            dstIp = parseIpStringToBytes(parsed.sourceIp),
            srcPort = parsed.destPort,
            dstPort = parsed.sourcePort
        )
    }

    /**
     * Wraps a raw DNS response payload in IPv4 and UDP headers directed back to the querying client.
     */
    fun wrapDnsResponse(
        dnsResponsePayload: ByteArray,
        srcIp: String,
        dstIp: String,
        srcPort: Int,
        dstPort: Int
    ): ByteArray {
        return wrapInIpUdp(
            dnsPayload = dnsResponsePayload,
            srcIp = parseIpStringToBytes(srcIp),
            dstIp = parseIpStringToBytes(dstIp),
            srcPort = srcPort,
            dstPort = dstPort
        )
    }

    /**
     * Skips a wire-format domain name starting at [startOffset] in [buffer].
     * Returns the exact number of bytes consumed in the wire stream, or null if malformed.
     * Note: If compression pointer (0xC0) is encountered, it consumes 2 bytes in the wire stream
     * regardless of how many labels the pointer references.
     */
    fun skipWireName(buffer: ByteArray, startOffset: Int): Int? {
        var offset = startOffset
        var bytesConsumed = 0
        while (offset < buffer.size) {
            val len = buffer[offset].toInt() and 0xFF
            if (len == 0) {
                // Zero-length root label terminating the domain name
                bytesConsumed++
                return bytesConsumed
            }
            if ((len and 0xC0) == 0xC0) {
                // Compression pointer is 2 bytes on the wire
                if (offset + 1 >= buffer.size) return null
                bytesConsumed += 2
                return bytesConsumed
            }
            if ((len and 0xC0) != 0) {
                // RFC 1035 reserved label type
                return null
            }
            if (offset + 1 + len > buffer.size) return null
            bytesConsumed += 1 + len
            offset += 1 + len
        }
        return null
    }

    /**
     * Extracts the minimum TTL (in seconds) across all Resource Records in the Answer section
     * of a DNS response payload. Terminates strictly after ANCOUNT records (never scanning
     * Authority or Additional sections to avoid EDNS0 OPT pseudo-record pollution).
     * Returns null if no answer records are present, if minTtl <= 0, or if payload is malformed.
     */
    fun extractMinTtl(dnsPayload: ByteArray): Long? {
        if (dnsPayload.size < DNS_HEADER_LEN) return null

        val qdCount = ((dnsPayload[4].toInt() and 0xFF) shl 8) or (dnsPayload[5].toInt() and 0xFF)
        val anCount = ((dnsPayload[6].toInt() and 0xFF) shl 8) or (dnsPayload[7].toInt() and 0xFF)
        if (anCount <= 0) return null

        var offset = DNS_HEADER_LEN

        // 1. Skip Question section (qdCount questions)
        for (i in 0 until qdCount) {
            val consumed = skipWireName(dnsPayload, offset) ?: return null
            offset += consumed + 4 // QNAME + QTYPE (2) + QCLASS (2)
            if (offset > dnsPayload.size) return null
        }

        // 2. Iterate strictly ANCOUNT Answer resource records
        var minTtl: Long? = null
        for (i in 0 until anCount) {
            if (offset >= dnsPayload.size) break
            val consumed = skipWireName(dnsPayload, offset) ?: return null
            offset += consumed

            // Must have at least 10 bytes: TYPE (2) + CLASS (2) + TTL (4) + RDLENGTH (2)
            if (offset + 10 > dnsPayload.size) break

            val ttl = ((dnsPayload[offset + 4].toLong() and 0xFF) shl 24) or
                    ((dnsPayload[offset + 5].toLong() and 0xFF) shl 16) or
                    ((dnsPayload[offset + 6].toLong() and 0xFF) shl 8) or
                    (dnsPayload[offset + 7].toLong() and 0xFF)

            val rdLength = ((dnsPayload[offset + 8].toInt() and 0xFF) shl 8) or
                    (dnsPayload[offset + 9].toInt() and 0xFF)

            offset += 10 + rdLength
            if (offset > dnsPayload.size) break

            minTtl = if (minTtl == null) ttl else minOf(minTtl, ttl)
        }

        return minTtl
    }

    private fun extractQuestionSection(dnsPayload: ByteArray): ByteArray? {
        if (dnsPayload.size < DNS_HEADER_LEN) return null
        val parsed = parseQName(dnsPayload, DNS_HEADER_LEN) ?: return null
        val questionLength = parsed.second + 4 // QNAME length + QTYPE (2) + QCLASS (2)
        if (DNS_HEADER_LEN + questionLength > dnsPayload.size) return null
        return dnsPayload.copyOfRange(DNS_HEADER_LEN, DNS_HEADER_LEN + questionLength)
    }

    private fun wrapInIpUdp(
        dnsPayload: ByteArray,
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int
    ): ByteArray {
        val totalLength = IPV4_HEADER_MIN_LEN + UDP_HEADER_LEN + dnsPayload.size
        val out = ByteArrayOutputStream(totalLength)

        // 1. IPv4 Header (20 bytes)
        out.write(byteArrayOf(0x45, 0x00))
        out.write(byteArrayOf((totalLength shr 8).toByte(), totalLength.toByte()))
        out.write(byteArrayOf(0x00, 0x00)) // Identification
        out.write(byteArrayOf(0x40, 0x00)) // Don't fragment
        out.write(byteArrayOf(64, 17)) // TTL=64, UDP=17
        out.write(byteArrayOf(0x00, 0x00)) // Checksum placeholder
        out.write(srcIp)
        out.write(dstIp)

        // 2. UDP Header (8 bytes)
        val udpLength = UDP_HEADER_LEN + dnsPayload.size
        out.write(byteArrayOf((srcPort shr 8).toByte(), srcPort.toByte()))
        out.write(byteArrayOf((dstPort shr 8).toByte(), dstPort.toByte()))
        out.write(byteArrayOf((udpLength shr 8).toByte(), udpLength.toByte()))
        out.write(byteArrayOf(0x00, 0x00)) // Optional UDP checksum

        // 3. DNS Payload
        out.write(dnsPayload)

        val packet = out.toByteArray()

        // Calculate & Inject IPv4 Header Checksum
        val ipChecksum = computeIpChecksum(packet, 0, IPV4_HEADER_MIN_LEN)
        packet[10] = (ipChecksum shr 8).toByte()
        packet[11] = ipChecksum.toByte()

        return packet
    }

    private fun parseQName(buffer: ByteArray, startOffset: Int): Pair<String, Int>? {
        val labels = mutableListOf<String>()
        var offset = startOffset
        var jumped = false
        var bytesConsumed = 0
        var jumpCount = 0
        var terminated = false

        while (offset < buffer.size) {
            val len = buffer[offset].toInt() and 0xFF
            if (len == 0) {
                offset++
                if (!jumped) bytesConsumed++
                terminated = true
                break
            }

            if ((len and 0xC0) == 0xC0) {
                // Compression pointer (2 bytes on the wire)
                if (offset + 1 >= buffer.size) return null
                if (!jumped) bytesConsumed += 2
                val pointerOffset = ((len and 0x3F) shl 8) or (buffer[offset + 1].toInt() and 0xFF)
                // RFC 1035: Pointers must point past the 12-byte header and strictly backward
                if (pointerOffset < DNS_HEADER_LEN || pointerOffset >= offset) return null
                jumpCount++
                if (jumpCount > 8) return null // Recursion / loop depth exceeded
                offset = pointerOffset
                jumped = true
                continue
            }

            if ((len and 0xC0) != 0) return null // Reserved label types
            if (len > 63) return null // RFC 1035: Max label length is 63 octets

            if (!jumped) bytesConsumed += 1 + len
            offset++
            if (offset + len > buffer.size) return null
            val labelStr = String(buffer, offset, len, Charsets.US_ASCII)
            labels.add(labelStr)
            offset += len
        }

        if (!terminated) return null
        return Pair(labels.joinToString("."), if (jumped) bytesConsumed else (offset - startOffset))
    }

    fun computeIpChecksum(buffer: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length) {
            val word = if (i + 1 < offset + length) {
                ((buffer[i].toInt() and 0xFF) shl 8) or (buffer[i + 1].toInt() and 0xFF)
            } else {
                (buffer[i].toInt() and 0xFF) shl 8
            }
            sum += word
            i += 2
        }
        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return (sum.inv()) and 0xFFFF
    }

    private fun parseIpStringToBytes(ip: String): ByteArray {
        val parts = ip.split(".")
        return byteArrayOf(
            parts[0].toInt().toByte(),
            parts[1].toInt().toByte(),
            parts[2].toInt().toByte(),
            parts[3].toInt().toByte()
        )
    }
}
