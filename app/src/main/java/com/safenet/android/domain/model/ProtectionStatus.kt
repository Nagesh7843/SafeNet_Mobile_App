package com.safenet.android.domain.model

enum class OverallProtectionState(val displayName: String) {
    PROTECTED("Protected"),
    PARTIALLY_PROTECTED("Partially Protected"),
    PROTECTION_DISABLED("Protection Disabled"),
    VPN_DISCONNECTED("VPN Disconnected"),
    DEVICE_NOT_MANAGED("Device Not Managed"),
    BLOCKLIST_UPDATE_FAILED("Blocklist Update Failed")
}

data class SystemProtectionHealth(
    val overallState: OverallProtectionState,
    val isVpnActive: Boolean,
    val isDnsFilteringActive: Boolean,
    val isDeviceOwnerActive: Boolean,
    val isLockdownEnabled: Boolean,
    val isChromeIncognitoRestricted: Boolean,
    val totalBlockedRequests: Long,
    val totalBlockedDomains: Int,
    val lastBlocklistUpdateTimestamp: Long,
    val networkConnectionType: String,
    val upstreamResolver: String,
    val blocklistVersion: String
)
