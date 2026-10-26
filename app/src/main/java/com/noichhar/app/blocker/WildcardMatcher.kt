package com.noichhar.app.blocker

object WildcardMatcher {

    fun matches(pattern: String, domain: String): Boolean {
        val p = clean(pattern)
        val d = clean(domain)
        if (p == d) return true
        if (p.startsWith("*.")) {
            val suffix = p.removePrefix("*.")
            return d == suffix || d.endsWith(".$suffix")
        }
        return false
    }

    fun matchesAny(patterns: List<String>, domain: String): Boolean =
        patterns.any { matches(it, domain) }

    fun matchesSuffix(suffix: String, domain: String): Boolean {
        val cleanSuffix = clean(suffix)
        val cleanDomain = clean(domain)
        return cleanSuffix.isNotBlank() &&
            (cleanDomain == cleanSuffix || cleanDomain.endsWith(".$cleanSuffix"))
    }

    fun matchesAnySuffix(suffixes: Collection<String>, domain: String): Boolean =
        suffixes.any { matchesSuffix(it, domain) }

    private fun clean(value: String): String =
        value.trim().lowercase().removeSuffix(".")
}
