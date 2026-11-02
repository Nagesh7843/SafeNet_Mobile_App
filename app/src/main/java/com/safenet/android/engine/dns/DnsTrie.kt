package com.safenet.android.engine.dns

import com.safenet.android.domain.model.DomainCategory
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance Reverse Domain Radix Trie for sub-millisecond DNS policy lookups.
 * Labels are stored in reverse order (e.g. "com" -> "example" -> "ads") so that
 * subdomain matching (e.g. *.example.com) is evaluated in O(L) time where L is label depth.
 */
class DnsTrie {

    private class Node {
        val children = ConcurrentHashMap<String, Node>()
        @Volatile var isTerminal: Boolean = false
        @Volatile var category: DomainCategory? = null
        @Volatile var matchSubdomains: Boolean = true
    }

    private val root = Node()
    @Volatile var ruleCount: Int = 0
        private set

    /**
     * Inserts a domain with its associated category into the Trie.
     */
    fun insert(rawDomain: String, category: DomainCategory, matchSubdomains: Boolean = true) {
        val normalized = normalizeDomain(rawDomain)
        if (normalized.isEmpty()) return

        val labels = normalized.split('.').reversed()
        var current = root
        for (label in labels) {
            current = current.children.computeIfAbsent(label) { Node() }
        }
        current.isTerminal = true
        current.category = category
        current.matchSubdomains = matchSubdomains
        ruleCount++
    }

    /**
     * Matches a domain against the Trie.
     * Returns the matched category, or null if no rule applies.
     */
    fun match(rawDomain: String): DomainCategory? {
        val normalized = normalizeDomain(rawDomain)
        if (normalized.isEmpty()) return null

        val labels = normalized.split('.').reversed()
        var current = root

        for (i in labels.indices) {
            val label = labels[i]
            val next = current.children[label] ?: return null
            current = next

            // If this node is terminal and allows subdomain wildcard or is the exact match (end of labels)
            if (current.isTerminal) {
                if (current.matchSubdomains || i == labels.lastIndex) {
                    return current.category
                }
            }
        }
        return if (current.isTerminal) current.category else null
    }

    fun clear() {
        root.children.clear()
        ruleCount = 0
    }

    companion object {
        fun normalizeDomain(domain: String): String {
            return domain.trim().lowercase().removeSuffix(".")
        }
    }
}
