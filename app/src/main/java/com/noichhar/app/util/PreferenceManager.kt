package com.noichhar.app.util

import android.content.Context
import android.content.SharedPreferences
import com.noichhar.app.util.Constants.DnsProtocol
import com.noichhar.app.util.Constants.DnsProvider

class PreferenceManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)

    private inline fun <reified T : Enum<T>> readEnum(key: String, fallback: T): T {
        val stored = prefs.getString(key, null) ?: return fallback
        return runCatching { enumValueOf<T>(stored) }.getOrDefault(fallback)
    }

    var vpnEnabled: Boolean
        get() = prefs.getBoolean(Constants.PREF_VPN_ENABLED, false)
        set(value) = prefs.edit().putBoolean(Constants.PREF_VPN_ENABLED, value).apply()

    var dnsProvider: DnsProvider
        get() = readEnum(Constants.PREF_DNS_PROVIDER, DnsProvider.CLOUDFLARE)
        set(value) = prefs.edit().putString(Constants.PREF_DNS_PROVIDER, value.name).apply()

    var dnsProtocol: DnsProtocol
        get() = readEnum(Constants.PREF_DNS_PROTOCOL, DnsProtocol.DOH)
        set(value) = prefs.edit().putString(Constants.PREF_DNS_PROTOCOL, value.name).apply()

    var customDnsUrl: String
        get() = prefs.getString(Constants.PREF_CUSTOM_DNS_URL, "") ?: ""
        set(value) = prefs.edit().putString(Constants.PREF_CUSTOM_DNS_URL, value.trim()).apply()

    var customDnsName: String
        get() = prefs.getString(Constants.PREF_CUSTOM_DNS_NAME, "") ?: ""
        set(value) = prefs.edit().putString(Constants.PREF_CUSTOM_DNS_NAME, value.trim()).apply()

    var startOnBoot: Boolean
        get() = prefs.getBoolean(Constants.PREF_START_ON_BOOT, false)
        set(value) = prefs.edit().putBoolean(Constants.PREF_START_ON_BOOT, value).apply()

    var enableLogs: Boolean
        get() = prefs.getBoolean(Constants.PREF_ENABLE_LOGS, true)
        set(value) = prefs.edit().putBoolean(Constants.PREF_ENABLE_LOGS, value).apply()

    var darkMode: Boolean
        get() = prefs.getBoolean(Constants.PREF_DARK_MODE, false)
        set(value) = prefs.edit().putBoolean(Constants.PREF_DARK_MODE, value).apply()

    var blockUnknown: Boolean
        get() = prefs.getBoolean(Constants.PREF_BLOCK_UNKNOWN, false)
        set(value) = prefs.edit().putBoolean(Constants.PREF_BLOCK_UNKNOWN, value).apply()

    var blockNewApps: Boolean
        get() = prefs.getBoolean(Constants.PREF_BLOCK_NEW_APPS, false)
        set(value) = prefs.edit().putBoolean(Constants.PREF_BLOCK_NEW_APPS, value).apply()

    var lockdownMode: Boolean
        get() = prefs.getBoolean(Constants.PREF_LOCKDOWN_MODE, false)
        set(value) = prefs.edit().putBoolean(Constants.PREF_LOCKDOWN_MODE, value).apply()

    var lastBlocklistUpdate: Long
        get() = prefs.getLong(Constants.PREF_LAST_BLOCKLIST_UPDATE, 0L)
        set(value) = prefs.edit().putLong(Constants.PREF_LAST_BLOCKLIST_UPDATE, value).apply()

    fun saveDnsSettings(
        provider: DnsProvider,
        protocol: DnsProtocol,
        customUrl: String,
        customName: String = ""
    ) {
        prefs.edit()
            .putString(Constants.PREF_DNS_PROVIDER, provider.name)
            .putString(Constants.PREF_DNS_PROTOCOL, protocol.name)
            .putString(Constants.PREF_CUSTOM_DNS_URL, customUrl.trim())
            .putString(Constants.PREF_CUSTOM_DNS_NAME, customName.trim())
            .apply()
    }

    fun getDohUrl(): String = when (dnsProvider) {
        DnsProvider.CLOUDFLARE -> Constants.DNS_CLOUDFLARE
        DnsProvider.GOOGLE -> Constants.DNS_GOOGLE
        DnsProvider.QUAD9 -> Constants.DNS_QUAD9
        DnsProvider.ADGUARD -> Constants.DNS_ADGUARD
        DnsProvider.CUSTOM -> customDnsUrl.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }.orEmpty()
        DnsProvider.SYSTEM -> ""
    }

    fun getDotHostname(): String {
        val custom = endpointHost(customDnsUrl)
        return when (dnsProvider) {
            DnsProvider.CLOUDFLARE -> Constants.DOT_CLOUDFLARE
            DnsProvider.GOOGLE -> Constants.DOT_GOOGLE
            DnsProvider.QUAD9 -> Constants.DOT_QUAD9
            DnsProvider.ADGUARD -> Constants.DOT_ADGUARD
            DnsProvider.CUSTOM -> custom.takeIf { it.isNotBlank() && !isIpLiteral(it) }.orEmpty()
            DnsProvider.SYSTEM -> ""
        }
    }

    fun getPlainDnsIp(): String {
        val customHost = endpointHost(customDnsUrl)
        return when (dnsProvider) {
            DnsProvider.CLOUDFLARE -> Constants.DNS_CLOUDFLARE_IP
            DnsProvider.GOOGLE -> Constants.DNS_GOOGLE_IP
            DnsProvider.QUAD9 -> Constants.DNS_QUAD9_IP
            DnsProvider.ADGUARD -> Constants.DNS_ADGUARD_IP
            DnsProvider.CUSTOM -> customHost.takeIf { isIpLiteral(it) }.orEmpty()
            DnsProvider.SYSTEM -> ""
        }
    }

    fun getDnsIp(): String = getPlainDnsIp().ifBlank { Constants.FALLBACK_DNS_IP }

    fun getDnsUrl(): String = getDohUrl()

    fun getDnsSummary(): String {
        val endpoint = when (dnsProtocol) {
            DnsProtocol.DOH -> getDohUrl().ifBlank { "System DNS" }
            DnsProtocol.DOT -> getDotHostname().ifBlank { "System DNS" }
            DnsProtocol.PLAIN -> getPlainDnsIp().ifBlank { "System DNS" }
        }
        return "$endpoint • ${dnsProtocol.name}"
    }

    fun customEndpointIsValidFor(protocol: DnsProtocol): Boolean {
        if (dnsProvider != DnsProvider.CUSTOM) return true
        val value = customDnsUrl.trim()
        if (value.isBlank()) return false
        return when (protocol) {
            DnsProtocol.DOH -> value.startsWith("https://", true)
            DnsProtocol.DOT -> {
                val host = endpointHost(value)
                host.isNotBlank() && !isIpLiteral(host) && !value.startsWith("https://", true)
            }
            DnsProtocol.PLAIN -> isIpLiteral(endpointHost(value))
        }
    }

    private fun endpointHost(value: String): String {
        var host = value.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("tls://")
            .substringBefore('/')
            .substringBefore('#')
            .trim()
        if (host.startsWith('[')) {
            host = host.substringAfter('[').substringBefore(']')
        } else if (host.count { it == ':' } == 1) {
            host = host.substringBefore(':')
        }
        return host
    }

    private fun isIpLiteral(value: String): Boolean {
        if (value.isBlank()) return false
        val ipv4 = Regex("^(\\d{1,3}\\.){3}\\d{1,3}$")
        if (ipv4.matches(value)) {
            return value.split('.').all { it.toIntOrNull()?.let { n -> n in 0..255 } == true }
        }
        return value.contains(':') && value.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == ':' }
    }
}
