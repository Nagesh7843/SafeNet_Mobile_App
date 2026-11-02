package com.safenet.android.domain.model

data class BrowserInfo(
    val packageName: String,
    val displayName: String,
    val isInstalled: Boolean,
    val supportsManagedConfigurations: Boolean,
    val supportsIncognitoDisabling: Boolean,
    val isIncognitoCurrentlyRestricted: Boolean,
    val managedConfigStatus: String,
    val networkFilteringStatus: String = "Protected via SafeNet TUN"
)
