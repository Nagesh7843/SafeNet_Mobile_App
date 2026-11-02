package com.safenet.android.core.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri

/**
 * Handles launcher icon independence and visibility.
 * Hiding or unhiding the app launcher icon toggles the <activity-alias>
 * using [PackageManager.DONT_KILL_APP] to guarantee that [SafeNetVpnService]
 * and ongoing DNS filtering are NEVER killed or interrupted.
 */
object LauncherManager {

    const val LAUNCHER_ALIAS_CLASS = "com.safenet.android.LauncherAlias"
    const val DEEP_LINK_URI = "safenet://open"

    fun getLauncherAliasComponent(context: Context): ComponentName {
        return ComponentName(context.packageName, LAUNCHER_ALIAS_CLASS)
    }

    /**
     * Checks whether the launcher icon alias is currently visible in the application drawer.
     */
    fun isLauncherIconVisible(context: Context): Boolean {
        val pm = context.packageManager
        val component = getLauncherAliasComponent(context)
        val state = pm.getComponentEnabledSetting(component)
        // Default state in manifest is enabled
        return state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }

    /**
     * Toggles launcher icon visibility.
     * Uses DONT_KILL_APP flag to avoid killing the process or VPN service.
     */
    fun setLauncherIconVisible(context: Context, visible: Boolean): Boolean {
        return try {
            val pm = context.packageManager
            val component = getLauncherAliasComponent(context)
            val newState = if (visible) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }

            pm.setComponentEnabledSetting(
                component,
                newState,
                PackageManager.DONT_KILL_APP
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Creates an intent to launch SafeNet via its deep-link scheme.
     * Used when the launcher icon is hidden.
     */
    fun createDeepLinkIntent(): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(DEEP_LINK_URI)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }
}
