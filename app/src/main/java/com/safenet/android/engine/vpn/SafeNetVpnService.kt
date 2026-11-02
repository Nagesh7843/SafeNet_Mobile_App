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
import com.safenet.android.core.state.ProtectionStateManager
import com.safenet.android.domain.model.FilterAction
import com.safenet.android.engine.dns.DnsFilterEngine
import com.safenet.android.engine.dns.DnsPacket
import com.safenet.android.engine.dns.DnsResolver
import com.safenet.android.ui.MainActivity
import java.io.FileInputStream
import java.io.FileOutputStream
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
            android.util.Log.w("SafeNetVpnService", "ACTION_STOP_VPN ignored: SafeNet is running in permanent lockdown mode.")
            return START_STICKY
        }

        ProtectionStateManager.instance.onVpnActivating()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        startVpn()
        return START_STICKY
    }

    override fun onRevoke() {
        ProtectionStateManager.instance.onVpnRevoked()
        stopVpn(isRevocation = true)
        super.onRevoke()
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

            val pfd = builder.establish()
            if (pfd == null || !pfd.fileDescriptor.valid()) {
                ProtectionStateManager.instance.recordInterruption(
                    "Failed to establish VPN interface: ParcelFileDescriptor is null or invalid.",
                    requiresRecovery = true
                )
                stopVpn(isRevocation = false)
                return
            }

            vpnInterface = pfd
            isRunning.set(true)
            isServiceActive = true

            // Register dynamic network monitor
            registerNetworkMonitor()

            // Update centralized state to ACTIVE only after interface is established
            ProtectionStateManager.instance.onVpnOperational(
                upstreamResolver = dnsResolver?.currentProvider?.primaryIpv4 ?: "1.1.1.3",
                ruleCount = filterEngine.trie.ruleCount
            )

            // Start dedicated packet processing thread
            workerThread = Thread({ runPacketLoop() }, "SafeNet-PacketProcessor").apply {
                priority = Thread.MAX_PRIORITY
                start()
            }
        } catch (e: Exception) {
            ProtectionStateManager.instance.recordInterruption(
                "Exception during VPN establishment: ${e.localizedMessage}",
                requiresRecovery = true
            )
            stopVpn(isRevocation = false)
        }
    }

    private fun runPacketLoop() {
        val pfd = vpnInterface ?: return
        val inputStream = FileInputStream(pfd.fileDescriptor)
        val outputStream = FileOutputStream(pfd.fileDescriptor)
        val packetBuffer = ByteArray(32767)
        var iteration = 0L

        while (isRunning.get()) {
            try {
                val length = inputStream.read(packetBuffer)
                if (length <= 0) continue

                processPacket(packetBuffer, length, outputStream)

                // Periodically sync live query metrics to ProtectionStateManager
                if (++iteration % 50 == 0L) {
                    ProtectionStateManager.instance.updateMetrics(
                        totalProcessed = filterEngine.totalQueriesProcessed.get(),
                        totalBlocked = filterEngine.totalQueriesBlocked.get(),
                        ruleCount = filterEngine.trie.ruleCount
                    )
                }
            } catch (e: Exception) {
                if (!isRunning.get()) break
            }
        }
    }

    private fun processPacket(buffer: ByteArray, length: Int, outputStream: FileOutputStream) {
        if (length < 20) return

        val version = (buffer[0].toInt() shr 4) and 0x0F

        if (version == 4) {
            processIpv4Packet(buffer, length, outputStream)
        } else if (version == 6) {
            processIpv6Packet(buffer, length, outputStream)
        }
    }

    private fun processIpv4Packet(buffer: ByteArray, length: Int, outputStream: FileOutputStream) {
        val ihl = (buffer[0].toInt() and 0x0F) * 4
        if (length < ihl + 8) return

        val protocol = buffer[9].toInt() and 0xFF
        if (protocol != 17) return // Only UDP DNS

        val srcPort = ((buffer[ihl].toInt() and 0xFF) shl 8) or (buffer[ihl + 1].toInt() and 0xFF)
        val dstPort = ((buffer[ihl + 2].toInt() and 0xFF) shl 8) or (buffer[ihl + 3].toInt() and 0xFF)
        val udpLen = ((buffer[ihl + 4].toInt() and 0xFF) shl 8) or (buffer[ihl + 5].toInt() and 0xFF)

        if (dstPort != 53) return // Only DNS queries

        val dnsOffset = ihl + 8
        val dnsLength = udpLen - 8
        if (dnsLength <= 0 || dnsOffset + dnsLength > length) return

        val dnsPacket = DnsPacket.parse(buffer, dnsOffset, dnsLength) ?: return
        if (dnsPacket.isResponse) return

        val evaluation = filterEngine.evaluate(dnsPacket.qName, isIpv6 = false)

        val dnsResponseBytes: ByteArray? = when (evaluation.action) {
            FilterAction.BLOCK_SINKHOLE -> DnsPacket.buildSinkholeResponse(dnsPacket)
            FilterAction.BLOCK_NXDOMAIN -> DnsPacket.buildNxDomainResponse(dnsPacket)
            FilterAction.REWRITE_SAFESEARCH -> {
                val rewriteIp = evaluation.rewrittenIp ?: "216.239.38.120"
                DnsPacket.buildSyntheticResponse(dnsPacket, rewriteIp)
            }
            FilterAction.ALLOW -> {
                val resolver = dnsResolver
                if (resolver != null) {
                    val queryBytes = ByteArray(dnsLength)
                    System.arraycopy(buffer, dnsOffset, queryBytes, 0, dnsLength)
                    resolver.forwardQuery(queryBytes)
                } else null
            }
        }

        if (dnsResponseBytes != null) {
            writeIpv4UdpResponse(buffer, ihl, srcPort, dstPort, dnsResponseBytes, outputStream)
        }
    }

    private fun processIpv6Packet(buffer: ByteArray, length: Int, outputStream: FileOutputStream) {
        if (length < 48) return // 40-byte IPv6 header + 8-byte UDP header

        val nextHeader = buffer[6].toInt() and 0xFF
        if (nextHeader != 17) return // Only UDP

        val ihl = 40
        val srcPort = ((buffer[ihl].toInt() and 0xFF) shl 8) or (buffer[ihl + 1].toInt() and 0xFF)
        val dstPort = ((buffer[ihl + 2].toInt() and 0xFF) shl 8) or (buffer[ihl + 3].toInt() and 0xFF)
        val udpLen = ((buffer[ihl + 4].toInt() and 0xFF) shl 8) or (buffer[ihl + 5].toInt() and 0xFF)

        if (dstPort != 53) return

        val dnsOffset = ihl + 8
        val dnsLength = udpLen - 8
        if (dnsLength <= 0 || dnsOffset + dnsLength > length) return

        val dnsPacket = DnsPacket.parse(buffer, dnsOffset, dnsLength) ?: return
        if (dnsPacket.isResponse) return

        val evaluation = filterEngine.evaluate(dnsPacket.qName, isIpv6 = true)

        val dnsResponseBytes: ByteArray? = when (evaluation.action) {
            FilterAction.BLOCK_SINKHOLE -> DnsPacket.buildSinkholeResponse(dnsPacket)
            FilterAction.BLOCK_NXDOMAIN -> DnsPacket.buildNxDomainResponse(dnsPacket)
            FilterAction.REWRITE_SAFESEARCH -> {
                val rewriteIp = evaluation.rewrittenIp ?: "2001:4860:4806::78"
                DnsPacket.buildSyntheticResponse(dnsPacket, rewriteIp)
            }
            FilterAction.ALLOW -> {
                val resolver = dnsResolver
                if (resolver != null) {
                    val queryBytes = ByteArray(dnsLength)
                    System.arraycopy(buffer, dnsOffset, queryBytes, 0, dnsLength)
                    resolver.forwardQuery(queryBytes)
                } else null
            }
        }

        if (dnsResponseBytes != null) {
            writeIpv6UdpResponse(buffer, srcPort, dstPort, dnsResponseBytes, outputStream)
        }
    }

    private fun writeIpv4UdpResponse(
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

        // Copy IP Header
        System.arraycopy(reqPacket, 0, response, 0, ihl)

        // Total Length
        response[2] = ((respTotalLen shr 8) and 0xFF).toByte()
        response[3] = (respTotalLen and 0xFF).toByte()

        // Swap Source & Destination IPs
        System.arraycopy(reqPacket, 16, response, 12, 4)
        System.arraycopy(reqPacket, 12, response, 16, 4)

        // Checksum
        response[10] = 0
        response[11] = 0
        val ipChecksum = computeIpChecksum(response, 0, ihl)
        response[10] = ((ipChecksum shr 8) and 0xFF).toByte()
        response[11] = (ipChecksum and 0xFF).toByte()

        // UDP Header
        response[ihl] = ((serverPort shr 8) and 0xFF).toByte()
        response[ihl + 1] = (serverPort and 0xFF).toByte()
        response[ihl + 2] = ((clientPort shr 8) and 0xFF).toByte()
        response[ihl + 3] = (clientPort and 0xFF).toByte()
        response[ihl + 4] = ((respUdpLen shr 8) and 0xFF).toByte()
        response[ihl + 5] = (respUdpLen and 0xFF).toByte()
        response[ihl + 6] = 0
        response[ihl + 7] = 0

        // DNS Payload
        System.arraycopy(dnsPayload, 0, response, ihl + 8, dnsPayload.size)

        synchronized(outputStream) {
            outputStream.write(response)
            outputStream.flush()
        }
    }

    private fun writeIpv6UdpResponse(
        reqPacket: ByteArray,
        clientPort: Int,
        serverPort: Int,
        dnsPayload: ByteArray,
        outputStream: FileOutputStream
    ) {
        val ihl = 40
        val respUdpLen = 8 + dnsPayload.size
        val respTotalLen = ihl + respUdpLen
        val response = ByteArray(respTotalLen)

        // Copy IPv6 Header
        System.arraycopy(reqPacket, 0, response, 0, ihl)

        // Payload Length in IPv6 header (bytes 4..5)
        response[4] = ((respUdpLen shr 8) and 0xFF).toByte()
        response[5] = (respUdpLen and 0xFF).toByte()

        // Swap Source & Destination IPv6 addresses (bytes 8..23 with 24..39)
        System.arraycopy(reqPacket, 24, response, 8, 16)
        System.arraycopy(reqPacket, 8, response, 24, 16)

        // UDP Header
        response[ihl] = ((serverPort shr 8) and 0xFF).toByte()
        response[ihl + 1] = (serverPort and 0xFF).toByte()
        response[ihl + 2] = ((clientPort shr 8) and 0xFF).toByte()
        response[ihl + 3] = (clientPort and 0xFF).toByte()
        response[ihl + 4] = ((respUdpLen shr 8) and 0xFF).toByte()
        response[ihl + 5] = (respUdpLen and 0xFF).toByte()
        response[ihl + 6] = 0
        response[ihl + 7] = 0

        // DNS Payload
        System.arraycopy(dnsPayload, 0, response, ihl + 8, dnsPayload.size)

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
                setUnderlyingNetworks(arrayOf(network))
                ProtectionStateManager.instance.onNetworkConnectivityChanged(true, "Physical Network Connected")
            }

            override fun onLost(network: Network) {
                setUnderlyingNetworks(null)
                ProtectionStateManager.instance.onNetworkConnectivityChanged(false, "Network Lost")
            }
        }
        networkCallback?.let { connectivityManager.registerNetworkCallback(request, it) }
    }

    private fun stopVpn(isRevocation: Boolean = false) {
        isRunning.set(false)
        isServiceActive = false

        if (!isRevocation) {
            ProtectionStateManager.instance.onVpnStopped()
        }

        try {
            networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        } catch (e: Exception) {}
        networkCallback = null

        try {
            vpnInterface?.close()
        } catch (e: Exception) {}
        vpnInterface = null

        try {
            workerThread?.interrupt()
        } catch (e: Exception) {}
        workerThread = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn(isRevocation = false)
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
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
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
