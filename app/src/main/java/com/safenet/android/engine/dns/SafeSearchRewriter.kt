package com.safenet.android.engine.dns

/**
 * Handles DNS-level Safe Search enforcement by intercepting search engine queries
 * and rewriting them to their respective strict VIP IP addresses.
 */
object SafeSearchRewriter {

    private const val GOOGLE_SAFE_SEARCH_IPV4 = "216.239.38.120"
    private const val GOOGLE_SAFE_SEARCH_IPV6 = "2001:4860:4806::78"

    private const val YOUTUBE_RESTRICTED_IPV4 = "216.239.38.119"
    private const val YOUTUBE_RESTRICTED_IPV6 = "2001:4860:4806::77"

    private const val BING_STRICT_IPV4 = "204.79.197.220"
    private const val DUCKDUCKGO_SAFE_IPV4 = "52.142.124.215"

    fun getSafeSearchRewrite(domain: String, isIpv6: Boolean = false): String? {
        val normalized = DnsTrie.normalizeDomain(domain)

        // Google search domains
        if (isGoogleSearch(normalized)) {
            return if (isIpv6) GOOGLE_SAFE_SEARCH_IPV6 else GOOGLE_SAFE_SEARCH_IPV4
        }

        // YouTube domains
        if (isYouTube(normalized)) {
            return if (isIpv6) YOUTUBE_RESTRICTED_IPV6 else YOUTUBE_RESTRICTED_IPV4
        }

        // Bing domains
        if (isBing(normalized)) {
            return BING_STRICT_IPV4
        }

        // DuckDuckGo domains
        if (isDuckDuckGo(normalized)) {
            return DUCKDUCKGO_SAFE_IPV4
        }

        return null
    }

    private fun isGoogleSearch(domain: String): Boolean {
        if (domain == "google.com" || domain == "www.google.com") return true
        if (domain.startsWith("www.google.") || domain.startsWith("google.")) {
            // Check country code TLDs (e.g. google.co.uk, google.ca, google.co.in)
            val parts = domain.split('.')
            if (parts.size in 2..4 && (parts[0] == "google" || parts[1] == "google")) {
                return true
            }
        }
        return false
    }

    private fun isYouTube(domain: String): Boolean {
        return domain == "youtube.com" ||
                domain == "www.youtube.com" ||
                domain == "m.youtube.com" ||
                domain == "youtubei.googleapis.com" ||
                domain == "youtube.googleapis.com" ||
                domain == "www.youtube-nocookie.com"
    }

    private fun isBing(domain: String): Boolean {
        return domain == "bing.com" || domain == "www.bing.com"
    }

    private fun isDuckDuckGo(domain: String): Boolean {
        return domain == "duckduckgo.com" || domain == "www.duckduckgo.com"
    }
}
