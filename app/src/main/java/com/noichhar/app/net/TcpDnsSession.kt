package com.noichhar.app.net

import java.io.ByteArrayOutputStream

/** One bounded DNS-over-TCP request, including its two-byte length prefix. */
internal class TcpDnsSession(
    val nextSequence: Long,
    val initialClientSequence: Long,
    val createdAt: Long = System.currentTimeMillis()
) {
    data class ReceiveResult(val query: ByteArray? = null, val invalid: Boolean = false)

    private val buffer = ByteArrayOutputStream()
    @Volatile var clientNextSequence: Long = initialClientSequence and SEQUENCE_MASK
        private set
    private var submitted = false

    /** Accept in-order or overlapping retransmitted bytes; ACK gaps without buffering them. */
    @Synchronized
    fun receive(sequence: Long, payload: ByteArray): ReceiveResult {
        if (submitted || payload.isEmpty()) return ReceiveResult()
        val start = sequence and SEQUENCE_MASK
        val ahead = (start - clientNextSequence) and SEQUENCE_MASK
        if (ahead != 0L && ahead < HALF_SEQUENCE_SPACE) return ReceiveResult()
        val overlap = if (ahead == 0L) 0L else (clientNextSequence - start) and SEQUENCE_MASK
        if (overlap >= payload.size) return ReceiveResult()
        val offset = overlap.toInt()
        val accepted = payload.size - offset
        if (buffer.size() + accepted > MAX_QUERY_BYTES + 2) return ReceiveResult(invalid = true)
        buffer.write(payload, offset, accepted)
        clientNextSequence = (clientNextSequence + accepted) and SEQUENCE_MASK

        val bytes = buffer.toByteArray()
        if (bytes.size < 2) return ReceiveResult()
        val length = ((bytes[0].toInt() and 0xff) shl 8) or (bytes[1].toInt() and 0xff)
        if (length < 12 || length > MAX_QUERY_BYTES) return ReceiveResult(invalid = true)
        if (bytes.size < length + 2) return ReceiveResult()
        submitted = true
        return ReceiveResult(query = bytes.copyOfRange(2, length + 2))
    }

    companion object {
        private const val SEQUENCE_MASK = 0xFFFFFFFFL
        private const val HALF_SEQUENCE_SPACE = 0x80000000L
        private const val MAX_QUERY_BYTES = 4096
    }
}
