package com.noichhar.app.util

import com.noichhar.app.util.Constants.DnsProtocol
import com.noichhar.app.util.Constants.DnsProvider
import java.net.URI

object DnsEndpointValidator {

    fun isValid(provider: DnsProvider, protocol: DnsProtocol, rawValue: String): Boolean {
        if (provider != DnsProvider.CUSTOM) return true
        val value = rawValue.trim()
        if (value.isBlank()) return false
        return when (protocol) {
            DnsProtocol.DOH -> isValidHttpsUrl(value)
            DnsProtocol.DOT -> {
                !value.startsWith("http://", true) &&
                    !value.startsWith("https://", true) &&
                    DomainValidator.normalize(endpointHost(value)) != null &&
                    !isIpLiteral(endpointHost(value))
            }
            DnsProtocol.PLAIN -> isIpLiteral(value)
        }
    }

    fun endpointHint(protocol: DnsProtocol): Int = when (protocol) {
        DnsProtocol.DOH -> com.noichhar.app.R.string.custom_endpoint_doh_hint
        DnsProtocol.DOT -> com.noichhar.app.R.string.custom_endpoint_dot_hint
        DnsProtocol.PLAIN -> com.noichhar.app.R.string.custom_endpoint_plain_hint
    }

    private fun isValidHttpsUrl(value: String): Boolean {
        if (!value.startsWith("https://", ignoreCase = true)) return false
        return runCatching {
            val uri = URI(value)
            uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
        }.getOrDefault(false)
    }

    private fun endpointHost(value: String): String {
        var host = value.trim().removePrefix("tls://").substringBefore('/').trim()
        if (host.startsWith('[')) return host.substringAfter('[').substringBefore(']')
        if (host.count { it == ':' } == 1) host = host.substringBefore(':')
        return host
    }

    private fun isIpLiteral(value: String): Boolean {
        val ipv4 = Regex("^(\\d{1,3}\\.){3}\\d{1,3}$")
        if (ipv4.matches(value)) {
            return value.split('.').all { it.toIntOrNull()?.let { n -> n in 0..255 } == true }
        }
        return value.contains(':') && value.all {
            it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' || it == '[' || it == ']'
        }
    }
}
