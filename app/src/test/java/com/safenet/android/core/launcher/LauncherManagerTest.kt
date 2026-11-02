package com.safenet.android.core.launcher

import org.junit.Assert.*
import org.junit.Test

class LauncherManagerTest {

    @Test
    fun testLauncherAliasClassName() {
        assertEquals("com.safenet.android.LauncherAlias", LauncherManager.LAUNCHER_ALIAS_CLASS)
    }

    @Test
    fun testDeepLinkUri() {
        assertEquals("safenet://open", LauncherManager.DEEP_LINK_URI)
    }
}
