package com.safenet.android.core.recovery

import org.junit.Assert.*
import org.junit.Test

class ConfigRecoveryTest {

    @Test
    fun testDomainSanitization() {
        assertEquals("malicious.com", ConfigRecoveryManager.sanitizeDomain("  malicious.com.  "))
        assertEquals("bad-domain.xyz", ConfigRecoveryManager.sanitizeDomain("http://bad-domain.xyz/test"))
        assertEquals("phishing123.com", ConfigRecoveryManager.sanitizeDomain("https://phishing123.com:8080/path"))
        assertEquals("clean.org", ConfigRecoveryManager.sanitizeDomain(".clean.org..."))
    }

    @Test
    fun testTamperDetectionRejectsModifiedPayload() {
        val originalPayload = "{\"version\":1,\"safeSearchEnabled\":true,\"categories\":{\"adult\":true}}"

        val method = ConfigRecoveryManager.javaClass.getDeclaredMethod("computeChecksum", String::class.java)
        method.isAccessible = true
        val originalChecksum = method.invoke(ConfigRecoveryManager, originalPayload) as String

        // Alter single byte from true to false
        val tamperedPayload = "{\"version\":1,\"safeSearchEnabled\":false,\"categories\":{\"adult\":true}}"
        val tamperedChecksum = method.invoke(ConfigRecoveryManager, tamperedPayload) as String

        assertNotEquals("Tampered payload must produce distinct checksum", originalChecksum, tamperedChecksum)
    }

    @Test
    fun testChecksumIsDeterministic() {
        val method = ConfigRecoveryManager.javaClass.getDeclaredMethod("computeChecksum", String::class.java)
        method.isAccessible = true
        val data = "{\"test\":123}"
        val checksum1 = method.invoke(ConfigRecoveryManager, data) as String
        val checksum2 = method.invoke(ConfigRecoveryManager, data) as String
        assertEquals(checksum1, checksum2)
        assertEquals(64, checksum1.length) // SHA-256 hex is 64 characters
    }
}
