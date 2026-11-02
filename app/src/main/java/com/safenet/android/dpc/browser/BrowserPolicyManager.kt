package com.safenet.android.dpc.browser

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import com.safenet.android.domain.model.BrowserInfo
import com.safenet.android.dpc.DpcManager

class BrowserPolicyManager(
    private val context: Context,
    private val dpcManager: DpcManager
) {

    companion object {
        const val PKG_CHROME = "com.android.chrome"
        const val PKG_FIREFOX = "org.mozilla.firefox"
        const val PKG_SAMSUNG = "com.sec.android.app.sbrowser"
        const val PKG_EDGE = "com.microsoft.emmx"
        const val PKG_BRAVE = "com.brave.browser"

        // Chrome Enterprise Managed Configuration Keys
        const val KEY_INCOGNITO_MODE_AVAILABILITY = "IncognitoModeAvailability"
        const val KEY_SAFE_SITES_FILTER_BEHAVIOR = "SafeSitesFilterBehavior"

        // 0 = Enabled, 1 = Disabled, 2 = Forced
        const val INCOGNITO_DISABLED = 1
        const val INCOGNITO_ENABLED = 0
    }

    /**
     * Inspects the device for installed browsers and audits their current policy state.
     */
    fun getInstalledBrowsers(): List<BrowserInfo> {
        val packageManager = context.packageManager
        val list = mutableListOf<BrowserInfo>()

        // 1. Google Chrome
        val chromeInstalled = isAppInstalled(PKG_CHROME, packageManager)
        val chromeRestricted = isChromeIncognitoRestricted()
        list.add(
            BrowserInfo(
                packageName = PKG_CHROME,
                displayName = "Google Chrome",
                isInstalled = chromeInstalled,
                supportsManagedConfigurations = true,
                supportsIncognitoDisabling = true,
                isIncognitoCurrentlyRestricted = chromeRestricted,
                managedConfigStatus = if (chromeRestricted) "Incognito Disabled via Managed Config" else "Unmanaged"
            )
        )

        // 2. Mozilla Firefox
        val firefoxInstalled = isAppInstalled(PKG_FIREFOX, packageManager)
        list.add(
            BrowserInfo(
                packageName = PKG_FIREFOX,
                displayName = "Mozilla Firefox",
                isInstalled = firefoxInstalled,
                supportsManagedConfigurations = false,
                supportsIncognitoDisabling = false,
                isIncognitoCurrentlyRestricted = false,
                managedConfigStatus = "Private tabs filtered at network layer (No enterprise bundle API)"
            )
        )

        // 3. Samsung Internet
        val samsungInstalled = isAppInstalled(PKG_SAMSUNG, packageManager)
        list.add(
            BrowserInfo(
                packageName = PKG_SAMSUNG,
                displayName = "Samsung Internet",
                isInstalled = samsungInstalled,
                supportsManagedConfigurations = false,
                supportsIncognitoDisabling = false,
                isIncognitoCurrentlyRestricted = false,
                managedConfigStatus = "Secret mode filtered at network layer"
            )
        )

        // 4. Microsoft Edge
        val edgeInstalled = isAppInstalled(PKG_EDGE, packageManager)
        val edgeRestricted = isEdgeInPrivateRestricted()
        list.add(
            BrowserInfo(
                packageName = PKG_EDGE,
                displayName = "Microsoft Edge",
                isInstalled = edgeInstalled,
                supportsManagedConfigurations = true,
                supportsIncognitoDisabling = true,
                isIncognitoCurrentlyRestricted = edgeRestricted,
                managedConfigStatus = if (edgeRestricted) "InPrivate Disabled via Managed Config" else "Standard Filtering"
            )
        )

        // 5. Brave
        val braveInstalled = isAppInstalled(PKG_BRAVE, packageManager)
        list.add(
            BrowserInfo(
                packageName = PKG_BRAVE,
                displayName = "Brave Browser",
                isInstalled = braveInstalled,
                supportsManagedConfigurations = false,
                supportsIncognitoDisabling = false,
                isIncognitoCurrentlyRestricted = false,
                managedConfigStatus = "Private mode filtered; DoH bootstrap blocked"
            )
        )

        return list
    }

    /**
     * Enforces Google Chrome restrictions:
     * - Disables Incognito mode (IncognitoModeAvailability = 1)
     * - Enforces Google SafeSites filter (SafeSitesFilterBehavior = 1)
     */
    fun enforceChromeIncognitoRestriction(disabled: Boolean): Boolean {
        if (!dpcManager.isDeviceOwner()) return false
        return try {
            val bundle = Bundle().apply {
                putInt(
                    KEY_INCOGNITO_MODE_AVAILABILITY,
                    if (disabled) INCOGNITO_DISABLED else INCOGNITO_ENABLED
                )
                putInt(KEY_SAFE_SITES_FILTER_BEHAVIOR, 1)
            }
            dpcManager.devicePolicyManager.setApplicationRestrictions(
                dpcManager.adminComponent,
                PKG_CHROME,
                bundle
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isChromeIncognitoRestricted(): Boolean {
        if (!dpcManager.isDeviceOwner()) return false
        return try {
            val bundle = dpcManager.devicePolicyManager.getApplicationRestrictions(
                dpcManager.adminComponent,
                PKG_CHROME
            )
            bundle.getInt(KEY_INCOGNITO_MODE_AVAILABILITY, 0) == INCOGNITO_DISABLED
        } catch (e: Exception) {
            false
        }
    }

    fun enforceEdgeInPrivateRestriction(disabled: Boolean): Boolean {
        if (!dpcManager.isDeviceOwner()) return false
        return try {
            val bundle = Bundle().apply {
                putInt(
                    "InPrivateModeAvailability",
                    if (disabled) 1 else 0
                )
            }
            dpcManager.devicePolicyManager.setApplicationRestrictions(
                dpcManager.adminComponent,
                PKG_EDGE,
                bundle
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    fun isEdgeInPrivateRestricted(): Boolean {
        if (!dpcManager.isDeviceOwner()) return false
        return try {
            val bundle = dpcManager.devicePolicyManager.getApplicationRestrictions(
                dpcManager.adminComponent,
                PKG_EDGE
            )
            bundle.getInt("InPrivateModeAvailability", 0) == 1
        } catch (e: Exception) {
            false
        }
    }

    private fun isAppInstalled(packageName: String, packageManager: PackageManager): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
