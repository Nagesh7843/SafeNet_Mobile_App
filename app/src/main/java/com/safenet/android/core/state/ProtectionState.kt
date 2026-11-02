package com.safenet.android.core.state

/**
 * Standardized protection states for SafeNet.
 * All dashboard components, notifications, and logs adhere to these states.
 */
enum class ProtectionState(
    val displayName: String,
    val isProtected: Boolean
) {
    /** Fresh install or setup incomplete */
    NOT_CONFIGURED("Setup Required", false),

    /** System VPN permission prompt required */
    VPN_PERMISSION_REQUIRED("VPN Permission Required", false),

    /** Tunnel interface is currently negotiating or establishing */
    ACTIVATING("Activating...", false),

    /** Both VPN interface and DNS filtering engine are verified active */
    ACTIVE("Protected", true),

    /** VPN is active but some protection features or upstream connectivity are degraded */
    DEGRADED("Protection Degraded", true),

    /** VPN interface is turned off or revoked by user/system */
    VPN_DISABLED("Protection Disabled", false),

    /** No physical network interface available */
    NETWORK_UNAVAILABLE("Network Unavailable", false),

    /** VPN or configuration damaged; manual user recovery required */
    RECOVERY_REQUIRED("Recovery Required", false)
}

/**
 * Detailed diagnostics snapshot of system protection.
 */
data class ProtectionDetails(
    val state: ProtectionState = ProtectionState.NOT_CONFIGURED,
    val isVpnInterfaceUp: Boolean = false,
    val isDnsEngineOperational: Boolean = false,
    val isAlwaysOnConfigured: Boolean = false,
    val isLockdownEnabled: Boolean = false,
    val isDeviceOwner: Boolean = false,
    val isNetworkConnected: Boolean = true,
    val networkInterfaceType: String = "Unknown",
    val activeUpstreamResolver: String = "1.1.1.3",
    val ruleCount: Int = 0,
    val totalQueriesProcessed: Long = 0L,
    val totalQueriesBlocked: Long = 0L,
    val lastHealthCheckTimestamp: Long = 0L,
    val lastInterruptionReason: String? = null,
    val lastInterruptionTimestamp: Long = 0L,
    val missingRequirementMessage: String? = null
)
