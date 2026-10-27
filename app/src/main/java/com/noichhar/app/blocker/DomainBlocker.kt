package com.noichhar.app.blocker

import com.noichhar.app.util.DomainValidator
import com.noichhar.app.util.Logger
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

data class BlockRule(
    val domain: String,
    val wildcard: Boolean = false,
    val allow: Boolean = false
)

class DomainBlocker {

    private val exactRules: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val wildcardSuffixes: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val exactAllowRules: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val wildcardAllowSuffixes: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    @Volatile
    var domainCount: Int = 0
        private set

    fun loadDomains(domains: List<String>) {
        val rules = domains.mapNotNull { raw ->
            DomainValidator.normalize(raw)?.let { BlockRule(it.domain, it.wildcard) }
        }
        loadRules(rules)
    }

    fun loadRules(rules: Collection<BlockRule>) {
        val exact = HashSet<String>(rules.size)
        val wildcards = HashSet<String>()
        val allowExact = HashSet<String>()
        val allowWildcards = HashSet<String>()

        rules.forEach { rule ->
            val normalized = DomainValidator.normalize(rule.domain) ?: return@forEach
            val value = normalized.domain
            val wildcard = rule.wildcard || normalized.wildcard
            when {
                rule.allow && wildcard -> allowWildcards += value
                rule.allow -> allowExact += value
                wildcard -> wildcards += value
                else -> exact += value
            }
        }

        exactRules.clear()
        exactRules.addAll(exact)
        wildcardSuffixes.clear()
        wildcardSuffixes.addAll(wildcards)
        exactAllowRules.clear()
        exactAllowRules.addAll(allowExact)
        wildcardAllowSuffixes.clear()
        wildcardAllowSuffixes.addAll(allowWildcards)
        updateCount()
        Logger.i("Loaded $domainCount block rules and ${allowExact.size + allowWildcards.size} allow rules")
    }

    fun addDomain(domain: String) {
        val rule = DomainValidator.normalize(domain) ?: return
        if (rule.wildcard) wildcardSuffixes += rule.domain else exactRules += rule.domain
        // A newly added user block rule must win over any list-based allow rule.
        exactAllowRules.remove(rule.domain)
        wildcardAllowSuffixes.remove(rule.domain)
        updateCount()
    }

    fun removeDomain(domain: String) {
        val rule = DomainValidator.normalize(domain) ?: return
        exactRules.remove(rule.domain)
        wildcardSuffixes.remove(rule.domain)
        exactAllowRules.remove(rule.domain)
        wildcardAllowSuffixes.remove(rule.domain)
        updateCount()
    }

    fun addWhitelist(domain: String) {
        val rule = DomainValidator.normalize(domain) ?: return
        exactAllowRules += rule.domain
        // Preserve the original user-facing behaviour: allowing a parent domain also
        // allows its subdomains. Explicit rules loaded from lists stay exact-only.
        if (!rule.wildcard) wildcardAllowSuffixes += rule.domain
    }

    fun addAllowRule(domain: String, includeSubdomains: Boolean = false) {
        val rule = DomainValidator.normalize(domain) ?: return
        if (rule.wildcard || includeSubdomains) {
            wildcardAllowSuffixes += rule.domain
        } else {
            exactAllowRules += rule.domain
        }
    }

    fun isBlocked(rawDomain: String): Boolean {
        val normalized = DomainValidator.normalize(rawDomain) ?: return false
        val domain = normalized.domain
        if (isAllowed(domain)) return false
        if (exactRules.contains(domain)) return true

        val parts = domain.split('.')
        for (i in 1 until parts.size - 1) {
            if (exactRules.contains(parts.subList(i, parts.size).joinToString("."))) return true
        }

        for (i in 0 until parts.size - 1) {
            if (wildcardSuffixes.contains(parts.subList(i, parts.size).joinToString("."))) return true
        }
        return false
    }

    fun getBlockReason(rawDomain: String): String? {
        val normalized = DomainValidator.normalize(rawDomain) ?: return null
        val domain = normalized.domain
        if (isAllowed(domain)) return null
        if (exactRules.contains(domain)) return "Direct match: $domain"

        val parts = domain.split('.')
        for (i in 1 until parts.size - 1) {
            val parent = parts.subList(i, parts.size).joinToString(".")
            if (exactRules.contains(parent)) return "Parent domain: $parent"
        }
        for (i in 0 until parts.size - 1) {
            val suffix = parts.subList(i, parts.size).joinToString(".")
            if (wildcardSuffixes.contains(suffix)) return "Wildcard: *.$suffix"
        }
        return null
    }

    fun blockedRuleCount(): Int = domainCount

    fun allowRuleCount(): Int = exactAllowRules.size + wildcardAllowSuffixes.size

    fun clear() {
        exactRules.clear()
        wildcardSuffixes.clear()
        exactAllowRules.clear()
        wildcardAllowSuffixes.clear()
        domainCount = 0
    }

    private fun isAllowed(domain: String): Boolean {
        if (exactAllowRules.contains(domain)) return true
        val parts = domain.split('.')
        for (i in 0 until parts.size - 1) {
            if (wildcardAllowSuffixes.contains(parts.subList(i, parts.size).joinToString("."))) return true
        }
        return false
    }

    private fun updateCount() {
        domainCount = exactRules.size + wildcardSuffixes.size
    }
}
