package com.safenet.android.core.monitor

import android.content.Context
import com.safenet.android.SafeNetApplication
import com.safenet.android.core.state.ProtectionState
import com.safenet.android.core.state.ProtectionStateManager
import com.safenet.android.domain.model.SecurityEvent
import com.safenet.android.engine.vpn.SafeNetVpnService
import kotlinx.coroutines.*

/**
 * Monitors system integrity, authorization status, DNS engine health, and unexpected shutdowns.
 */
class TamperMonitor(
    private val context: Context,
    private val stateManager: ProtectionStateManager = ProtectionStateManager.instance
) {

    private var monitorJob: Job? = null

    fun startMonitoring(scope: CoroutineScope) {
        monitorJob?.cancel()
        monitorJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                runIntegrityCheck()
                delay(15_000) // Check every 15 seconds
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
    }

    /**
     * Executes an immediate comprehensive integrity and health audit.
     */
    fun runIntegrityCheck(): Boolean {
        var isHealthy = true

        // 1. Check Network Connectivity
        val hasNetwork = PermissionMonitor.isNetworkAvailable(context)
        val networkType = PermissionMonitor.getNetworkTypeName(context)
        stateManager.onNetworkConnectivityChanged(hasNetwork, networkType)

        if (!hasNetwork) {
            return false
        }

        // 2. Check VPN Authorization
        val isAuthorized = PermissionMonitor.isVpnAuthorized(context)
        if (!isAuthorized) {
            if (SafeNetVpnService.isServiceActive) {
                // VPN was marked active, but system authorization has been revoked!
                stateManager.recordInterruption(
                    "VPN authorization revoked by Android system settings or another VPN app.",
                    requiresRecovery = true
                )
                recordTamperEvent(
                    "WARNING",
                    "VPN_AUTHORIZATION_REVOKED",
                    "SafeNet VPN authorization was removed while service was active."
                )
                return false
            }
        }

        // 3. Check Notification Permission
        val hasNotifications = PermissionMonitor.isNotificationPermissionGranted(context)
        if (!hasNotifications && SafeNetVpnService.isServiceActive) {
            recordTamperEvent(
                "INFO",
                "NOTIFICATION_PERMISSION_ABSENT",
                "POST_NOTIFICATIONS permission not granted. Foreground protection remains active but banner may be silenced."
            )
        }

        // 4. Check DNS Filtering Engine Operational Health
        if (SafeNetVpnService.isServiceActive) {
            val trie = SafeNetVpnService.filterEngine.trie
            if (trie.ruleCount == 0) {
                stateManager.recordInterruption(
                    "DNS Filter Engine has 0 active filtering rules loaded.",
                    requiresRecovery = false
                )
                isHealthy = false
            } else {
                // Self-test trie evaluation
                val testEval = SafeNetVpnService.filterEngine.evaluate("cloudflare-dns.com")
                if (testEval.action != com.safenet.android.domain.model.FilterAction.BLOCK_SINKHOLE) {
                    stateManager.recordInterruption(
                        "DNS Filter Engine integrity test failed: bootstrap DoH test query was not intercepted.",
                        requiresRecovery = false
                    )
                    isHealthy = false
                }
            }
        }

        stateManager.onHealthCheckCompleted(isHealthy)
        return isHealthy
    }

    private fun recordTamperEvent(severity: String, type: String, description: String) {
        try {
            val app = SafeNetApplication.instance
            app.databaseHelper.recordSecurityEvent(
                SecurityEvent(
                    timestamp = System.currentTimeMillis(),
                    severity = severity,
                    eventType = type,
                    description = description
                )
            )
        } catch (e: Exception) {}
    }
}
