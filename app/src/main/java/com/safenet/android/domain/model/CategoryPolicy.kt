package com.safenet.android.domain.model

enum class DomainCategory(val id: String, val title: String, val description: String, val defaultEnabled: Boolean) {
    ADULT("adult", "Adult Websites", "General adult and age-restricted web portals", true),
    PORNOGRAPHY("pornography", "Pornography", "Sexually explicit videos, images, and content", true),
    EXPLICIT_SEXUAL("explicit_sexual", "Explicit Content", "Adult dating, erotica, and uncensored content", true),
    GAMBLING("gambling", "Gambling & Betting", "Online casinos, sports betting, and lottery portals", true),
    MALWARE("malware", "Malware & Trojans", "Command and control servers, malware distribution nodes", true),
    PHISHING("phishing", "Phishing & Fraud", "Credential harvesting and deceptive financial domains", true),
    MALICIOUS_REDIRECTS("redirects", "Malicious Redirects", "Adware networks, spam redirectors, and pop-ups", true),
    CUSTOM_BLOCKED("custom_blocked", "Custom Blocked", "User-specified blacklist rules", true),
    CUSTOM_ALLOWED("custom_allowed", "Custom Allowed", "User-specified whitelist exemptions (e.g. education/medical)", false)
}

data class CategoryPolicyRule(
    val category: DomainCategory,
    val isEnabled: Boolean,
    val ruleCount: Int
)

enum class FilterAction {
    BLOCK_SINKHOLE, // Return 0.0.0.0 or ::
    BLOCK_NXDOMAIN, // Return RCODE 3 (Non-Existent Domain)
    ALLOW,
    REWRITE_SAFESEARCH
}
