package com.noichhar.app.dns

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DnsCacheTest {

    @Test
    fun storesAndReturnsCopies() {
        val cache = DnsCache()
        val response = byteArrayOf(1, 2, 3)
        cache.put("Example.COM", 1, response)
        response[0] = 9
        assertArrayEquals(byteArrayOf(1, 2, 3), cache.get("example.com", 1))
        assertEquals(1, cache.size())
    }

    @Test
    fun expiredEntryIsRemoved() {
        val cache = DnsCache()
        cache.put("expired.example", 1, byteArrayOf(1), ttlSeconds = -1)
        assertNull(cache.get("expired.example", 1))
        assertEquals(0, cache.size())
    }
}
