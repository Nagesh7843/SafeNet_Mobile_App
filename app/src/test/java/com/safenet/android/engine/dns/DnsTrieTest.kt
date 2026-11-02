package com.safenet.android.engine.dns

import com.safenet.android.domain.model.DomainCategory
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DnsTrieTest {

    private lateinit var trie: DnsTrie

    @Before
    fun setUp() {
        trie = DnsTrie()
    }

    @Test
    fun testExactDomainMatch() {
        trie.insert("example-adult.com", DomainCategory.ADULT)
        assertEquals(DomainCategory.ADULT, trie.match("example-adult.com"))
        assertNull(trie.match("example.com"))
        assertNull(trie.match("not-adult.com"))
    }

    @Test
    fun testSubdomainWildcardMatch() {
        trie.insert("badsite.org", DomainCategory.PORNOGRAPHY)
        assertEquals(DomainCategory.PORNOGRAPHY, trie.match("badsite.org"))
        assertEquals(DomainCategory.PORNOGRAPHY, trie.match("www.badsite.org"))
        assertEquals(DomainCategory.PORNOGRAPHY, trie.match("videos.sub.badsite.org"))
        assertNull(trie.match("otherbadsite.org"))
    }

    @Test
    fun testDomainNormalization() {
        trie.insert("  MALWARE-NODE.NET.  ", DomainCategory.MALWARE)
        assertEquals(DomainCategory.MALWARE, trie.match("malware-node.net"))
        assertEquals(DomainCategory.MALWARE, trie.match("MALWARE-NODE.NET."))
        assertEquals(DomainCategory.MALWARE, trie.match("payload.malware-node.net"))
    }

    @Test
    fun testMultipleCategories() {
        trie.insert("adult.com", DomainCategory.ADULT)
        trie.insert("casino.com", DomainCategory.GAMBLING)
        trie.insert("phish.com", DomainCategory.PHISHING)

        assertEquals(DomainCategory.ADULT, trie.match("adult.com"))
        assertEquals(DomainCategory.GAMBLING, trie.match("casino.com"))
        assertEquals(DomainCategory.PHISHING, trie.match("phish.com"))
    }

    @Test
    fun testSubdomainOnlyWhenNotAllowed() {
        trie.insert("specific.com", DomainCategory.ADULT, matchSubdomains = false)
        assertEquals(DomainCategory.ADULT, trie.match("specific.com"))
        assertNull(trie.match("sub.specific.com"))
    }
}
