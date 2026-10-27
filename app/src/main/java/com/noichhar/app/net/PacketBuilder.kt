package com.noichhar.app.net

import java.nio.ByteBuffer

object PacketBuilder {
    private const val IP_HEADER_LENGTH = 20
    private const val UDP_HEADER_LENGTH = 8
    private const val TCP_HEADER_LENGTH = 20
    private const val IP_PROTOCOL_TCP = 6
    private const val IP_PROTOCOL_UDP = 17

    fun udpDnsResponse(original: IPPacket, dnsResponse: ByteArray): ByteArray {
        val udpLength = UDP_HEADER_LENGTH + dnsResponse.size
        val totalLength = IP_HEADER_LENGTH + udpLength
        val packet = ByteBuffer.allocate(totalLength)

        writeIpv4Header(packet, totalLength, IP_PROTOCOL_UDP, original.destAddress.address, original.sourceAddress.address)
        packet.putShort((original.destPort and 0xFFFF).toShort())
        packet.putShort((original.sourcePort and 0xFFFF).toShort())
        packet.putShort((udpLength and 0xFFFF).toShort())
        packet.putShort(0) // UDP checksum is optional for IPv4.
        packet.put(dnsResponse)

        val result = packet.array()
        writeIpChecksum(result)
        return result
    }

    fun tcpDnsResponse(
        original: IPPacket,
        dnsResponse: ByteArray,
        sequenceNumber: Long,
        acknowledgementNumber: Long,
        flags: Int = TcpSegment.FLAG_PSH or TcpSegment.FLAG_ACK or TcpSegment.FLAG_FIN
    ): ByteArray {
        val dnsPayload = ByteBuffer.allocate(2 + dnsResponse.size)
        dnsPayload.putShort((dnsResponse.size and 0xFFFF).toShort())
        dnsPayload.put(dnsResponse)
        return buildTcpResponse(
            original = original,
            sequenceNumber = sequenceNumber,
            acknowledgementNumber = acknowledgementNumber,
            flags = flags,
            payload = dnsPayload.array()
        )
    }

    fun tcpSynAck(original: IPPacket, sequenceNumber: Long, acknowledgementNumber: Long): ByteArray =
        buildTcpResponse(
            original = original,
            sequenceNumber = sequenceNumber,
            acknowledgementNumber = acknowledgementNumber,
            flags = TcpSegment.FLAG_SYN or TcpSegment.FLAG_ACK,
            payload = ByteArray(0)
        )

    fun tcpReset(original: IPPacket): ByteArray {
        val segment = original.tcpSegment()
        val acknowledgement = if (segment != null) {
            (segment.sequenceNumber + segment.payload.size.toLong()) and 0xFFFFFFFFL
        } else {
            0L
        }
        return buildTcpResponse(
            original = original,
            sequenceNumber = 0L,
            acknowledgementNumber = acknowledgement,
            flags = TcpSegment.FLAG_RST or TcpSegment.FLAG_ACK,
            payload = ByteArray(0)
        )
    }

    private fun buildTcpResponse(
        original: IPPacket,
        sequenceNumber: Long,
        acknowledgementNumber: Long,
        flags: Int,
        payload: ByteArray
    ): ByteArray {
        val tcpLength = TCP_HEADER_LENGTH + payload.size
        val totalLength = IP_HEADER_LENGTH + tcpLength
        val packet = ByteBuffer.allocate(totalLength)
        val sourceIp = original.destAddress.address
        val destinationIp = original.sourceAddress.address

        writeIpv4Header(packet, totalLength, IP_PROTOCOL_TCP, sourceIp, destinationIp)
        packet.putShort((original.destPort and 0xFFFF).toShort())
        packet.putShort((original.sourcePort and 0xFFFF).toShort())
        packet.putInt((sequenceNumber and 0xFFFFFFFFL).toInt())
        packet.putInt((acknowledgementNumber and 0xFFFFFFFFL).toInt())
        packet.put(0x50.toByte()) // Data offset: 5 words (20 bytes).
        packet.put((flags and 0xFF).toByte())
        packet.putShort(0xFFFF.toShort()) // Window.
        packet.putShort(0) // checksum placeholder
        packet.putShort(0) // urgent pointer
        if (payload.isNotEmpty()) packet.put(payload)

        val result = packet.array()
        writeIpChecksum(result)
        writeTcpChecksum(result, sourceIp, destinationIp, tcpLength)
        return result
    }

    private fun writeIpv4Header(
        buffer: ByteBuffer,
        totalLength: Int,
        protocol: Int,
        sourceIp: ByteArray,
        destinationIp: ByteArray
    ) {
        buffer.put(0x45.toByte()) // IPv4, 20-byte header.
        buffer.put(0) // DSCP/ECN.
        buffer.putShort((totalLength and 0xFFFF).toShort())
        buffer.putShort(0) // Identification.
        buffer.putShort(0x4000.toShort()) // Don't fragment.
        buffer.put(64) // TTL.
        buffer.put((protocol and 0xFF).toByte())
        buffer.putShort(0) // Header checksum placeholder.
        buffer.put(sourceIp.copyOf(4))
        buffer.put(destinationIp.copyOf(4))
    }

    private fun writeIpChecksum(packet: ByteArray) {
        packet[10] = 0
        packet[11] = 0
        val value = checksum(packet, 0, IP_HEADER_LENGTH)
        packet[10] = ((value shr 8) and 0xFF).toByte()
        packet[11] = (value and 0xFF).toByte()
    }

    private fun writeTcpChecksum(
        packet: ByteArray,
        sourceIp: ByteArray,
        destinationIp: ByteArray,
        tcpLength: Int
    ) {
        val checksumOffset = IP_HEADER_LENGTH + 16
        packet[checksumOffset] = 0
        packet[checksumOffset + 1] = 0

        var pseudoSum = 0L
        for (i in 0 until 4 step 2) {
            pseudoSum += (((sourceIp[i].toLong() and 0xFFL) shl 8) or (sourceIp[i + 1].toLong() and 0xFFL))
            pseudoSum += (((destinationIp[i].toLong() and 0xFFL) shl 8) or (destinationIp[i + 1].toLong() and 0xFFL))
        }
        pseudoSum += IP_PROTOCOL_TCP.toLong() and 0xFFFFL
        pseudoSum += tcpLength.toLong() and 0xFFFFL

        val value = checksum(packet, IP_HEADER_LENGTH, tcpLength, pseudoSum)
        packet[checksumOffset] = ((value shr 8) and 0xFF).toByte()
        packet[checksumOffset + 1] = (value and 0xFF).toByte()
    }

    private fun checksum(data: ByteArray, offset: Int, length: Int, initialSum: Long = 0L): Int {
        var sum = initialSum
        var index = offset
        val end = offset + length
        while (index + 1 < end) {
            sum += (((data[index].toLong() and 0xFFL) shl 8) or (data[index + 1].toLong() and 0xFFL))
            index += 2
        }
        if (index < end) {
            sum += ((data[index].toLong() and 0xFFL) shl 8)
        }
        while ((sum shr 16) > 0) {
            sum = (sum and 0xFFFFL) + (sum shr 16)
        }
        return sum.inv().toInt() and 0xFFFF
    }
}
