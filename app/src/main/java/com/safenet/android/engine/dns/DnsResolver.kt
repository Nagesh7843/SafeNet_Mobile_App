package com.safenet.android.engine.dns

import android.net.VpnService
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

class DnsResolver(
    private val vpnService: VpnService
) {

    enum class UpstreamProvider(
        val displayName: String,
        val primaryIpv4: String,
        val secondaryIpv4: String,
        val description: String
    ) {
        CLOUDFLARE_FAMILY(
            "Cloudflare 1.1.1.3 (Malware + Adult Block)",
            "1.1.1.3",
            "1.0.0.3",
            "Automated upstream adult content and malware blocking"
        ),
        QUAD9_SECURITY(
            "Quad9 9.9.9.9 (Malware + Phishing Block)",
            "9.9.9.9",
            "149.112.112.112",
            "High-privacy enterprise threat intelligence"
        ),
        CLEANBROWSING_FAMILY(
            "CleanBrowsing Family (Strict Filter)",
            "185.228.168.168",
            "185.228.169.168",
            "Enforces strict safe search, adult and proxy blocking"
        ),
        CUSTOM(
            "Custom DNS",
            "1.1.1.1",
            "8.8.8.8",
            "User-defined upstream resolver"
        )
    }

    @Volatile
    var currentProvider: UpstreamProvider = UpstreamProvider.CLOUDFLARE_FAMILY

    @Volatile
    var customPrimaryIp: String = "1.1.1.3"

    /**
     * Forwards an allowed DNS query to the trusted upstream resolver using a protected DatagramSocket.
     */
    fun forwardQuery(queryBytes: ByteArray): ByteArray? {
        val targetIp = if (currentProvider == UpstreamProvider.CUSTOM) {
            customPrimaryIp
        } else {
            currentProvider.primaryIpv4
        }

        val result = queryIp(targetIp, queryBytes, 3500)
        if (result != null) return result

        // Fallback 1: Secondary IP of chosen provider
        val secondaryResult = queryIp(currentProvider.secondaryIpv4, queryBytes, 2500)
        if (secondaryResult != null) return secondaryResult

        // Fallback 2: Ultra-reliable public resolver
        val universalFallback = if (targetIp != "1.1.1.1") "1.1.1.1" else "8.8.8.8"
        return queryIp(universalFallback, queryBytes, 2500)
    }

    private fun queryIp(ip: String, queryBytes: ByteArray, timeoutMs: Int): ByteArray? {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            vpnService.protect(socket)

            try {
                val cm = vpnService.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                cm?.activeNetwork?.bindSocket(socket)
            } catch (e: Exception) {}

            socket.soTimeout = timeoutMs

            val serverAddr = InetAddress.getByName(ip)
            val sendPacket = DatagramPacket(queryBytes, queryBytes.size, serverAddr, 53)
            socket.send(sendPacket)

            val receiveBuf = ByteArray(1500)
            val receivePacket = DatagramPacket(receiveBuf, receiveBuf.size)
            socket.receive(receivePacket)

            val res = ByteArray(receivePacket.length)
            System.arraycopy(receiveBuf, 0, res, 0, receivePacket.length)
            return res
        } catch (e: Exception) {
            return null
        } finally {
            socket?.close()
        }
    }
}
