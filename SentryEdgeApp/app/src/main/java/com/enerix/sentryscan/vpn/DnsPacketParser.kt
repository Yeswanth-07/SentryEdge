package com.enerix.sentryscan.vpn

import java.nio.ByteBuffer

/**
 * Minimal IPv4/UDP/DNS parser. We don't need a full network stack — we
 * only need to notice "this packet is a DNS query" and pull the domain
 * name out of it, which is enough to log what a device is talking to
 * without ever looking at payload content (no packet body other than
 * the query name is inspected or stored).
 */
object DnsPacketParser {

    data class DnsQuery(val domain: String, val sourcePort: Int, val destPort: Int, val protocol: Int)

    /** Returns the queried domain name if this raw IP packet is a DNS query on port 53, else null. */
    fun extractDnsQuery(packet: ByteArray, length: Int): DnsQuery? {
        if (length < 20) return null
        val buffer = ByteBuffer.wrap(packet, 0, length)

        val versionAndIhl = buffer.get(0).toInt() and 0xFF
        val version = versionAndIhl shr 4
        if (version != 4) return null // IPv6 handling omitted for the hackathon build

        val ihl = (versionAndIhl and 0x0F) * 4
        val protocol = buffer.get(9).toInt() and 0xFF
        if (protocol != 17) return null // UDP only; DNS-over-TCP/DoH is a known limitation, see README

        val udpStart = ihl
        if (length < udpStart + 8) return null
        val srcPort = ((packet[udpStart].toInt() and 0xFF) shl 8) or (packet[udpStart + 1].toInt() and 0xFF)
        val dstPort = ((packet[udpStart + 2].toInt() and 0xFF) shl 8) or (packet[udpStart + 3].toInt() and 0xFF)
        if (dstPort != 53) return null

        val dnsStart = udpStart + 8
        val domain = parseDnsName(packet, dnsStart + 12, length) ?: return null // +12 skips the DNS header
        return DnsQuery(domain, srcPort, dstPort, protocol)
    }

    private fun parseDnsName(packet: ByteArray, start: Int, length: Int): String? {
        val labels = mutableListOf<String>()
        var pos = start
        while (pos < length) {
            val len = packet[pos].toInt() and 0xFF
            if (len == 0) break
            if (pos + 1 + len > length) return null
            labels += String(packet, pos + 1, len, Charsets.US_ASCII)
            pos += 1 + len
        }
        return if (labels.isEmpty()) null else labels.joinToString(".")
    }
}
