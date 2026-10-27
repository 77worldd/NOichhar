package com.noichhar.app.blocker

import com.noichhar.app.util.DomainValidator

/**
 * Lock-free reads against an immutable, atomically swapped rules snapshot. Domain
 * suffixes are checked at label boundaries; allow rules always take precedence.
 * A Bloom filter avoids expensive hash-set probes for most non-blocked domains.
 */
open class EnhancedBlocklistEngine {
    private data class Snapshot(
        val exact: Set<String> = emptySet(),
        val wildcards: Set<String> = emptySet(),
        val allowExact: Set<String> = emptySet(),
        val allowWildcards: Set<String> = emptySet(),
        val bloom: BloomFilterBlocklist = BloomFilterBlocklist(0)
    )

    @Volatile private var snapshot = Snapshot()
    val domainCount: Int get() = snapshot.let { it.exact.size + it.wildcards.size }

    fun loadDomains(domains: List<String>) = loadRules(domains.map { BlockRule(it) })

    @Synchronized
    fun loadRules(rules: Collection<BlockRule>) {
        val exact = HashSet<String>(rules.size)
        val wildcards = HashSet<String>()
        val allows = HashSet<String>()
        val allowWildcards = HashSet<String>()
        rules.forEach { rule ->
            val normalized = DomainValidator.normalize(rule.domain) ?: return@forEach
            when {
                rule.allow && (rule.wildcard || normalized.wildcard) -> allowWildcards.add(normalized.domain)
                rule.allow -> allows.add(normalized.domain)
                rule.wildcard || normalized.wildcard -> wildcards.add(normalized.domain)
                else -> exact.add(normalized.domain)
            }
        }
        publish(exact, wildcards, allows, allowWildcards)
    }

    @Synchronized
    fun addDomain(domain: String) {
        val rule = DomainValidator.normalize(domain) ?: return
        val old = snapshot
        val exact = old.exact.toMutableSet()
        val wildcards = old.wildcards.toMutableSet()
        val allows = old.allowExact.toMutableSet()
        val allowWildcards = old.allowWildcards.toMutableSet()
        if (rule.wildcard) wildcards.add(rule.domain) else exact.add(rule.domain)
        allows.remove(rule.domain)
        allowWildcards.remove(rule.domain)
        publish(exact, wildcards, allows, allowWildcards)
    }

    @Synchronized
    fun removeDomain(domain: String) {
        val rule = DomainValidator.normalize(domain) ?: return
        val old = snapshot
        publish(old.exact - rule.domain, old.wildcards - rule.domain,
            old.allowExact - rule.domain, old.allowWildcards - rule.domain)
    }

    fun addWhitelist(domain: String) = addAllowRule(domain, includeSubdomains = true)

    @Synchronized
    fun addAllowRule(domain: String, includeSubdomains: Boolean = false) {
        val rule = DomainValidator.normalize(domain) ?: return
        val old = snapshot
        if (rule.wildcard || includeSubdomains) {
            publish(old.exact, old.wildcards, old.allowExact, old.allowWildcards + rule.domain)
        } else {
            publish(old.exact, old.wildcards, old.allowExact + rule.domain, old.allowWildcards)
        }
    }

    fun isBlocked(rawDomain: String): Boolean = getBlockReason(rawDomain) != null

    fun getBlockReason(rawDomain: String): String? {
        val domain = DomainValidator.normalize(rawDomain)?.domain ?: return null
        val rules = snapshot
        if (domain in rules.allowExact) return null
        val suffixes = suffixes(domain)
        if (suffixes.any { it in rules.allowWildcards }) return null
        suffixes.forEachIndexed { index, suffix ->
            if (!rules.bloom.mightContain(suffix)) return@forEachIndexed
            if (suffix in rules.exact) {
                return if (index == 0) "Direct match: $suffix" else "Parent domain: $suffix"
            }
            if (suffix in rules.wildcards) return "Wildcard: *.$suffix"
        }
        return null
    }

    fun blockedRuleCount(): Int = domainCount
    fun allowRuleCount(): Int = snapshot.let { it.allowExact.size + it.allowWildcards.size }

    @Synchronized fun clear() { snapshot = Snapshot() }

    private fun publish(exact: Set<String>, wildcards: Set<String>,
                        allows: Set<String>, allowWildcards: Set<String>) {
        val bloom = BloomFilterBlocklist(exact.size + wildcards.size)
        exact.forEach(bloom::add)
        wildcards.forEach(bloom::add)
        snapshot = Snapshot(exact, wildcards, allows, allowWildcards, bloom)
    }

    private fun suffixes(domain: String): List<String> {
        val result = ArrayList<String>(4)
        var value = domain
        while (value.contains('.')) {
            result.add(value)
            value = value.substringAfter('.')
        }
        return result
    }
}
