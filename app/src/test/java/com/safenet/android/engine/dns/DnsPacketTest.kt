package com.safenet.android.engine.dns

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

class DnsPacketTest {

    private fun createSampleDnsQuery(domain: String, txId: Short = 0x1234): ByteArray {
        val labels = domain.split('.')
        var qNameLen = 1 // terminating 0
        for (l in labels) {
            qNameLen += 1 + l.length
        }
        val buffer = ByteBuffer.allocate(12 + qNameLen + 4)
        buffer.putShort(txId) // ID
        buffer.putShort(0x0100.toShort()) // Standard query, RD=1
        buffer.putShort(1.toShort()) // QDCOUNT = 1
        buffer.putShort(0.toShort()) // ANCOUNT = 0
        buffer.putShort(0.toShort()) // NSCOUNT = 0
        buffer.putShort(0.toShort()) // ARCOUNT = 0

        for (l in labels) {
            buffer.put(l.length.toByte())
            buffer.put(l.toByteArray(Charsets.US_ASCII))
        }
        buffer.put(0.toByte()) // end of QNAME
        buffer.putShort(DnsPacket.TYPE_A.toShort()) // QTYPE
        buffer.putShort(DnsPacket.CLASS_IN.toShort()) // QCLASS

        return buffer.array()
    }

    @Test
    fun testParseValidDnsQuery() {
        val queryBytes = createSampleDnsQuery("example.com", 0x4321.toShort())
        val packet = DnsPacket.parse(queryBytes)

        assertNotNull(packet)
        assertEquals(0x4321, packet!!.transactionId)
        assertFalse(packet.isResponse)
        assertTrue(packet.recursionDesired)
        assertEquals("example.com", packet.qName)
        assertEquals(DnsPacket.TYPE_A, packet.qType)
        assertEquals(DnsPacket.CLASS_IN, packet.qClass)
    }

    @Test
    fun testBuildSyntheticSinkholeResponse() {
        val queryBytes = createSampleDnsQuery("blocked-site.com", 0xABCD.toShort())
        val queryPacket = DnsPacket.parse(queryBytes)!!

        val responseBytes = DnsPacket.buildSyntheticResponse(queryPacket, "0.0.0.0")
        assertNotNull(responseBytes)

        val buffer = ByteBuffer.wrap(responseBytes)
        val respId = buffer.short.toInt() and 0xFFFF
        val respFlags = buffer.short.toInt() and 0xFFFF
        val qdCount = buffer.short.toInt() and 0xFFFF
        val anCount = buffer.short.toInt() and 0xFFFF

        assertEquals(0xABCD, respId)
        assertTrue((respFlags and 0x8000) != 0) // QR = 1 (Response)
        assertEquals(1, qdCount)
        assertEquals(1, anCount)
    }

    @Test
    fun testBuildNxDomainResponse() {
        val queryBytes = createSampleDnsQuery("nonexistent.com", 0x7777.toShort())
        val queryPacket = DnsPacket.parse(queryBytes)!!

        val responseBytes = DnsPacket.buildNxDomainResponse(queryPacket)
        assertNotNull(responseBytes)

        val buffer = ByteBuffer.wrap(responseBytes)
        val respId = buffer.short.toInt() and 0xFFFF
        val respFlags = buffer.short.toInt() and 0xFFFF
        val qdCount = buffer.short.toInt() and 0xFFFF
        val anCount = buffer.short.toInt() and 0xFFFF

        assertEquals(0x7777, respId)
        assertEquals(3, respFlags and 0x000F) // RCODE = 3 (NXDOMAIN)
        assertEquals(1, qdCount)
        assertEquals(0, anCount)
    }

    @Test
    fun testMalformedPacketSafety() {
        val truncatedHeader = ByteArray(8)
        assertNull(DnsPacket.parse(truncatedHeader))

        val empty = ByteArray(0)
        assertNull(DnsPacket.parse(empty))
    }
}
