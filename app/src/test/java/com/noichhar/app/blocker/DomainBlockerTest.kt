package com.noichhar.app.blocker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainBlockerTest {

    @Test
    fun blocksExactDomainAndSubdomains() {
        val blocker = DomainBlocker()
        blocker.loadDomains(listOf("ads.example.com"))
        assertTrue(blocker.isBlocked("ads.example.com"))
        assertTrue(blocker.isBlocked("pixel.ads.example.com"))
        assertFalse(blocker.isBlocked("example.com"))
        assertFalse(blocker.isBlocked("notads.example.com"))
    }

    @Test
    fun wildcardRespectsDomainBoundary() {
        val blocker = DomainBlocker()
        blocker.loadDomains(listOf("*.tracker.test"))
        assertTrue(blocker.isBlocked("a.tracker.test"))
        assertTrue(blocker.isBlocked("tracker.test"))
        assertFalse(blocker.isBlocked("eviltracker.test"))
    }

    @Test
    fun whitelistOverridesBlocklist() {
        val blocker = DomainBlocker()
        blocker.loadDomains(listOf("ads.example.com"))
        blocker.addWhitelist("safe.ads.example.com")
        assertFalse(blocker.isBlocked("safe.ads.example.com"))
    }

    @Test
    fun explicitAllowRulesOverrideDownloadedRules() {
        val blocker = DomainBlocker()
        blocker.loadRules(
            listOf(
                BlockRule("ads.example.com"),
                BlockRule("*.tracker.test", wildcard = true),
                BlockRule("safe.ads.example.com", allow = true)
            )
        )
        assertTrue(blocker.isBlocked("ads.example.com"))
        assertTrue(blocker.isBlocked("pixel.tracker.test"))
        assertFalse(blocker.isBlocked("safe.ads.example.com"))
    }

    @Test
    fun userBlockRuleOverridesAllowlist() {
        val blocker = DomainBlocker()
        blocker.loadRules(listOf(BlockRule("allowed.example.com", allow = true)))
        assertFalse(blocker.isBlocked("allowed.example.com"))

        blocker.addDomain("allowed.example.com")

        assertTrue(blocker.isBlocked("allowed.example.com"))
    }
}
