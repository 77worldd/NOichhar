package com.noichhar.app.blocker

import com.noichhar.app.util.DomainValidator

/**
 * Lock-free reads against an immutable, atomically swapped rules snapshot.
 * Suffixes are checked at label boundaries. User allows win over user blocks;
 * user blocks win over downloaded exceptions; downloaded allows win over lists.
 * Bloom positives are always confirmed in the relevant set (no false blocks).
 */
open class EnhancedBlocklistEngine {
    private data class Snapshot(
        val exact: Set<String> = emptySet(),
        val wildcards: Set<String> = emptySet(),
        val allowExact: Set<String> = emptySet(),
        val allowWildcards: Set<String> = emptySet(),
        val userExact: Set<String> = emptySet(),
        val userWildcards: Set<String> = emptySet(),
        val userAllowExact: Set<String> = emptySet(),
        val userAllowWildcards: Set<String> = emptySet(),
        val bloom: BloomFilterBlocklist = BloomFilterBlocklist(0)
    )

    @Volatile private var snapshot = Snapshot()
    val domainCount: Int get() = snapshot.let { it.exact.size + it.wildcards.size + it.userExact.size + it.userWildcards.size }

    fun loadDomains(domains: List<String>) = loadRules(domains.map { BlockRule(it) })

    @Synchronized
    fun loadRules(rules: Collection<BlockRule>) {
        val exact = HashSet<String>(rules.size)
        val wildcards = HashSet<String>()
        val allows = HashSet<String>()
        val allowWildcards = HashSet<String>()
        val userExact = HashSet<String>()
        val userWildcards = HashSet<String>()
        val userAllows = HashSet<String>()
        val userAllowWildcards = HashSet<String>()
        rules.forEach { rule ->
            val normalized = DomainValidator.normalize(rule.domain) ?: return@forEach
            val wildcard = rule.wildcard || normalized.wildcard
            when {
                rule.user && rule.allow && wildcard -> userAllowWildcards.add(normalized.domain)
                rule.user && rule.allow -> userAllows.add(normalized.domain)
                rule.user && wildcard -> userWildcards.add(normalized.domain)
                rule.user -> userExact.add(normalized.domain)
                rule.allow && wildcard -> allowWildcards.add(normalized.domain)
                rule.allow -> allows.add(normalized.domain)
                wildcard -> wildcards.add(normalized.domain)
                else -> exact.add(normalized.domain)
            }
        }
        publish(Snapshot(exact, wildcards, allows, allowWildcards,
            userExact, userWildcards, userAllows, userAllowWildcards))
    }

    @Synchronized
    fun addDomain(domain: String) {
        val rule = DomainValidator.normalize(domain) ?: return
        val old = snapshot
        publish(old.copy(
            exact = old.exact - rule.domain, wildcards = old.wildcards - rule.domain,
            allowExact = old.allowExact - rule.domain, allowWildcards = old.allowWildcards - rule.domain,
            userExact = if (rule.wildcard) old.userExact else old.userExact + rule.domain,
            userWildcards = if (rule.wildcard) old.userWildcards + rule.domain else old.userWildcards,
            userAllowExact = old.userAllowExact - rule.domain,
            userAllowWildcards = old.userAllowWildcards - rule.domain
        ))
    }

    @Synchronized
    fun removeDomain(domain: String) {
        val rule = DomainValidator.normalize(domain) ?: return
        val old = snapshot
        publish(old.copy(exact = old.exact - rule.domain, wildcards = old.wildcards - rule.domain,
            allowExact = old.allowExact - rule.domain, allowWildcards = old.allowWildcards - rule.domain,
            userExact = old.userExact - rule.domain, userWildcards = old.userWildcards - rule.domain,
            userAllowExact = old.userAllowExact - rule.domain, userAllowWildcards = old.userAllowWildcards - rule.domain))
    }

    fun addWhitelist(domain: String) = addAllowRule(domain, includeSubdomains = true)

    @Synchronized
    fun addAllowRule(domain: String, includeSubdomains: Boolean = false) {
        val rule = DomainValidator.normalize(domain) ?: return
        val old = snapshot
        publish(old.copy(
            userAllowExact = if (rule.wildcard || includeSubdomains) old.userAllowExact else old.userAllowExact + rule.domain,
            userAllowWildcards = if (rule.wildcard || includeSubdomains) old.userAllowWildcards + rule.domain else old.userAllowWildcards
        ))
    }

    fun isBlocked(rawDomain: String): Boolean = getBlockReason(rawDomain) != null

    fun getBlockReason(rawDomain: String): String? {
        val domain = DomainValidator.normalize(rawDomain)?.domain ?: return null
        val rules = snapshot
        val suffixes = suffixes(domain)
        if (domain in rules.userAllowExact || suffixes.any { it in rules.userAllowWildcards }) return null
        findMatch(suffixes, rules.userExact, rules.userWildcards, rules.bloom)?.let { return it }
        if (domain in rules.allowExact || suffixes.any { it in rules.allowWildcards }) return null
        return findMatch(suffixes, rules.exact, rules.wildcards, rules.bloom)
    }

    private fun findMatch(suffixes: List<String>, exact: Set<String>, wildcards: Set<String>, bloom: BloomFilterBlocklist): String? {
        if (exact.isEmpty() && wildcards.isEmpty()) return null
        suffixes.forEachIndexed { index, suffix ->
            if (!bloom.mightContain(suffix)) return@forEachIndexed
            if (suffix in exact) return if (index == 0) "Direct match: $suffix" else "Parent domain: $suffix"
            if (suffix in wildcards) return "Wildcard: *.$suffix"
        }
        return null
    }

    fun blockedRuleCount(): Int = domainCount
    fun allowRuleCount(): Int = snapshot.let { it.allowExact.size + it.allowWildcards.size + it.userAllowExact.size + it.userAllowWildcards.size }
    @Synchronized fun clear() { snapshot = Snapshot() }

    private fun publish(next: Snapshot) {
        val bloom = BloomFilterBlocklist(next.exact.size + next.wildcards.size + next.userExact.size + next.userWildcards.size)
        next.exact.forEach(bloom::add); next.wildcards.forEach(bloom::add)
        next.userExact.forEach(bloom::add); next.userWildcards.forEach(bloom::add)
        snapshot = next.copy(bloom = bloom)
    }

    private fun suffixes(domain: String): List<String> {
        val result = ArrayList<String>(4)
        var value = domain
        while (value.contains('.')) { result.add(value); value = value.substringAfter('.') }
        return result
    }
}
