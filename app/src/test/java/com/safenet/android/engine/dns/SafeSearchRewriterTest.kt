package com.safenet.android.engine.dns

import org.junit.Assert.*
import org.junit.Test

class SafeSearchRewriterTest {

    @Test
    fun testGoogleSearchDomains() {
        val googleIpv4 = SafeSearchRewriter.getSafeSearchRewrite("google.com", isIpv6 = false)
        assertEquals("216.239.38.120", googleIpv4)

        val googleWww = SafeSearchRewriter.getSafeSearchRewrite("www.google.com", isIpv6 = false)
        assertEquals("216.239.38.120", googleWww)

        val googleUk = SafeSearchRewriter.getSafeSearchRewrite("www.google.co.uk", isIpv6 = false)
        assertEquals("216.239.38.120", googleUk)
    }

    @Test
    fun testYouTubeDomains() {
        val yt = SafeSearchRewriter.getSafeSearchRewrite("www.youtube.com", isIpv6 = false)
        assertEquals("216.239.38.119", yt)

        val mYt = SafeSearchRewriter.getSafeSearchRewrite("m.youtube.com", isIpv6 = false)
        assertEquals("216.239.38.119", mYt)
    }

    @Test
    fun testBingDomains() {
        val bing = SafeSearchRewriter.getSafeSearchRewrite("www.bing.com", isIpv6 = false)
        assertEquals("204.79.197.220", bing)
    }

    @Test
    fun testDuckDuckGoDomains() {
        val ddg = SafeSearchRewriter.getSafeSearchRewrite("duckduckgo.com", isIpv6 = false)
        assertEquals("52.142.124.215", ddg)
    }

    @Test
    fun testOrdinaryDomainDoesNotRewrite() {
        assertNull(SafeSearchRewriter.getSafeSearchRewrite("example.org"))
        assertNull(SafeSearchRewriter.getSafeSearchRewrite("wikipedia.org"))
    }
}
