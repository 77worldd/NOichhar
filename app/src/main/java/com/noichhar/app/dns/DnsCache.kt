package com.noichhar.app.dns

import com.noichhar.app.util.Constants
import java.util.concurrent.ConcurrentHashMap

/**
 * High-performance DNS cache with TTL respect and LRU-style eviction.
 * Sized at 2000 entries per spec to minimize upstream lookups and browsing lag.
 */
class DnsCache {
    data class CacheEntry(
        val response: ByteArray,
        val expireTime: Long
    )

    // ConcurrentHashMap for lock-free reads on the hot packet path
    private val cache = ConcurrentHashMap<String, CacheEntry>()

    fun get(domain: String, type: Int): ByteArray? {
        val key = key(domain, type)
        val entry = cache[key] ?: return null
        if (System.currentTimeMillis() >= entry.expireTime) {
            cache.remove(key)
            return null
        }
        return entry.response.copyOf()
    }

    fun put(
        domain: String,
        type: Int,
        response: ByteArray,
        ttlSeconds: Long = Constants.DNS_CACHE_TTL_SECONDS
    ) {
        val key = key(domain, type)
        if (cache.size >= Constants.DNS_CACHE_SIZE) {
            val now = System.currentTimeMillis()
            cache.entries.removeIf { it.value.expireTime < now }
            if (cache.size >= Constants.DNS_CACHE_SIZE) {
                cache.entries.minByOrNull { it.value.expireTime }?.let { cache.remove(it.key) }
            }
        }

        val effectiveTtl = if (ttlSeconds == Constants.DNS_CACHE_TTL_SECONDS) {
            DnsPacketParser.getMinAnswerTtlSeconds(response)
                ?.coerceIn(Constants.MIN_DNS_CACHE_TTL_SECONDS, Constants.MAX_DNS_CACHE_TTL_SECONDS)
                ?: ttlSeconds
        } else {
            ttlSeconds
        }.coerceAtLeast(0L)

        cache[key] = CacheEntry(response.copyOf(), System.currentTimeMillis() + effectiveTtl * 1000L)
    }

    fun clear() {
        cache.clear()
    }

    fun size(): Int = cache.size

    private fun key(domain: String, type: Int): String = "${domain.trim().lowercase()}:$type"
}
