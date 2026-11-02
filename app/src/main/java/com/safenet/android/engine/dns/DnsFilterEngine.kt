package com.safenet.android.engine.dns

import com.safenet.android.domain.model.BlockEvent
import com.safenet.android.domain.model.DomainCategory
import com.safenet.android.domain.model.DomainEvaluationResult
import com.safenet.android.domain.model.FilterAction
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong

class DnsFilterEngine {

    val trie = DnsTrie()
    private val allowlist = CopyOnWriteArraySet<String>()
    private val categoryToggles = ConcurrentHashMap<DomainCategory, Boolean>()

    @Volatile
    var enforceSafeSearch: Boolean = true

    // Live Metrics
    val totalQueriesProcessed = AtomicLong(0)
    val totalQueriesBlocked = AtomicLong(0)
    val adultQueriesBlocked = AtomicLong(0)
    val malwareQueriesBlocked = AtomicLong(0)
    val phishingQueriesBlocked = AtomicLong(0)

    var onBlockEventListener: ((BlockEvent) -> Unit)? = null

    init {
        // Initialize default category toggles
        for (category in DomainCategory.values()) {
            categoryToggles[category] = category.defaultEnabled
        }

        // Initialize default educational & medical allowlist
        seedDefaultAllowlist()

        // Seed initial high-priority adult, malware, and DoH bootstrap block rules
        seedInitialBlockRules()
    }

    fun isCategoryEnabled(category: DomainCategory): Boolean {
        return categoryToggles[category] ?: true
    }

    fun setCategoryEnabled(category: DomainCategory, enabled: Boolean) {
        categoryToggles[category] = enabled
    }

    fun addAllowedDomain(domain: String) {
        val normalized = DnsTrie.normalizeDomain(domain)
        if (normalized.isNotEmpty()) {
            allowlist.add(normalized)
        }
    }

    fun removeAllowedDomain(domain: String) {
        allowlist.remove(DnsTrie.normalizeDomain(domain))
    }

    fun isAllowlisted(domain: String): Boolean {
        val normalized = DnsTrie.normalizeDomain(domain)
        if (allowlist.contains(normalized)) return true

        // Check parent domains
        val parts = normalized.split('.')
        for (i in 1 until parts.size) {
            val parent = parts.subList(i, parts.size).joinToString(".")
            if (allowlist.contains(parent)) return true
        }
        return false
    }

    /**
     * Core evaluation entry point for a requested domain.
     */
    fun evaluate(domain: String, isIpv6: Boolean = false): DomainEvaluationResult {
        totalQueriesProcessed.incrementAndGet()
        val normalized = DnsTrie.normalizeDomain(domain)

        // 1. Explicit Whitelist Check (Overrides all filters)
        if (isAllowlisted(normalized)) {
            return DomainEvaluationResult(
                domain = normalized,
                action = FilterAction.ALLOW,
                matchedCategory = null
            )
        }

        // 2. Safe Search Rewrite Check
        if (enforceSafeSearch) {
            val rewrittenIp = SafeSearchRewriter.getSafeSearchRewrite(normalized, isIpv6)
            if (rewrittenIp != null) {
                return DomainEvaluationResult(
                    domain = normalized,
                    action = FilterAction.REWRITE_SAFESEARCH,
                    matchedCategory = null,
                    isSafeSearchRewrite = true,
                    rewrittenIp = rewrittenIp
                )
            }
        }

        // 3. Local Trie Match Check
        val matchedCategory = trie.match(normalized)
        if (matchedCategory != null && isCategoryEnabled(matchedCategory)) {
            totalQueriesBlocked.incrementAndGet()
            when (matchedCategory) {
                DomainCategory.ADULT, DomainCategory.PORNOGRAPHY, DomainCategory.EXPLICIT_SEXUAL ->
                    adultQueriesBlocked.incrementAndGet()
                DomainCategory.MALWARE ->
                    malwareQueriesBlocked.incrementAndGet()
                DomainCategory.PHISHING ->
                    phishingQueriesBlocked.incrementAndGet()
                else -> {}
            }

            val event = BlockEvent(
                domain = normalized,
                category = matchedCategory,
                actionTaken = "SINKHOLE_0.0.0.0"
            )
            onBlockEventListener?.invoke(event)

            return DomainEvaluationResult(
                domain = normalized,
                action = FilterAction.BLOCK_SINKHOLE,
                matchedCategory = matchedCategory
            )
        }

        // 4. Default Allow
        return DomainEvaluationResult(
            domain = normalized,
            action = FilterAction.ALLOW,
            matchedCategory = null
        )
    }

    private fun seedDefaultAllowlist() {
        val defaultAllowed = listOf(
            // Educational
            "khanacademy.org", "wikipedia.org", "wikimedia.org", "mit.edu", "stanford.edu",
            "harvard.edu", "coursera.org", "edx.org", "duolingo.com", "quizlet.com",
            // Medical & Health
            "nih.gov", "cdc.gov", "who.int", "mayoclinic.org", "webmd.com", "healthline.com",
            "pubmed.ncbi.nlm.nih.gov", "medlineplus.gov"
        )
        for (domain in defaultAllowed) {
            addAllowedDomain(domain)
        }
    }

    private fun seedInitialBlockRules() {
        // Known DoH Bootstraps (Must be blocked so browsers cannot bypass network filter)
        val dohBootstraps = listOf(
            "cloudflare-dns.com", "dns.google", "dns.quad9.net",
            "doh.cleanbrowsing.org", "dns.nextdns.io", "doh.mullvad.net",
            "dns.adguard-dns.com", "dns-family.adguard.com"
        )
        for (doh in dohBootstraps) {
            trie.insert(doh, DomainCategory.MALICIOUS_REDIRECTS)
        }

        // Adult & Pornography representative seeds (will be expanded by blocklist updates)
        val adultSeeds = listOf(
            "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "youporn.com",
            "redtube.com", "chaturbate.com", "stripchat.com", "onlyfans.com",
            "bongacams.com", "cam4.com", "livejasmin.com", "faphouse.com",
            "brazzers.com", "tube8.com", "spankwire.com", "beeg.com", "hqporner.com"
        )
        for (domain in adultSeeds) {
            trie.insert(domain, DomainCategory.PORNOGRAPHY)
        }

        // Gambling Seeds
        val gamblingSeeds = listOf(
            "bet365.com", "pokerstars.com", "bovada.lv", "draftkings.com",
            "fanduel.com", "888casino.com", "betway.com", "stake.com"
        )
        for (domain in gamblingSeeds) {
            trie.insert(domain, DomainCategory.GAMBLING)
        }

        // Malware & Phishing Seeds
        val malwareSeeds = listOf(
            "tracker-badware.top", "malicious-payload-drop.xyz", "ransomware-cnc.cc",
            "phishing-verify-login.account-security.tk", "stealer-gate.info"
        )
        for (domain in malwareSeeds) {
            trie.insert(domain, DomainCategory.MALWARE)
        }
    }
}
