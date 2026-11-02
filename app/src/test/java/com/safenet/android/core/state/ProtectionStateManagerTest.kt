package com.safenet.android.core.state

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ProtectionStateManagerTest {

    private lateinit var manager: ProtectionStateManager

    @Before
    fun setUp() {
        manager = ProtectionStateManager.instance
    }

    @Test
    fun testInitialOrStoppedState() {
        manager.onVpnStopped()
        val details = manager.details.value
        assertEquals(ProtectionState.VPN_DISABLED, details.state)
        assertFalse(details.isVpnInterfaceUp)
        assertFalse(details.isDnsEngineOperational)
    }

    @Test
    fun testActivatingState() {
        manager.onVpnActivating()
        val details = manager.details.value
        assertEquals(ProtectionState.ACTIVATING, details.state)
        assertFalse(details.isVpnInterfaceUp)
    }

    @Test
    fun testOperationalStateSetsActiveOnlyWhenUp() {
        manager.onVpnOperational(upstreamResolver = "1.1.1.3", ruleCount = 42)
        val details = manager.details.value
        assertEquals(ProtectionState.ACTIVE, details.state)
        assertTrue(details.isVpnInterfaceUp)
        assertTrue(details.isDnsEngineOperational)
        assertEquals("1.1.1.3", details.activeUpstreamResolver)
        assertEquals(42, details.ruleCount)
    }

    @Test
    fun testVpnRevocationTransitionsToDisabled() {
        manager.onVpnOperational("1.1.1.3", 100)
        manager.onVpnRevoked()
        val details = manager.details.value
        assertEquals(ProtectionState.VPN_DISABLED, details.state)
        assertFalse(details.isVpnInterfaceUp)
        assertNotNull(details.lastInterruptionReason)
    }

    @Test
    fun testNetworkDisconnectTransitionsToNetworkUnavailable() {
        manager.onVpnOperational("1.1.1.3", 50)
        manager.onNetworkConnectivityChanged(false, "Disconnected")
        val details = manager.details.value
        assertEquals(ProtectionState.NETWORK_UNAVAILABLE, details.state)
        assertFalse(details.isNetworkConnected)
    }

    @Test
    fun testInterruptionRequiresRecovery() {
        manager.recordInterruption("Fatal TUN interface error", requiresRecovery = true)
        val details = manager.details.value
        assertEquals(ProtectionState.RECOVERY_REQUIRED, details.state)
        assertFalse(details.isVpnInterfaceUp)
    }
}
