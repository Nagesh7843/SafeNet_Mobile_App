package com.safenet.android.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.safenet.android.dpc.DpcManager
import com.safenet.android.engine.vpn.SafeNetVpnService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val dpcManager = DpcManager(context.applicationContext)
            if (dpcManager.isDeviceOwner()) {
                // Re-affirm baseline Device Owner policies
                dpcManager.applyBaselineDeviceOwnerPolicies()

                // If Always-On VPN is configured, start VPN service if not automatically started
                val vpnIntent = Intent(context, SafeNetVpnService::class.java).apply {
                    action = SafeNetVpnService.ACTION_START_VPN
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(vpnIntent)
                    } else {
                        context.startService(vpnIntent)
                    }
                } catch (e: Exception) {}
            }
        }
    }
}
