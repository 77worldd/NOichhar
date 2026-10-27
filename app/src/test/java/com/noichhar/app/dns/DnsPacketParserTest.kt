package com.noichhar.app.dns

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

class DnsPacketParserTest {

    @Test
    fun parsesQuestionAndType() {
        val query = query("ads.example.com", DnsPacketParser.TYPE_A)
        val question = DnsPacketParser.parseQuestion(query)
        assertNotNull(question)
        assertEquals("ads.example.com", question!!.name)
        assertEquals(DnsPacketParser.TYPE_A, question.type)
        assertEquals(1, question.clazz)
        assertTrue(question.endOffset > 12)
    }

    @Test
    fun nxDomainResponseKeepsTransactionIdAndQuestion() {
        val query = query("tracker.example.com", DnsPacketParser.TYPE_AAAA, transactionId = 0xBEEF)
        val response = DnsPacketParser.createNxDomainResponse(query)
        assertEquals(0xBEEF, DnsPacketParser.getTransactionId(response))
        val header = DnsPacketParser.parseHeader(response)
        assertNotNull(header)
        assertEquals(0x8183, header!!.flags)
        assertEquals(1, header.qdCount)
        assertEquals(0, header.anCount)
        assertEquals("tracker.example.com", DnsPacketParser.parseQuestion(response)!!.name)
    }

    @Test
    fun rewriteTransactionIdDoesNotMutateCachedResponse() {
        val response = byteArrayOf(1, 2, 3, 4)
        val query = byteArrayOf(9, 9)
        val rewritten = DnsPacketParser.rewriteTransactionId(response, query)
        assertArrayEquals(byteArrayOf(9, 9, 3, 4), rewritten)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), response)
    }

    @Test
    fun parsesTtlAnswerNameAndIpv4Address() {
        val response = answerResponse(
            domain = "cdn.example.com",
            type = DnsPacketParser.TYPE_A,
            ttlSeconds = 120,
            rdata = byteArrayOf(93, 184.toByte(), 216, 34)
        )
        assertEquals(120L, DnsPacketParser.getMinAnswerTtlSeconds(response))
        assertEquals("93.184.216.34", DnsPacketParser.parseFirstAnswerIp(response))
        assertEquals(listOf("93.184.216.34"), DnsPacketParser.parseAnswerIps(response))
        assertTrue(DnsPacketParser.parseAnswerNames(response).contains("cdn.example.com"))
    }

    private fun answerResponse(domain: String, type: Int, ttlSeconds: Int, rdata: ByteArray): ByteArray {
        val question = query(domain, type)
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { stream ->
            stream.writeShort(0x1234)
            stream.writeShort(0x8180)
            stream.writeShort(1)
            stream.writeShort(1)
            stream.writeShort(0)
            stream.writeShort(0)
            stream.write(question, 12, question.size - 12)
            stream.writeShort(0xC00C)
            stream.writeShort(type)
            stream.writeShort(1)
            stream.writeInt(ttlSeconds)
            stream.writeShort(rdata.size)
            stream.write(rdata)
        }
        return output.toByteArray()
    }

    private fun query(domain: String, type: Int, transactionId: Int = 0x1234): ByteArray {
        val output = ByteArrayOutputStream()
        val stream = DataOutputStream(output)
        stream.use {
            it.writeShort(transactionId)
            it.writeShort(0x0100)
            it.writeShort(1)
            it.writeShort(0)
            it.writeShort(0)
            it.writeShort(0)
            for (label in domain.split('.')) {
                it.writeByte(label.length)
                it.write(label.toByteArray(Charsets.US_ASCII))
            }
            it.writeByte(0)
            it.writeShort(type)
            it.writeShort(1)
        }
        return output.toByteArray()
    }
}
