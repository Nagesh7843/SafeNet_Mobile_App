package com.safenet.android.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.safenet.android.core.monitor.PermissionMonitor
import com.safenet.android.dpc.DpcManager
import com.safenet.android.engine.vpn.SafeNetVpnService

/**
 * Recovers SafeNet VPN protection after device reboot for BOTH
 * Device Owner managed setups and standard APK installations.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val appContext = context.applicationContext
            val dpcManager = DpcManager(appContext)

            if (dpcManager.isDeviceOwner()) {
                // Re-affirm baseline Device Owner policies
                dpcManager.applyBaselineDeviceOwnerPolicies()
            }

            // For both Device Owner and standard APK installations:
            // If VPN permission is granted, start SafeNet VPN
            if (PermissionMonitor.isVpnAuthorized(appContext)) {
                val vpnIntent = Intent(appContext, SafeNetVpnService::class.java).apply {
                    action = SafeNetVpnService.ACTION_START_VPN
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        appContext.startForegroundService(vpnIntent)
                    } else {
                        appContext.startService(vpnIntent)
                    }
                } catch (e: Exception) {}
            }
        }
    }
}
