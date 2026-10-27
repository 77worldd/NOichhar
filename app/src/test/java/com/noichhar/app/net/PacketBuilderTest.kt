package com.noichhar.app.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.nio.ByteBuffer

class PacketBuilderTest {

    @Test
    fun udpResponseHasValidIpv4Header() {
        val original = udpPacket()
        val response = PacketBuilder.udpDnsResponse(original, ByteArray(24) { 0x42 })
        assertTrue(response.size > 20)
        assertEquals(4, (response[0].toInt() and 0xF0) shr 4)
        assertEquals(17, response[9].toInt() and 0xFF)
        assertEquals(0xFFFF, foldedChecksum(response, 0, 20))
    }

    @Test
    fun tcpDnsResponseHasValidIpAndTcpChecksums() {
        val original = tcpPacket()
        val response = PacketBuilder.tcpDnsResponse(
            original = original,
            dnsResponse = ByteArray(20) { 0x33 },
            sequenceNumber = 9_000L,
            acknowledgementNumber = 5_000L
        )
        val tcpLength = response.size - 20
        assertEquals(62, response.size)
        assertEquals(6, response[9].toInt() and 0xFF)
        assertEquals(
            TcpSegment.FLAG_PSH or TcpSegment.FLAG_ACK or TcpSegment.FLAG_FIN,
            response[33].toInt() and 0xFF
        )
        assertEquals(0xFFFF, foldedChecksum(response, 0, 20))
        assertEquals(0xFFFF, foldedChecksum(response, 20, tcpLength, pseudoHeaderSum(response, tcpLength)))
    }

    @Test
    fun tcpResetAcknowledgesOriginalPayload() {
        val original = tcpPacket()
        val reset = PacketBuilder.tcpReset(original)
        assertEquals(40, reset.size)
        assertEquals(
            TcpSegment.FLAG_RST or TcpSegment.FLAG_ACK,
            reset[33].toInt() and 0xFF
        )
        assertEquals(0xFFFF, foldedChecksum(reset, 0, 20))
        assertEquals(0xFFFF, foldedChecksum(reset, 20, 20, pseudoHeaderSum(reset, 20)))
    }

    private fun udpPacket(): IPPacket {
        val payload = ByteArray(8 + 12)
        ByteBuffer.wrap(payload).apply {
            putShort(12_345)
            putShort(53)
            putShort(20)
            putShort(0)
        }
        return ipPacket(IPPacket.PROTOCOL_UDP, payload)
    }

    private fun tcpPacket(): IPPacket {
        val payload = ByteArray(20 + 12)
        ByteBuffer.wrap(payload).apply {
            putShort(12_345)
            putShort(53)
            putInt(1_000)
            putInt(2_000)
            put(0x50)
            put((TcpSegment.FLAG_PSH or TcpSegment.FLAG_ACK).toByte())
            putShort(0xFFFF.toShort())
            putShort(0)
            putShort(0)
            put(ByteArray(12) { 0x11 })
        }
        return ipPacket(IPPacket.PROTOCOL_TCP, payload)
    }

    private fun ipPacket(protocol: Int, transport: ByteArray): IPPacket = IPPacket(
        version = 4,
        protocol = protocol,
        sourceAddress = InetAddress.getByName("10.111.222.1"),
        destAddress = InetAddress.getByName("10.111.222.3"),
        sourcePort = 12_345,
        destPort = 53,
        headerLength = 20,
        totalLength = 20 + transport.size,
        payload = transport
    )

    private fun pseudoHeaderSum(packet: ByteArray, transportLength: Int): Long {
        var sum = 0L
        for (i in 12 until 20 step 2) {
            sum += (((packet[i].toLong() and 0xFFL) shl 8) or (packet[i + 1].toLong() and 0xFFL))
        }
        sum += 6L
        sum += transportLength.toLong() and 0xFFFFL
        return sum
    }

    private fun foldedChecksum(data: ByteArray, offset: Int, length: Int, initialSum: Long = 0L): Int {
        var sum = initialSum
        var index = offset
        val end = offset + length
        while (index + 1 < end) {
            sum += (((data[index].toLong() and 0xFFL) shl 8) or (data[index + 1].toLong() and 0xFFL))
            index += 2
        }
        if (index < end) sum += ((data[index].toLong() and 0xFFL) shl 8)
        while ((sum shr 16) > 0) sum = (sum and 0xFFFFL) + (sum shr 16)
        return sum.toInt() and 0xFFFF
    }
}
