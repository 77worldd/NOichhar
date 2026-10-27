package com.noichhar.app.util

object Constants {
    const val NOTIFICATION_CHANNEL_ID = "noichhar_vpn_channel"
    const val NOTIFICATION_ID = 1337
    const val VPN_REQUEST_CODE = 100
    const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001

    const val DNS_CLOUDFLARE = "https://cloudflare-dns.com/dns-query"
    const val DNS_GOOGLE = "https://dns.google/dns-query"
    const val DNS_QUAD9 = "https://dns.quad9.net/dns-query"
    const val DNS_ADGUARD = "https://dns.adguard-dns.com/dns-query"

    const val DNS_CLOUDFLARE_IP = "1.1.1.1"
    const val DNS_GOOGLE_IP = "8.8.8.8"
    const val DNS_QUAD9_IP = "9.9.9.9"
    const val DNS_ADGUARD_IP = "94.140.14.14"
    const val FALLBACK_DNS_IP = "1.1.1.1"

    const val DOT_CLOUDFLARE = "one.one.one.one"
    const val DOT_GOOGLE = "dns.google"
    const val DOT_QUAD9 = "dns.quad9.net"
    const val DOT_ADGUARD = "dns.adguard-dns.com"

    const val VPN_MTU = 1500
    const val VPN_INTERFACE_ADDRESS = "10.111.222.1"
    const val VPN_DNS_ADDRESS = "10.111.222.3"
    const val VPN_ROUTE = "0.0.0.0"
    const val VPN_INTERFACE_NAME = "noichhar0"

    const val DNS_PORT = 53
    const val DOH_PORT = 443
    const val DOT_PORT = 853
    const val HTTP_PORT = 80
    const val HTTPS_PORT = 443
    const val LOCAL_PROXY_ADDRESS = "127.0.0.1"
    const val LOCAL_PROXY_PORT = 8765
    const val DNS_UDP_BUFFER_SIZE = 1500
    const val DNS_TIMEOUT_MS = 5000
    const val PROXY_CONNECT_TIMEOUT_MS = 10_000
    const val PROXY_HEADER_LIMIT = 16 * 1024
    const val PROXY_BUFFER_SIZE = 32 * 1024

    const val MAX_LOG_ENTRIES = 10000
    const val DNS_CACHE_SIZE = 2000
    const val DNS_CACHE_TTL_SECONDS = 300L
    const val MIN_DNS_CACHE_TTL_SECONDS = 15L
    const val MAX_DNS_CACHE_TTL_SECONDS = 3600L
    const val BLOCKLIST_UPDATE_INTERVAL_MS = 12 * 60 * 60 * 1000L
    const val NETWORK_SHIELD_REFRESH_INTERVAL_MS = 12 * 60 * 60 * 1000L
    const val MAX_BLOCKLIST_ENTRIES = 180_000
    const val MAX_ALLOWLIST_ENTRIES = 20_000
    const val MAX_NETWORK_SHIELD_ROUTES = 420
    const val NETWORK_SHIELD_RESOLVE_TIMEOUT_MS = 6_000L

    const val PREF_NAME = "noichhar_prefs"
    const val PREF_VPN_ENABLED = "vpn_enabled"
    const val PREF_DNS_PROVIDER = "dns_provider"
    const val PREF_DNS_PROTOCOL = "dns_protocol"
    const val PREF_CUSTOM_DNS_URL = "custom_dns_url"
    const val PREF_CUSTOM_DNS_NAME = "custom_dns_name"
    const val PREF_START_ON_BOOT = "start_on_boot"
    const val PREF_ENABLE_LOGS = "enable_logs"
    const val PREF_DARK_MODE = "dark_mode"
    const val PREF_BLOCK_UNKNOWN = "block_unknown"
    const val PREF_BLOCK_NEW_APPS = "block_new_apps"
    const val PREF_LOCKDOWN_MODE = "lockdown_mode"
    const val PREF_LAST_BLOCKLIST_UPDATE = "last_blocklist_update"
    const val PREF_ADVANCED_PROTECTION = "advanced_protection"
    const val PREF_BLOCK_DOH_BYPASS = "block_doh_bypass"
    const val PREF_AUTO_UPDATE_BLOCKLISTS = "auto_update_blocklists"
    const val PREF_NETWORK_SHIELD_IPS = "network_shield_ips"
    const val PREF_NETWORK_SHIELD_UPDATED = "network_shield_updated"

    const val WORK_BLOCKLIST_PERIODIC = "noichhar-blocklist-periodic"
    const val WORK_BLOCKLIST_IMMEDIATE = "noichhar-blocklist-immediate"

    val DEFAULT_BLOCKLIST_URLS: List<String> = listOf(
        "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/adblock/pro.mini.txt",
        "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts",
        "https://raw.githubusercontent.com/AdAway/adaway.github.io/master/hosts.txt"
    )

    val PROTECTED_DNS_IPS: Set<String> = setOf(
        "1.1.1.1", "1.0.0.1", "8.8.8.8", "8.8.4.4", "9.9.9.9", "149.112.112.112",
        "94.140.14.14", "94.140.15.15", "208.67.222.222", "208.67.220.220"
    )

    enum class DnsProvider {
        SYSTEM, CLOUDFLARE, GOOGLE, QUAD9, ADGUARD, CUSTOM
    }

    enum class DnsProtocol {
        DOH, DOT, PLAIN
    }

    const val ACTION_START = "com.noichhar.app.START"
    const val ACTION_STOP = "com.noichhar.app.STOP"
    const val ACTION_RELOAD_CONFIG = "com.noichhar.app.RELOAD_CONFIG"
    const val ACTION_REFRESH_APPS = "com.noichhar.app.REFRESH_APPS"
    const val ACTION_VPN_STATE_CHANGED = "com.noichhar.app.VPN_STATE_CHANGED"
    const val EXTRA_VPN_ACTIVE = "vpn_active"
}
