package com.safenet.android.engine.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import com.safenet.android.R
import com.safenet.android.domain.model.FilterAction
import com.safenet.android.engine.dns.DnsFilterEngine
import com.safenet.android.engine.dns.DnsPacket
import com.safenet.android.engine.dns.DnsResolver
import com.safenet.android.ui.MainActivity
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class SafeNetVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private val isRunning = AtomicBoolean(false)
    private var workerThread: Thread? = null

    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    companion object {
        const val ACTION_START_VPN = "com.safenet.android.START_VPN"
        const val ACTION_STOP_VPN = "com.safenet.android.STOP_VPN"
        const val NOTIFICATION_CHANNEL_ID = "safenet_vpn_channel"
        const val NOTIFICATION_ID = 1001

        @Volatile
        var isServiceActive: Boolean = false
            private set

        val filterEngine = DnsFilterEngine()
        var dnsResolver: DnsResolver? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        dnsResolver = DnsResolver(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_VPN
        if (action == ACTION_STOP_VPN) {
            stopVpn()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning.get()) return

        try {
            val builder = Builder()
                .setSession("SafeNet-SecureTunnel")
                .setMtu(1500)
                // Virtual IPv4 interface & DNS
                .addAddress("10.254.1.2", 32)
                .addRoute("10.254.1.1", 32)
                .addDnsServer("10.254.1.1")
                // Virtual IPv6 interface & DNS
                .addAddress("fd00::2", 128)
                .addRoute("fd00::1", 128)
                .addDnsServer("fd00::1")
                .setBlocking(true)

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                stopSelf()
                return
            }

            isRunning.set(true)
            isServiceActive = true

            registerNetworkMonitor()

            workerThread = Thread({ runPacketLoop() }, "SafeNet-PacketProcessor").apply {
                priority = Thread.MAX_PRIORITY
                start()
            }
        } catch (e: Exception) {
            stopVpn()
        }
    }

    private fun runPacketLoop() {
        val pfd = vpnInterface ?: return
        val inputStream = FileInputStream(pfd.fileDescriptor)
        val outputStream = FileOutputStream(pfd.fileDescriptor)
        val packetBuffer = ByteArray(32767)

        while (isRunning.get()) {
            try {
                val length = inputStream.read(packetBuffer)
                if (length <= 0) continue

                processPacket(packetBuffer, length, outputStream)
            } catch (e: Exception) {
                if (!isRunning.get()) break
            }
        }
    }

    private fun processPacket(buffer: ByteArray, length: Int, outputStream: FileOutputStream) {
        if (length < 20) return // Minimum IPv4 header length

        val version = (buffer[0].toInt() shr 4) and 0x0F
        if (version != 4) return // Currently focus on IPv4 packet routing

        val ihl = (buffer[0].toInt() and 0x0F) * 4
        if (length < ihl + 8) return

        val protocol = buffer[9].toInt() and 0xFF
        if (protocol != 17) return // Only UDP packets for DNS

        // Extract UDP Ports
        val srcPort = ((buffer[ihl].toInt() and 0xFF) shl 8) or (buffer[ihl + 1].toInt() and 0xFF)
        val dstPort = ((buffer[ihl + 2].toInt() and 0xFF) shl 8) or (buffer[ihl + 3].toInt() and 0xFF)
        val udpLen = ((buffer[ihl + 4].toInt() and 0xFF) shl 8) or (buffer[ihl + 5].toInt() and 0xFF)

        if (dstPort != 53) return // Only DNS queries

        val dnsOffset = ihl + 8
        val dnsLength = udpLen - 8
        if (dnsLength <= 0 || dnsOffset + dnsLength > length) return

        // Parse DNS query
        val dnsPacket = DnsPacket.parse(buffer, dnsOffset, dnsLength) ?: return
        if (dnsPacket.isResponse) return // We process incoming queries, not responses

        // Evaluate domain through filtering engine
        val evaluation = filterEngine.evaluate(dnsPacket.qName)

        val dnsResponseBytes: ByteArray? = when (evaluation.action) {
            FilterAction.BLOCK_SINKHOLE -> {
                // Return 0.0.0.0 sinkhole
                DnsPacket.buildSyntheticResponse(dnsPacket, "0.0.0.0")
            }
            FilterAction.BLOCK_NXDOMAIN -> {
                // Return NXDOMAIN
                DnsPacket.buildNxDomainResponse(dnsPacket)
            }
            FilterAction.REWRITE_SAFESEARCH -> {
                // Return strict SafeSearch VIP IP
                val rewriteIp = evaluation.rewrittenIp ?: "216.239.38.120"
                DnsPacket.buildSyntheticResponse(dnsPacket, rewriteIp)
            }
            FilterAction.ALLOW -> {
                // Query trusted upstream resolver via protected socket
                val resolver = dnsResolver
                if (resolver != null) {
                    val queryBytes = ByteArray(dnsLength)
                    System.arraycopy(buffer, dnsOffset, queryBytes, 0, dnsLength)
                    resolver.forwardQuery(queryBytes)
                } else null
            }
        }

        if (dnsResponseBytes != null) {
            writeUdpResponse(buffer, ihl, srcPort, dstPort, dnsResponseBytes, outputStream)
        }
    }

    private fun writeUdpResponse(
        reqPacket: ByteArray,
        ihl: Int,
        clientPort: Int,
        serverPort: Int,
        dnsPayload: ByteArray,
        outputStream: FileOutputStream
    ) {
        val respUdpLen = 8 + dnsPayload.size
        val respTotalLen = ihl + respUdpLen
        val response = ByteArray(respTotalLen)

        // Copy original IP Header
        System.arraycopy(reqPacket, 0, response, 0, ihl)

        // Update Total Length in IP Header
        response[2] = ((respTotalLen shr 8) and 0xFF).toByte()
        response[3] = (respTotalLen and 0xFF).toByte()

        // Swap Source & Destination IPs
        // Client IP: bytes 12..15, Server IP: bytes 16..19
        System.arraycopy(reqPacket, 16, response, 12, 4) // Source IP = original Dest IP
        System.arraycopy(reqPacket, 12, response, 16, 4) // Dest IP = original Source IP

        // Recalculate IPv4 Header Checksum
        response[10] = 0
        response[11] = 0
        val ipChecksum = computeIpChecksum(response, 0, ihl)
        response[10] = ((ipChecksum shr 8) and 0xFF).toByte()
        response[11] = (ipChecksum and 0xFF).toByte()

        // Build UDP Header
        // Source Port = original Dest Port (53)
        response[ihl] = ((serverPort shr 8) and 0xFF).toByte()
        response[ihl + 1] = (serverPort and 0xFF).toByte()
        // Destination Port = original Client Port
        response[ihl + 2] = ((clientPort shr 8) and 0xFF).toByte()
        response[ihl + 3] = (clientPort and 0xFF).toByte()
        // UDP Length
        response[ihl + 4] = ((respUdpLen shr 8) and 0xFF).toByte()
        response[ihl + 5] = (respUdpLen and 0xFF).toByte()
        // UDP Checksum = 0 (UDP checksum is optional in IPv4)
        response[ihl + 6] = 0
        response[ihl + 7] = 0

        // Copy DNS Payload
        System.arraycopy(dnsPayload, 0, response, ihl + 8, dnsPayload.size)

        // Write directly back to TUN virtual interface
        synchronized(outputStream) {
            outputStream.write(response)
            outputStream.flush()
        }
    }

    private fun computeIpChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        while (i < offset + length - 1) {
            val word = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        if (i < offset + length) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }
        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }
        return sum.inv() and 0xFFFF
    }

    private fun registerNetworkMonitor() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Update underlying networks so VPN transparently re-routes over new physical interface
                setUnderlyingNetworks(arrayOf(network))
            }

            override fun onLost(network: Network) {
                setUnderlyingNetworks(null)
            }
        }
        networkCallback?.let { connectivityManager.registerNetworkCallback(request, it) }
    }

    private fun stopVpn() {
        isRunning.set(false)
        isServiceActive = false

        try {
            networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        } catch (e: Exception) {}
        networkCallback = null

        try {
            vpnInterface?.close()
        } catch (e: Exception) {}
        vpnInterface = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.vpn_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.vpn_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        return builder
            .setContentTitle(getString(R.string.vpn_running_title))
            .setContentText(getString(R.string.vpn_running_desc))
            .setSmallIcon(R.drawable.ic_safenet_shield)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
