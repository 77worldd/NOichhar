package com.noichhar.app.blocker

import java.util.BitSet
import kotlin.math.ln
import kotlin.math.roundToInt

/** A negative lookup accelerator. A positive result must always be checked against the set. */
class BloomFilterBlocklist(expectedEntries: Int, falsePositiveRate: Double = 0.01) {
    private val bitCount: Int
    private val hashCount: Int
    private val bits: BitSet

    init {
        require(expectedEntries >= 0)
        require(falsePositiveRate > 0.0 && falsePositiveRate < 1.0)
        val size = expectedEntries.coerceAtLeast(1)
        bitCount = (-(size * ln(falsePositiveRate)) / (ln(2.0) * ln(2.0)))
            .roundToInt().coerceAtLeast(64)
        hashCount = ((bitCount.toDouble() / size) * ln(2.0)).roundToInt().coerceIn(1, 16)
        bits = BitSet(bitCount)
    }

    fun add(value: String) = forEachIndex(value) { bits.set(it) }

    fun mightContain(value: String): Boolean {
        var present = true
        forEachIndex(value) { if (!bits[it]) present = false }
        return present
    }

    private inline fun forEachIndex(value: String, block: (Int) -> Unit) {
        // Stable hashes, independent of Java's process state. Double hashing keeps
        // lookup cost proportional to the number of hashes, not the list size.
        var h1 = -3750763034362895579L
        var h2 = 1125899906842597L
        for (ch in value) {
            h1 = (h1 xor ch.code.toLong()) * 1099511628211L
            h2 = 31L * h2 + ch.code
        }
        for (i in 0 until hashCount) {
            val mixed = h1 + i.toLong() * h2
            block(((mixed and Long.MAX_VALUE) % bitCount).toInt())
        }
    }
}
