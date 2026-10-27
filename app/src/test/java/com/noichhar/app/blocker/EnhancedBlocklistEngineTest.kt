package com.noichhar.app.blocker

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class EnhancedBlocklistEngineTest {
    @Test fun bloomFilterHasNoFalseNegatives() {
        val bloom = BloomFilterBlocklist(25_000)
        repeat(25_000) { bloom.add("ads$it.example.test") }
        repeat(25_000) { assertTrue(bloom.mightContain("ads$it.example.test")) }
    }

    @Test fun handlesHalfAMillionRulesAndExceptions() {
        val engine = EnhancedBlocklistEngine()
        val rules = ArrayList<BlockRule>(500_001)
        repeat(500_000) { rules.add(BlockRule("ads$it.example.test")) }
        rules.add(BlockRule("safe.ads499999.example.test", allow = true))
        engine.loadRules(rules)
        assertEquals(500_000, engine.domainCount)
        assertTrue(engine.isBlocked("pixel.ads499999.example.test"))
        assertFalse(engine.isBlocked("safe.ads499999.example.test"))
        assertFalse(engine.isBlocked("notads499999.example.test"))
    }

    @Test fun reloadsPublishAtomicallyToReaders() {
        val engine = EnhancedBlocklistEngine()
        val first = (0 until 1_000).map { BlockRule("ads$it.example.test") } + BlockRule("always.blocked.test")
        val second = (0 until 1_000).map { BlockRule("track$it.example.test") } + BlockRule("always.blocked.test")
        engine.loadRules(first)
        val failed = AtomicBoolean(false)
        val executor = Executors.newFixedThreadPool(2)
        val reader = executor.submit { repeat(20_000) { if (!engine.isBlocked("always.blocked.test")) failed.set(true) } }
        val writer = executor.submit { repeat(20) { engine.loadRules(if (it % 2 == 0) second else first) } }
        reader.get(60, TimeUnit.SECONDS); writer.get(60, TimeUnit.SECONDS); executor.shutdownNow()
        assertFalse("A reader observed a partially reloaded blocklist", failed.get())
    }
}
