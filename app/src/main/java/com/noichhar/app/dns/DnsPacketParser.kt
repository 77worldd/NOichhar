package com.noichhar.app.dns

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer

data class DnsQuestion(
    val name: String,
    val type: Int,
    val clazz: Int,
    val endOffset: Int = 0
)

data class DnsHeader(
    val id: Int,
    val flags: Int,
    val qdCount: Int,
    val anCount: Int,
    val nsCount: Int,
    val arCount: Int
)

object DnsPacketParser {

    const val TYPE_A = 1
    const val TYPE_AAAA = 28
    const val TYPE_CNAME = 5
    const val TYPE_MX = 15
    const val TYPE_TXT = 16
    const val TYPE_NS = 2
    const val TYPE_SOA = 6

    private const val FLAG_NXDOMAIN = 0x8183
    private const val FLAG_SERVFAIL = 0x8182

    fun parseQuestion(packet: ByteArray): DnsQuestion? {
        val header = parseHeader(packet) ?: return null
        if (header.qdCount == 0) return null
        val (name, nameEnd) = readDomainName(packet, 12) ?: return null
        if (nameEnd + 4 > packet.size) return null
        val type = ((packet[nameEnd].toInt() and 0xFF) shl 8) or (packet[nameEnd + 1].toInt() and 0xFF)
        val clazz = ((packet[nameEnd + 2].toInt() and 0xFF) shl 8) or (packet[nameEnd + 3].toInt() and 0xFF)
        return DnsQuestion(name, type, clazz, nameEnd + 4)
    }

    fun parseHeader(packet: ByteArray): DnsHeader? {
        if (packet.size < 12) return null
        return try {
            val buffer = ByteBuffer.wrap(packet)
            DnsHeader(
                id = buffer.short.toInt() and 0xFFFF,
                flags = buffer.short.toInt() and 0xFFFF,
                qdCount = buffer.short.toInt() and 0xFFFF,
                anCount = buffer.short.toInt() and 0xFFFF,
                nsCount = buffer.short.toInt() and 0xFFFF,
                arCount = buffer.short.toInt() and 0xFFFF
            )
        } catch (_: Exception) {
            null
        }
    }

    fun getTransactionId(packet: ByteArray): Int {
        if (packet.size < 2) return 0
        return ((packet[0].toInt() and 0xFF) shl 8) or (packet[1].toInt() and 0xFF)
    }

    fun rewriteTransactionId(response: ByteArray, query: ByteArray): ByteArray {
        if (response.size < 2 || query.size < 2) return response
        return response.copyOf().also {
            it[0] = query[0]
            it[1] = query[1]
        }
    }

    fun createBlockedResponse(query: ByteArray): ByteArray = createNxDomainResponse(query)

    fun createNxDomainResponse(query: ByteArray): ByteArray = createEmptyResponse(query, FLAG_NXDOMAIN)

    fun createServFailResponse(query: ByteArray): ByteArray = createEmptyResponse(query, FLAG_SERVFAIL)

    fun getQueryTypeName(type: Int): String = when (type) {
        TYPE_A -> "A"
        TYPE_AAAA -> "AAAA"
        TYPE_CNAME -> "CNAME"
        TYPE_MX -> "MX"
        TYPE_TXT -> "TXT"
        TYPE_NS -> "NS"
        TYPE_SOA -> "SOA"
        else -> "TYPE$type"
    }

    fun parseFirstAnswerIp(packet: ByteArray): String? {
        val header = parseHeader(packet) ?: return null
        if (header.anCount == 0) return null
        var offset = 12
        repeat(header.qdCount) {
            val (_, end) = readDomainName(packet, offset) ?: return null
            offset = end + 4
            if (offset > packet.size) return null
        }
        repeat(header.anCount) {
            val (_, nameEnd) = readDomainName(packet, offset) ?: return null
            if (nameEnd + 10 > packet.size) return null
            val type = readShort(packet, nameEnd)
            val dataLength = readShort(packet, nameEnd + 8)
            val dataStart = nameEnd + 10
            if (dataStart + dataLength > packet.size) return null
            if (type == TYPE_A && dataLength == 4) {
                return (0 until 4).joinToString(".") { (packet[dataStart + it].toInt() and 0xFF).toString() }
            }
            offset = dataStart + dataLength
        }
        return null
    }

    fun getMinAnswerTtlSeconds(packet: ByteArray): Long? {
        val header = parseHeader(packet) ?: return null
        if (header.anCount == 0) return null
        var offset = 12
        repeat(header.qdCount) {
            val (_, end) = readDomainName(packet, offset) ?: return null
            offset = end + 4
            if (offset > packet.size) return null
        }
        var minimum: Long? = null
        repeat(header.anCount) {
            val (_, nameEnd) = readDomainName(packet, offset) ?: return null
            if (nameEnd + 10 > packet.size) return null
            val dataLength = readShort(packet, nameEnd + 8)
            val dataStart = nameEnd + 10
            if (dataStart + dataLength > packet.size) return null
            val ttl = readInt(packet, nameEnd + 4)
            if (ttl > 0 && (minimum == null || ttl < minimum!!)) minimum = ttl
            offset = dataStart + dataLength
        }
        return minimum
    }

