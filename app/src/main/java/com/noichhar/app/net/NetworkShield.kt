package com.noichhar.app.net

import android.content.Context
import com.noichhar.app.R
import com.noichhar.app.blocker.BlocklistManager
import com.noichhar.app.util.Constants
import com.noichhar.app.util.DomainValidator
import com.noichhar.app.util.IpSafety
import com.noichhar.app.util.Logger
import com.noichhar.app.util.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.InetAddress

class NetworkShield(
    private val context: Context,
    private val preferences: PreferenceManager,
    private val blocklistManager: BlocklistManager
) {
    @Volatile
    private var domainsByIp: Map<String, String> = emptyMap()

    fun blockedIps(): Set<String> =
        if (preferences.advancedProtection && preferences.protectionMode.domainFiltering) preferences.networkShieldIps else emptySet()

    fun domainFor(ip: String): String? = domainsByIp[ip]

    suspend fun refreshIfStale(force: Boolean = false): Set<String> = withContext(Dispatchers.IO) {
        if (!preferences.advancedProtection || !preferences.protectionMode.domainFiltering) return@withContext emptySet()

        val cached = preferences.networkShieldIps
        val lastUpdated = preferences.networkShieldUpdated
        val fresh = lastUpdated > 0 &&
            System.currentTimeMillis() - lastUpdated < Constants.NETWORK_SHIELD_REFRESH_INTERVAL_MS
        if (!force && fresh && cached.isNotEmpty()) {
            rebuildDomainMap(cached)
            return@withContext cached
        }

        val domains = runCatching { blocklistManager.getShieldDomains(1000) }.getOrDefault(emptyList())
        if (domains.isEmpty()) return@withContext cached

        val result = withTimeoutOrNull(Constants.NETWORK_SHIELD_RESOLVE_TIMEOUT_MS) {
            val resolved = resolveIps(domains)
            val essentialIps = resolveIps(readEssentialDomains()).keys
            val safe = LinkedHashSet<String>(Constants.MAX_NETWORK_SHIELD_ROUTES)
            val map = LinkedHashMap<String, String>()
            for ((ip, domain) in resolved) {
                if (ip in essentialIps) continue
                if (!IpSafety.isSafeToBlock(ip)) continue
                safe += ip
                map[ip] = domain
                if (safe.size >= Constants.MAX_NETWORK_SHIELD_ROUTES) break
            }
            safe to map
        }

        if (result == null || result.first.isEmpty()) {
            Logger.w("Network shield refresh timed out or produced no safe routes")
            rebuildDomainMap(cached)
            return@withContext cached
        }

        preferences.networkShieldIps = result.first
        preferences.networkShieldUpdated = System.currentTimeMillis()
        domainsByIp = result.second
        Logger.i("Network shield ready with ${result.first.size} protected routes")
        result.first
    }

    private suspend fun resolveIps(domains: List<String>): Map<String, String> = coroutineScope {
        val semaphore = Semaphore(32)
        val result = LinkedHashMap<String, String>()
        domains.map { domain ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    runCatching {
                        InetAddress.getAllByName(domain)
                            .filterIsInstance<Inet4Address>()
                            .mapNotNull { it.hostAddress }
                            .filter { IpSafety.isSafeToBlock(it) }
                            .forEach { ip -> synchronized(result) { result.putIfAbsent(ip, domain) } }
                    }
                }
            }
        }.awaitAll()
        result
    }

    private fun rebuildDomainMap(ips: Set<String>) {
        if (domainsByIp.isNotEmpty()) return
        domainsByIp = ips.associateWith { "Known ad or DNS-bypass server" }
    }

    /**
     * Route set from the last successful refresh, ready without any network I/O.
     * Using it lets the tunnel come up immediately; [refreshIfStale] then warms the
     * cache in the background for the next activation.
     */
    fun cachedRoutes(): Set<String> {
        val ips = blockedIps()
        rebuildDomainMap(ips)
        return ips
    }

    private fun readEssentialDomains(): List<String> {
        val domains = LinkedHashSet<String>()
        runCatching {
            context.resources.openRawResource(R.raw.essential_allowlist).use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).useLines { lines ->
                    lines.forEach { line ->
                        if (line.isBlank() || line.trim().startsWith("#")) return@forEach
                        val rule = DomainValidator.normalize(line) ?: return@forEach
                        domains += rule.domain
                    }
                }
            }
        }
        return domains.toList()
    }
}
