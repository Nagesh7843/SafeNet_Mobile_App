package com.safenet.android.core.monitor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Checks system permissions and authorization states without attempting unauthorized changes.
 */
object PermissionMonitor {

    /**
     * Checks if SafeNet is authorized as the active VPN service by the user.
     * Returns true if already authorized (prepare returns null).
     */
    fun isVpnAuthorized(context: Context): Boolean {
        return VpnService.prepare(context) == null
    }

    /**
     * Checks if notification posting permission is granted (required on Android 13+ / API 33+).
     */
    fun isNotificationPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Checks if any active network connection (Wi-Fi, Cellular, Ethernet) is available.
     */
    fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Retrieves a human-readable network description.
     */
    fun getNetworkTypeName(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return "Disconnected"
        val activeNetwork = cm.activeNetwork ?: return "Disconnected"
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return "Disconnected"

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN Tunnel"
            else -> "Connected"
        }
    }
}
