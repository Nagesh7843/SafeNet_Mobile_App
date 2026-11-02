package com.safenet.android.core.state

import android.app.NotificationManager
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicReference

/**
 * Centralized thread-safe Protection State Manager.
 * Single source of truth for the entire application (UI, notifications, background services).
 */
class ProtectionStateManager private constructor() {

    private val _details = MutableStateFlow(ProtectionDetails())
    val details: StateFlow<ProtectionDetails> = _details.asStateFlow()

    private val lastKnownState = AtomicReference(ProtectionState.NOT_CONFIGURED)

    companion object {
        val instance: ProtectionStateManager by lazy { ProtectionStateManager() }
    }

    /**
     * Updates protection state when VPN service successfully establishes interface and starts packet engine.
     */
    @Synchronized
    fun onVpnOperational(
        upstreamResolver: String,
        ruleCount: Int
    ) {
        val next = _details.value.copy(
            state = ProtectionState.ACTIVE,
            isVpnInterfaceUp = true,
            isDnsEngineOperational = true,
            activeUpstreamResolver = upstreamResolver,
            ruleCount = ruleCount,
            lastHealthCheckTimestamp = System.currentTimeMillis(),
            missingRequirementMessage = null
        )
        updateDetails(next)
    }

    /**
     * Updates state when VPN is in the process of starting.
     */
    @Synchronized
    fun onVpnActivating() {
        val next = _details.value.copy(
            state = ProtectionState.ACTIVATING,
            isVpnInterfaceUp = false,
            isDnsEngineOperational = false
        )
        updateDetails(next)
    }

    /**
     * Updates state when VPN is stopped cleanly by the user.
     */
    @Synchronized
    fun onVpnStopped() {
        val next = _details.value.copy(
            state = ProtectionState.VPN_DISABLED,
            isVpnInterfaceUp = false,
            isDnsEngineOperational = false,
            lastInterruptionReason = "User requested stop",
            lastInterruptionTimestamp = System.currentTimeMillis()
        )
        updateDetails(next)
    }

    /**
     * Updates state when VPN is revoked by Android OS or another VPN application.
     */
    @Synchronized
    fun onVpnRevoked() {
        val next = _details.value.copy(
            state = ProtectionState.VPN_DISABLED,
            isVpnInterfaceUp = false,
            isDnsEngineOperational = false,
            lastInterruptionReason = "VPN authorization revoked by Android OS or another VPN app",
            lastInterruptionTimestamp = System.currentTimeMillis(),
            missingRequirementMessage = "VPN profile was disconnected in system settings."
        )
        updateDetails(next)
    }

    /**
     * Updates state when physical network connectivity changes.
     */
    @Synchronized
    fun onNetworkConnectivityChanged(isConnected: Boolean, networkType: String) {
        val current = _details.value
        val newState = when {
            !isConnected -> ProtectionState.NETWORK_UNAVAILABLE
            !current.isVpnInterfaceUp -> ProtectionState.VPN_DISABLED
            else -> ProtectionState.ACTIVE
        }
        val next = current.copy(
            state = newState,
            isNetworkConnected = isConnected,
            networkInterfaceType = networkType
        )
        updateDetails(next)
    }

    /**
     * Records an unexpected protection interruption.
     */
    @Synchronized
    fun recordInterruption(reason: String, requiresRecovery: Boolean = false) {
        val nextState = if (requiresRecovery) ProtectionState.RECOVERY_REQUIRED else ProtectionState.DEGRADED
        val next = _details.value.copy(
            state = nextState,
            isVpnInterfaceUp = false,
            isDnsEngineOperational = false,
            lastInterruptionReason = reason,
            lastInterruptionTimestamp = System.currentTimeMillis(),
            missingRequirementMessage = reason
        )
        updateDetails(next)
    }

    /**
     * Updates live metric counters.
     */
    @Synchronized
    fun updateMetrics(totalProcessed: Long, totalBlocked: Long, ruleCount: Int) {
        val current = _details.value
        val next = current.copy(
            totalQueriesProcessed = totalProcessed,
            totalQueriesBlocked = totalBlocked,
            ruleCount = ruleCount
        )
        _details.value = next
    }

    /**
     * Updates Always-On / Lockdown detection status.
     */
    @Synchronized
    fun updateAlwaysOnStatus(isConfigured: Boolean, isLockdown: Boolean, isDeviceOwner: Boolean) {
        val current = _details.value
        val next = current.copy(
            isAlwaysOnConfigured = isConfigured,
            isLockdownEnabled = isLockdown,
            isDeviceOwner = isDeviceOwner
        )
        _details.value = next
    }

    /**
     * Records periodic health check probe results.
     */
    @Synchronized
    fun onHealthCheckCompleted(isHealthy: Boolean, errorDetail: String? = null) {
        val current = _details.value
        val newState = if (current.isVpnInterfaceUp) {
            if (isHealthy) ProtectionState.ACTIVE else ProtectionState.DEGRADED
        } else {
            current.state
        }
        val next = current.copy(
            state = newState,
            isDnsEngineOperational = isHealthy && current.isVpnInterfaceUp,
            lastHealthCheckTimestamp = System.currentTimeMillis(),
            missingRequirementMessage = if (!isHealthy) errorDetail ?: "Upstream DNS probe timeout" else null
        )
        updateDetails(next)
    }

    private fun updateDetails(next: ProtectionDetails) {
        lastKnownState.set(next.state)
        _details.value = next
    }
}
