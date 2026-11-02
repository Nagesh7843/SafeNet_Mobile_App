package com.safenet.android.core.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.provider.Settings
import com.safenet.android.dpc.DpcManager

/**
 * Helper to check Always-On VPN status and guide standard APK users
 * to Android System VPN settings to configure Always-on VPN and Lockdown.
 */
object AlwaysOnVpnHelper {

    /**
     * Checks if SafeNet is authorized as a VPN.
     */
    fun isVpnAuthorized(context: Context): Boolean {
        return VpnService.prepare(context) == null
    }

    /**
     * Checks if Always-On VPN is configured via Device Owner if available.
     * Note: For standard APK enrollment, Android does not expose a public API
     * for apps to query whether the user selected them in system Always-on VPN settings.
     */
    fun isAlwaysOnConfigured(dpcManager: DpcManager): Boolean {
        return if (dpcManager.isDeviceOwner()) {
            dpcManager.isAlwaysOnVpnConfigured()
        } else {
            false
        }
    }

    /**
     * Checks if Lockdown is configured via Device Owner if available.
     */
    fun isLockdownEnabled(dpcManager: DpcManager): Boolean {
        return if (dpcManager.isDeviceOwner()) {
            dpcManager.isAlwaysOnLockdownEnabled()
        } else {
            false
        }
    }

    /**
     * Creates an intent leading directly to the Android System VPN settings.
     * Users can click the gear icon next to SafeNet and enable:
     * 1. Always-on VPN
     * 2. Block connections without VPN (Lockdown)
     */
    fun createOpenVpnSettingsIntent(): Intent {
        return Intent(Settings.ACTION_VPN_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
