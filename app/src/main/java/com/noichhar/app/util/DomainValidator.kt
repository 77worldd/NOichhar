package com.noichhar.app.util

import java.net.IDN

object DomainValidator {

    data class DomainRule(val domain: String, val wildcard: Boolean)

    fun normalize(input: String): DomainRule? {
        var value = input.trim().lowercase()
            .substringBefore('#')
            .substringBefore('/')
            .trim()
        if (value.isBlank() || value.startsWith("#")) return null
        if (value.startsWith("||")) value = value.removePrefix("||").substringBefore('^')
        if (value.startsWith("https://") || value.startsWith("http://") || value.startsWith("tls://")) return null

        val wildcard = value.startsWith("*.")
        if (wildcard) value = value.removePrefix("*.")
        value = value.removeSuffix(".").trim()
        if (value.isBlank() || value.length > 253 || value.contains(' ') || value.contains('*')) return null

        val ascii = runCatching { IDN.toASCII(value).lowercase() }.getOrNull() ?: return null
        val labels = ascii.split('.')
        if (labels.size < 2 || labels.any { label ->
                label.isBlank() ||
                    label.length > 63 ||
                    label.startsWith('-') ||
                    label.endsWith('-') ||
                    !label.all { it.isLetterOrDigit() || it == '-' || it == '_' }
            }
        ) return null
        return DomainRule(ascii, wildcard)
    }

    fun display(rule: DomainRule): String = if (rule.wildcard) "*.${rule.domain}" else rule.domain
}
