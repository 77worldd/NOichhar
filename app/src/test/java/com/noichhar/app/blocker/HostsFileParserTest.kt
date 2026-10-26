package com.noichhar.app.blocker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HostsFileParserTest {

    @Test
    fun parsesHostsAndPlainDomainFormats() {
        val content = """
            # comment
            0.0.0.0 ads.example.com # inline comment
            127.0.0.1 localhost
            0.0.0.0 tracker.example.org
            plain.example.net
            *.wild.example.com
        """.trimIndent()
        val domains = HostsFileParser().parse(content).toSet()
        assertTrue(domains.contains("ads.example.com"))
        assertTrue(domains.contains("tracker.example.org"))
        assertTrue(domains.contains("plain.example.net"))
        assertTrue(domains.contains("*.wild.example.com"))
        assertEquals(4, domains.size)
    }
}
