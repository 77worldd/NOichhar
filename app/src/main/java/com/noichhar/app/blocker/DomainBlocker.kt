package com.noichhar.app.blocker

import com.noichhar.app.util.DomainValidator
import com.noichhar.app.util.Logger
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class DomainBlocker {

    private val blockedDomains: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val wildcardRules = CopyOnWriteArrayList<String>()
    private val whitelistedDomains: MutableSet<String> =
        Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    @Volatile
    var domainCount: Int = 0
        private set

    fun loadDomains(domains: List<String>) {
        val exact = HashSet<String>(domains.size)
        val wildcard = ArrayList<String>()
        for (raw in domains) {
            val rule = DomainValidator.normalize(raw) ?: continue
            if (rule.wildcard) wildcard.add(rule.domain) else exact.add(rule.domain)
        }
        blockedDomains.clear()
        blockedDomains.addAll(exact)
        wildcardRules.clear()
        wildcardRules.addAll(wildcard.distinct())
        updateCount()
        Logger.i("Loaded $domainCount block rules")
    }

    fun addDomain(domain: String) {
        val rule = DomainValidator.normalize(domain) ?: return
        if (rule.wildcard) {
            if (!wildcardRules.contains(rule.domain)) wildcardRules.add(rule.domain)
        } else {
            blockedDomains.add(rule.domain)
        }
        updateCount()
    }

    fun removeDomain(domain: String) {
        val rule = DomainValidator.normalize(domain) ?: return
        blockedDomains.remove(rule.domain)
        wildcardRules.remove(rule.domain)
        whitelistedDomains.remove(rule.domain)
        updateCount()
    }

    fun addWhitelist(domain: String) {
        val rule = DomainValidator.normalize(domain) ?: return
        whitelistedDomains.add(rule.domain)
    }

    fun isBlocked(rawDomain: String): Boolean {
        val rule = DomainValidator.normalize(rawDomain) ?: return false
        val domain = rule.domain

        if (isWhitelisted(domain)) return false
        if (blockedDomains.contains(domain)) return true

        val parts = domain.split('.')
        for (i in 1 until parts.size - 1) {
            val parent = parts.subList(i, parts.size).joinToString(".")
            if (blockedDomains.contains(parent)) return true
        }

        return WildcardMatcher.matchesAnySuffix(wildcardRules, domain)
    }

    fun getBlockReason(rawDomain: String): String? {
        val rule = DomainValidator.normalize(rawDomain) ?: return null
        val domain = rule.domain
        if (blockedDomains.contains(domain)) return "Direct match: $domain"

        val parts = domain.split('.')
        for (i in 1 until parts.size - 1) {
            val parent = parts.subList(i, parts.size).joinToString(".")
            if (blockedDomains.contains(parent)) return "Parent domain: $parent"
        }

        val wildcard = wildcardRules.firstOrNull { WildcardMatcher.matchesSuffix(it, domain) }
        return wildcard?.let { "Wildcard: *.$it" }
    }

    fun clear() {
        blockedDomains.clear()
        wildcardRules.clear()
        whitelistedDomains.clear()
        domainCount = 0
    }

    private fun isWhitelisted(domain: String): Boolean {
        if (whitelistedDomains.contains(domain)) return true
        val parts = domain.split('.')
        for (i in 1 until parts.size - 1) {
            if (whitelistedDomains.contains(parts.subList(i, parts.size).joinToString("."))) return true
        }
        return false
    }

    private fun updateCount() {
        domainCount = blockedDomains.size + wildcardRules.size
    }
}
