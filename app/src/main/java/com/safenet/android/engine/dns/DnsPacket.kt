package com.safenet.android.engine.dns

import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * Robust RFC 1035 binary DNS message parser and synthesizer.
 */
class DnsPacket(
    val transactionId: Int,
    val isResponse: Boolean,
    val opcode: Int,
    val recursionDesired: Boolean,
    val qName: String,
    val qType: Int,
    val qClass: Int,
    val rawPayload: ByteArray
) {

    companion object {
        const val TYPE_A = 1
        const val TYPE_AAAA = 28
        const val TYPE_CNAME = 5
        const val CLASS_IN = 1

        const val RCODE_NO_ERROR = 0
        const val RCODE_FORMAT_ERROR = 1
        const val RCODE_SERVER_FAILURE = 2
        const val RCODE_NAME_ERROR = 3 // NXDOMAIN
        const val RCODE_REFUSED = 5

        /**
         * Safely parses raw DNS packet bytes from UDP payload.
         * Returns null if packet is malformed or truncated.
         */
        fun parse(data: ByteArray, offset: Int = 0, length: Int = data.size): DnsPacket? {
            if (length < 12) return null // Minimum DNS header length

            try {
                val buffer = ByteBuffer.wrap(data, offset, length)
                val id = buffer.short.toInt() and 0xFFFF
                val flags = buffer.short.toInt() and 0xFFFF

                val isResponse = (flags and 0x8000) != 0
                val opcode = (flags shr 11) and 0x0F
                val recursionDesired = (flags and 0x0100) != 0

                val qdCount = buffer.short.toInt() and 0xFFFF
                if (qdCount < 1) return null // Must have at least 1 question

                val anCount = buffer.short.toInt() and 0xFFFF
                val nsCount = buffer.short.toInt() and 0xFFFF
                val arCount = buffer.short.toInt() and 0xFFFF

                // Parse QNAME
                val qNameBuilder = StringBuilder()
                var reading = true
                var safetyLoop = 0

                while (reading && buffer.hasRemaining()) {
                    if (++safetyLoop > 100) return null // Avoid infinite loop on malformed compression
                    val labelLen = buffer.get().toInt() and 0xFF
                    if (labelLen == 0) {
                        reading = false
                    } else if ((labelLen and 0xC0) == 0xC0) {
                        // Compressed pointer in question (unusual but valid)
                        if (!buffer.hasRemaining()) return null
                        buffer.get() // consume second byte of pointer
                        reading = false
                    } else {
                        if (buffer.remaining() < labelLen) return null
                        val labelBytes = ByteArray(labelLen)
                        buffer.get(labelBytes)
                        if (qNameBuilder.isNotEmpty()) qNameBuilder.append('.')
                        qNameBuilder.append(String(labelBytes, Charsets.US_ASCII))
                    }
                }

                if (buffer.remaining() < 4) return null
                val qType = buffer.short.toInt() and 0xFFFF
                val qClass = buffer.short.toInt() and 0xFFFF

                val raw = ByteArray(length)
                System.arraycopy(data, offset, raw, 0, length)

                return DnsPacket(
                    transactionId = id,
                    isResponse = isResponse,
                    opcode = opcode,
                    recursionDesired = recursionDesired,
                    qName = qNameBuilder.toString(),
                    qType = qType,
                    qClass = qClass,
                    rawPayload = raw
                )
            } catch (e: Exception) {
                return null
            }
        }

        /**
         * Synthesizes an authoritative RFC 1035 DNS response with a sinkhole or rewritten IP.
         */
        fun buildSyntheticResponse(
            query: DnsPacket,
            ipAddress: String,
            ttlSeconds: Int = 300
        ): ByteArray {
            val inetAddr = InetAddress.getByName(ipAddress)
            val ipBytes = inetAddr.address
            val isIpv6 = ipBytes.size == 16

            // Header (12) + Question + Answer (16 for A or 28 for AAAA)
            val questionLen = query.rawPayload.size - 12
            val answerLen = 2 + 2 + 2 + 4 + 2 + ipBytes.size // Pointer(2) + Type(2) + Class(2) + TTL(4) + Len(2) + IP
            val responseBytes = ByteArray(12 + questionLen + answerLen)
            val buffer = ByteBuffer.wrap(responseBytes)

            // Header: ID
            buffer.putShort(query.transactionId.toShort())

            // Flags: Response(0x8000) + Authoritative(0x0400) + RecursionAvailable(0x0080) + RecursionDesired(if set)
            var flags = 0x8480
            if (query.recursionDesired) flags = flags or 0x0100
            buffer.putShort(flags.toShort())

            // QDCOUNT = 1, ANCOUNT = 1, NSCOUNT = 0, ARCOUNT = 0
            buffer.putShort(1.toShort())
            buffer.putShort(1.toShort())
            buffer.putShort(0.toShort())
            buffer.putShort(0.toShort())

            // Question: copy from query
            buffer.put(query.rawPayload, 12, questionLen)

            // Answer Section
            // Name pointer to question at offset 12 (0xC00C)
            buffer.putShort(0xC00C.toShort())
            buffer.putShort((if (isIpv6) TYPE_AAAA else TYPE_A).toShort())
            buffer.putShort(CLASS_IN.toShort())
            buffer.putInt(ttlSeconds)
            buffer.putShort(ipBytes.size.toShort())
            buffer.put(ipBytes)

            return responseBytes
        }

        /**
         * Synthesizes an NXDOMAIN (RCODE 3) response.
         */
        fun buildNxDomainResponse(query: DnsPacket): ByteArray {
            val questionLen = query.rawPayload.size - 12
            val responseBytes = ByteArray(12 + questionLen)
            val buffer = ByteBuffer.wrap(responseBytes)

            // Header: ID
            buffer.putShort(query.transactionId.toShort())

            // Flags: Response(0x8000) + Authoritative(0x0400) + RecursionAvailable(0x0080) + RCODE 3 (0x0003)
            var flags = 0x8483
            if (query.recursionDesired) flags = flags or 0x0100
            buffer.putShort(flags.toShort())

            // QDCOUNT = 1, ANCOUNT = 0, NSCOUNT = 0, ARCOUNT = 0
            buffer.putShort(1.toShort())
            buffer.putShort(0.toShort())
            buffer.putShort(0.toShort())
            buffer.putShort(0.toShort())

            // Question: copy from query
            buffer.put(query.rawPayload, 12, questionLen)

            return responseBytes
        }
    }
}