    fun parseAnswerNames(packet: ByteArray): List<String> {
        val header = parseHeader(packet) ?: return emptyList()
        if (header.anCount == 0) return emptyList()
        val names = LinkedHashSet<String>()
        var offset = 12
        repeat(header.qdCount) {
            val (_, end) = readDomainName(packet, offset) ?: return emptyList()
            offset = end + 4
            if (offset > packet.size) return emptyList()
        }
        repeat(header.anCount) {
            val (owner, nameEnd) = readDomainName(packet, offset) ?: return names.toList()
            if (owner.isNotBlank()) names += owner.trim('.').lowercase()
            if (nameEnd + 10 > packet.size) return names.toList()
            val type = readShort(packet, nameEnd)
            val dataLength = readShort(packet, nameEnd + 8)
            val dataStart = nameEnd + 10
            if (dataStart + dataLength > packet.size) return names.toList()
            if ((type == TYPE_CNAME || type == TYPE_NS) && dataLength > 0) {
                val (target, _) = readDomainName(packet, dataStart) ?: return@repeat
                if (target.isNotBlank()) names += target.trim('.').lowercase()
            }
            offset = dataStart + dataLength
        }
        return names.toList()
    }

    fun parseAnswerIps(packet: ByteArray): List<String> {
        val header = parseHeader(packet) ?: return emptyList()
        if (header.anCount == 0) return emptyList()
        val ips = LinkedHashSet<String>()
        var offset = 12
        repeat(header.qdCount) {
            val (_, end) = readDomainName(packet, offset) ?: return emptyList()
            offset = end + 4
            if (offset > packet.size) return emptyList()
        }
        repeat(header.anCount) {
            val (_, nameEnd) = readDomainName(packet, offset) ?: return ips.toList()
            if (nameEnd + 10 > packet.size) return ips.toList()
            val type = readShort(packet, nameEnd)
            val dataLength = readShort(packet, nameEnd + 8)
            val dataStart = nameEnd + 10
            if (dataStart + dataLength > packet.size) return ips.toList()
            when {
                type == TYPE_A && dataLength == 4 -> {
                    ips += (0 until 4).joinToString(".") {
                        (packet[dataStart + it].toInt() and 0xFF).toString()
                    }
                }
                type == TYPE_AAAA && dataLength == 16 -> {
                    ips += (0 until 16 step 2).joinToString(":") {
                        val high = packet[dataStart + it].toInt() and 0xFF
                        val low = packet[dataStart + it + 1].toInt() and 0xFF
                        ((high shl 8) or low).toString(16)
                    }
                }
            }
            offset = dataStart + dataLength
        }
        return ips.toList()
    }

    private fun createEmptyResponse(query: ByteArray, flags: Int): ByteArray {
        val question = parseQuestion(query) ?: return ByteArray(0)
        val output = ByteArrayOutputStream(question.endOffset)
        val stream = DataOutputStream(output)
        stream.use {
            it.write(query, 0, 2)
            it.writeShort(flags)
            it.writeShort(1)
            it.writeShort(0)
            it.writeShort(0)
            it.writeShort(0)
            it.write(query, 12, question.endOffset - 12)
        }
        return output.toByteArray()
    }

    private fun readDomainName(packet: ByteArray, startOffset: Int): Pair<String, Int>? {
        if (startOffset < 0 || startOffset >= packet.size) return null
        val labels = mutableListOf<String>()
        var position = startOffset
        var endOffset = -1
        var jumps = 0

        while (true) {
            if (position >= packet.size || jumps > 12) return null
            val length = packet[position].toInt() and 0xFF
            position++

            if (length == 0) {
                if (endOffset < 0) endOffset = position
                return labels.joinToString(".") to endOffset
            }

            if ((length and 0xC0) == 0xC0) {
                if (position >= packet.size) return null
                val pointer = ((length and 0x3F) shl 8) or (packet[position].toInt() and 0xFF)
                position++
                if (endOffset < 0) endOffset = position
                if (pointer >= packet.size || pointer < 12) return null
                position = pointer
                jumps++
                continue
            }

            if ((length and 0xC0) != 0 || length > 63 || position + length > packet.size) return null
            labels.add(String(packet, position, length, Charsets.US_ASCII))
            position += length
        }
    }

    private fun readShort(packet: ByteArray, offset: Int): Int =
        ((packet[offset].toInt() and 0xFF) shl 8) or (packet[offset + 1].toInt() and 0xFF)

    private fun readInt(packet: ByteArray, offset: Int): Long =
        ((packet[offset].toLong() and 0xFFL) shl 24) or
            ((packet[offset + 1].toLong() and 0xFFL) shl 16) or
            ((packet[offset + 2].toLong() and 0xFFL) shl 8) or
            (packet[offset + 3].toLong() and 0xFFL)
}
