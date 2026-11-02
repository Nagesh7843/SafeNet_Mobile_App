package com.safenet.android.dpc

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import com.safenet.android.dpc.receiver.SafeNetDeviceAdminReceiver

class DpcManager(private val context: Context) {

    val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    val adminComponent: ComponentName =
        SafeNetDeviceAdminReceiver.getComponentName(context)

    fun isDeviceOwner(): Boolean {
        return devicePolicyManager.isDeviceOwnerApp(context.packageName)
    }

    fun isAdminActive(): Boolean {
        return devicePolicyManager.isAdminActive(adminComponent)
    }

    /**
     * Enforces Always-On VPN with OS-level lockdown.
     * When lockdown is enabled, Android's kernel drops all traffic outside the VPN tunnel.
     */
    fun setAlwaysOnVpn(enableLockdown: Boolean): Boolean {
        if (!isDeviceOwner()) return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                devicePolicyManager.setAlwaysOnVpnPackage(adminComponent, context.packageName, enableLockdown)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                devicePolicyManager.setAlwaysOnVpnPackage(adminComponent, context.packageName, enableLockdown)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun clearAlwaysOnVpn(): Boolean {
        if (!isDeviceOwner()) return false
        return try {
            devicePolicyManager.setAlwaysOnVpnPackage(adminComponent, null, false)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isAlwaysOnVpnConfigured(): Boolean {
        return try {
            devicePolicyManager.getAlwaysOnVpnPackage(adminComponent) == context.packageName
        } catch (e: Exception) {
            false
        }
    }

    fun isAlwaysOnLockdownEnabled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val method = devicePolicyManager.javaClass.getMethod("isAlwaysOnVpnLockdown", ComponentName::class.java)
                    method.invoke(devicePolicyManager, adminComponent) as Boolean
                } catch (e: Exception) {
                    isAlwaysOnVpnConfigured()
                }
            } else {
                isAlwaysOnVpnConfigured()
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Applies baseline Device Owner security policies:
     * - Disables Android Private DNS (DoT) to prevent DNS bypass
     * - Prevents user from altering VPN settings
     * - Prevents booting into safe mode to bypass app restrictions
     * - Blocks uninstallation of SafeNet
     */
    fun applyBaselineDeviceOwnerPolicies(): Boolean {
        if (!isDeviceOwner()) return false
        try {
            // 1. Disable Android OS Private DNS (DoT)
            devicePolicyManager.setGlobalSetting(
                adminComponent,
                "private_dns_mode",
                "off"
            )

            // 2. Prevent user from disabling or modifying VPN in Settings
            devicePolicyManager.addUserRestriction(
                adminComponent,
                UserManager.DISALLOW_CONFIG_VPN
            )

            // 3. Prevent booting into Safe Mode
            devicePolicyManager.addUserRestriction(
                adminComponent,
                UserManager.DISALLOW_SAFE_BOOT
            )

            // 4. Block uninstallation of SafeNet
            devicePolicyManager.setUninstallBlocked(
                adminComponent,
                context.packageName,
                true
            )

            return true
        } catch (e: Exception) {
            return false
        }
    }

    fun liftUserRestrictions(): Boolean {
        if (!isDeviceOwner()) return false
        try {
            devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_CONFIG_VPN)
            devicePolicyManager.clearUserRestriction(adminComponent, UserManager.DISALLOW_SAFE_BOOT)
            devicePolicyManager.setUninstallBlocked(adminComponent, context.packageName, false)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /**
     * Authorized unenrollment from Device Owner mode.
     */
    fun unenrollDeviceOwner(): Boolean {
        if (!isDeviceOwner()) return false
        try {
            liftUserRestrictions()
            clearAlwaysOnVpn()
            devicePolicyManager.clearDeviceOwnerApp(context.packageName)
            return true
        } catch (e: Exception) {
            return false
        }
    }
}
