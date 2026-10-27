package com.noichhar.app.net

data class TcpSegment(
    val sourcePort: Int,
    val destPort: Int,
    val sequenceNumber: Long,
    val acknowledgementNumber: Long,
    val dataOffset: Int,
    val flags: Int,
    val window: Int,
    val checksum: Int,
    val urgentPointer: Int,
    val payload: ByteArray
) {
    companion object {
        const val FLAG_FIN = 0x01
        const val FLAG_SYN = 0x02
        const val FLAG_RST = 0x04
        const val FLAG_PSH = 0x08
        const val FLAG_ACK = 0x10

        fun parse(data: ByteArray): TcpSegment? {
            if (data.size < 20) return null
            return try {
                val sourcePort = readShort(data, 0)
                val destPort = readShort(data, 2)
                val sequenceNumber = readInt(data, 4)
                val acknowledgementNumber = readInt(data, 8)
                val dataOffset = ((data[12].toInt() and 0xF0) shr 4) * 4
                if (dataOffset < 20 || dataOffset > data.size) return null
                val flags = data[13].toInt() and 0xFF
                val window = readShort(data, 14)
                val checksum = readShort(data, 16)
                val urgentPointer = readShort(data, 18)
                TcpSegment(
                    sourcePort = sourcePort,
                    destPort = destPort,
                    sequenceNumber = sequenceNumber,
                    acknowledgementNumber = acknowledgementNumber,
                    dataOffset = dataOffset,
                    flags = flags,
                    window = window,
                    checksum = checksum,
                    urgentPointer = urgentPointer,
                    payload = if (data.size > dataOffset) data.copyOfRange(dataOffset, data.size) else ByteArray(0)
                )
            } catch (_: Exception) {
                null
            }
        }

        private fun readShort(data: ByteArray, offset: Int): Int =
            ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)

        private fun readInt(data: ByteArray, offset: Int): Long =
            ((data[offset].toLong() and 0xFFL) shl 24) or
                ((data[offset + 1].toLong() and 0xFFL) shl 16) or
                ((data[offset + 2].toLong() and 0xFFL) shl 8) or
                (data[offset + 3].toLong() and 0xFFL)
    }

    fun hasFlag(flag: Int): Boolean = (flags and flag) == flag

    /** Number of sequence positions consumed by this segment, including SYN/FIN. */
    fun sequenceLength(): Long = payload.size.toLong() +
        (if (hasFlag(FLAG_SYN)) 1L else 0L) +
        (if (hasFlag(FLAG_FIN)) 1L else 0L)

    fun nextSequenceNumber(): Long = (sequenceNumber + sequenceLength()) and 0xFFFFFFFFL
}
