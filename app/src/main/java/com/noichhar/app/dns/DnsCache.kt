package com.noichhar.app.dns

import com.noichhar.app.util.Constants
import java.util.concurrent.ConcurrentHashMap

class DnsCache {
    data class CacheEntry(
        val response: ByteArray,
        val expireTime: Long
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    fun get(domain: String, type: Int): ByteArray? {
        val key = key(domain, type)
        val entry = cache[key] ?: return null
        if (System.currentTimeMillis() >= entry.expireTime) {
            cache.remove(key)
            return null
        }
        return entry.response
    }

    fun put(domain: String, type: Int, response: ByteArray, ttlSeconds: Long = Constants.DNS_CACHE_TTL_SECONDS) {
        val key = key(domain, type)
        if (cache.size >= Constants.DNS_CACHE_SIZE) {
            val now = System.currentTimeMillis()
            cache.entries.removeIf { it.value.expireTime < now }
            if (cache.size >= Constants.DNS_CACHE_SIZE) {
                cache.entries.minByOrNull { it.value.expireTime }?.let { cache.remove(it.key) }
            }
        }
        cache[key] = CacheEntry(response.copyOf(), nowMillis() + ttlSeconds.coerceAtLeast(0L) * 1000L)
    }

    fun clear() {
        cache.clear()
    }

    fun size(): Int = cache.size

    private fun key(domain: String, type: Int): String = "${domain.trim().lowercase()}:$type"

    private fun nowMillis(): Long = System.currentTimeMillis()
}
