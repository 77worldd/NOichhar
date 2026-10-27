package com.noichhar.app.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TcpDnsSessionTest {
    private val query = ByteArray(24) { it.toByte() }
    private val wire = byteArrayOf(0, 24) + query

    @Test fun assemblesLengthPrefixAndBodyAcrossSegments() {
        val session = TcpDnsSession(nextSequence = 99, initialClientSequence = 1000)
        assertNull(session.receive(1000, wire.copyOfRange(0, 1)).query)
        assertEquals(1001L, session.clientNextSequence)
        assertNull(session.receive(1001, wire.copyOfRange(1, 8)).query)
        assertEquals(1008L, session.clientNextSequence)
        assertArrayEquals(query, session.receive(1008, wire.copyOfRange(8, wire.size)).query)
        assertEquals(1026L, session.clientNextSequence)
        assertNull(session.receive(1008, wire.copyOfRange(8, wire.size)).query)
    }

    @Test fun acknowledgesRetransmitsAndWaitsForOutOfOrderGaps() {
        val session = TcpDnsSession(99, 2000)
        session.receive(2000, wire.copyOfRange(0, 4))
        assertNull(session.receive(2010, wire.copyOfRange(10, wire.size)).query)
        assertEquals(2004L, session.clientNextSequence)
        assertNull(session.receive(2000, wire.copyOfRange(0, 4)).query)
        assertEquals(2004L, session.clientNextSequence)
        assertArrayEquals(query, session.receive(2002, wire.copyOfRange(2, wire.size)).query)
        assertEquals(2026L, session.clientNextSequence)
    }

    @Test fun handlesSequenceWraparoundAndRejectsInvalidLengths() {
        val session = TcpDnsSession(99, 0xFFFFFFFEL)
        assertNull(session.receive(0xFFFFFFFEL, wire.copyOfRange(0, 2)).query)
        assertEquals(0L, session.clientNextSequence)
        assertArrayEquals(query, session.receive(0, wire.copyOfRange(2, wire.size)).query)
        val bad = TcpDnsSession(99, 1)
        assertTrue(bad.receive(1, byteArrayOf(0, 5)).invalid)
        val large = TcpDnsSession(99, 1)
        assertTrue(large.receive(1, byteArrayOf(0x10, 0x01)).invalid)
        assertFalse(TcpDnsSession(99, 1).receive(1, byteArrayOf(0)).invalid)
    }
}
