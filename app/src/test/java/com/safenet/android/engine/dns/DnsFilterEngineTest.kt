package com.safenet.android.engine.dns

import com.safenet.android.domain.model.DomainCategory
import com.safenet.android.domain.model.FilterAction
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DnsFilterEngineTest {

    private lateinit var engine: DnsFilterEngine

    @Before
    fun setUp() {
        engine = DnsFilterEngine()
    }

    @Test
    fun testEducationalAllowlistOverridesBlockRules() {
        // Ensure educational domain is allowed even if someone tried to block it
        engine.trie.insert("wikipedia.org", DomainCategory.ADULT)
        val result = engine.evaluate("en.wikipedia.org")
        assertEquals(FilterAction.ALLOW, result.action)
    }

    @Test
    fun testBlockedCategoryEvaluation() {
        engine.trie.insert("explicit-dating.biz", DomainCategory.EXPLICIT_SEXUAL)
        val result = engine.evaluate("explicit-dating.biz")

        assertEquals(FilterAction.BLOCK_SINKHOLE, result.action)
        assertEquals(DomainCategory.EXPLICIT_SEXUAL, result.matchedCategory)
    }

    @Test
    fun testDisabledCategoryAllowsTraffic() {
        engine.trie.insert("gambling-site.com", DomainCategory.GAMBLING)
        // Disable gambling category
        engine.setCategoryEnabled(DomainCategory.GAMBLING, false)

        val result = engine.evaluate("gambling-site.com")
        assertEquals(FilterAction.ALLOW, result.action)
    }

    @Test
    fun testDoHBootstrapBlocked() {
        val result = engine.evaluate("cloudflare-dns.com")
        assertEquals(FilterAction.BLOCK_SINKHOLE, result.action)
    }

    @Test
    fun testSafeSearchRewrite() {
        engine.enforceSafeSearch = true
        val result = engine.evaluate("www.google.com")
        assertEquals(FilterAction.REWRITE_SAFESEARCH, result.action)
        assertTrue(result.isSafeSearchRewrite)
        assertEquals("216.239.38.120", result.rewrittenIp)
    }
}
