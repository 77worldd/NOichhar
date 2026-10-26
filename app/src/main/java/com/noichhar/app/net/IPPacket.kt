package com.noichhar.app.net

import java.net.InetAddress
import java.nio.ByteBuffer

data class IPPacket(
    val version: Int,
    val protocol: Int,
    val sourceAddress: InetAddress,
    val destAddress: InetAddress,
    val sourcePort: Int,
    val destPort: Int,
    val headerLength: Int,
    val totalLength: Int,
    val payload: ByteArray
) {
    companion object {
        const val PROTOCOL_TCP = 6
        const val PROTOCOL_UDP = 17
        const val PROTOCOL_ICMP = 1

        fun parse(data: ByteArray): IPPacket? {
            if (data.size < 20) return null
            return try {
                val buffer = ByteBuffer.wrap(data)
                val versionAndIhl = buffer.get().toInt() and 0xFF
                val version = versionAndIhl shr 4
                val ihl = (versionAndIhl and 0x0F) * 4
                if (version != 4 || ihl < 20 || ihl > data.size) return null

                buffer.position(2)
                val totalLength = buffer.short.toInt() and 0xFFFF
                if (totalLength < ihl || totalLength > data.size) return null

                buffer.position(9)
                val protocol = buffer.get().toInt() and 0xFF

                buffer.position(12)
                val srcBytes = ByteArray(4)
                buffer.get(srcBytes)
                val srcAddress = InetAddress.getByAddress(srcBytes)
                val dstBytes = ByteArray(4)
                buffer.get(dstBytes)
                val dstAddress = InetAddress.getByAddress(dstBytes)

                var srcPort = 0
                var dstPort = 0
                if ((protocol == PROTOCOL_TCP || protocol == PROTOCOL_UDP) && totalLength >= ihl + 4) {
                    buffer.position(ihl)
                    srcPort = buffer.short.toInt() and 0xFFFF
                    dstPort = buffer.short.toInt() and 0xFFFF
                }

                val payload = data.copyOfRange(ihl, totalLength)
                IPPacket(
                    version = version,
                    protocol = protocol,
                    sourceAddress = srcAddress,
                    destAddress = dstAddress,
                    sourcePort = srcPort,
                    destPort = dstPort,
                    headerLength = ihl,
                    totalLength = totalLength,
                    payload = payload
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    fun isDnsQuery(): Boolean = protocol == PROTOCOL_UDP && destPort == 53

    fun getDnsPayload(): ByteArray? {
        if (!isDnsQuery() || payload.size < 8) return null
        val udpLength = ((payload[4].toInt() and 0xFF) shl 8) or (payload[5].toInt() and 0xFF)
        val end = if (udpLength in 8..payload.size) udpLength else payload.size
        return if (end > 8) payload.copyOfRange(8, end) else null
    }
}
