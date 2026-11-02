package com.safenet.android.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.safenet.android.core.monitor.PermissionMonitor
import com.safenet.android.engine.vpn.SafeNetVpnService

/**
 * Automatically resumes SafeNet protection after an application update (MY_PACKAGE_REPLACED).
 */
class PackageReplacedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // Check if VPN authorization is already granted
            if (PermissionMonitor.isVpnAuthorized(context)) {
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
