package top.expli.bluetoothtester.bluetooth

/**
 * 解析后的测试数据包。
 * 由 FrameSyncReader 解析产生，或由 TestPacketBuilder 构建。
 */
data class TestPacket(
    val sequenceNumber: Long,      // 4字节序号，用 Long 避免符号问题
    val payload: ByteArray,
    val crcValid: Boolean,         // CRC 校验结果（NoCRC 模式下始终为 true）
    val rawBytes: ByteArray        // 完整原始字节（含 delimiter 到 CRC）
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TestPacket) return false
        return sequenceNumber == other.sequenceNumber &&
                payload.contentEquals(other.payload) &&
                crcValid == other.crcValid &&
                rawBytes.contentEquals(other.rawBytes)
    }

    override fun hashCode(): Int {
        var result = sequenceNumber.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + crcValid.hashCode()
        result = 31 * result + rawBytes.contentHashCode()
        return result
    }
}

/**
 * TestPacket 构建器。预分配缓冲区，零分配热路径更新序号和 CRC。
 *
 * 缓冲区布局（CRC 模式）：
 * [0xAA][0x55][seq 4B 大端序][len 2B 大端序][payload NB][CRC 2B 大端序]
 *
 * 缓冲区布局（NoCRC 模式）：
 * [0xAA][0x55][seq 4B 大端序][len 2B 大端序][payload NB]
 */
object TestPacketBuilder {
    const val HEADER_SIZE = 8          // delimiter(2) + seq(4) + len(2)
    const val CRC_SIZE = 2
    const val MAX_PAYLOAD_SIZE = 32768

    val DELIMITER: UShort = 0xAA55u

    /**
     * 创建预分配缓冲区并填充固定部分（delimiter、payload 长度、默认 payload pattern）。
     * 序号和 CRC 字段留空，后续由 [fillBuffer] 或 [updateSequenceAndCrc] 填充。
     *
     * @param payloadSize payload 字节数（1~MAX_PAYLOAD_SIZE）
     * @param withCrc 是否包含 CRC 字段
     * @return 预分配的缓冲区
     */
    fun allocateBuffer(payloadSize: Int, withCrc: Boolean): ByteArray {
        val totalSize = HEADER_SIZE + payloadSize + if (withCrc) CRC_SIZE else 0
        val buffer = ByteArray(totalSize)

        // Delimiter: 0xAA, 0x55
        buffer[0] = 0xAA.toByte()
        buffer[1] = 0x55

        // Payload length: 2 字节大端序 (offset 6..7)
        buffer[6] = (payloadSize shr 8).toByte()
        buffer[7] = (payloadSize and 0xFF).toByte()

        // Payload: 默认递增序列 0x00~0xFF 循环 (offset 8..8+payloadSize-1)
        for (i in 0 until payloadSize) {
            buffer[HEADER_SIZE + i] = (i and 0xFF).toByte()
        }

        return buffer
    }

    /**
     * 同 [allocateBuffer] 但使用自定义 payload 内容（循环填充到 payloadSize）。
     *
     * @param payloadSize payload 字节数
     * @param withCrc 是否包含 CRC 字段
     * @param payload 自定义 payload 数据，循环填充
     * @return 预分配的缓冲区
     */
    fun allocateBufferWithPayload(payloadSize: Int, withCrc: Boolean, payload: ByteArray): ByteArray {
        val totalSize = HEADER_SIZE + payloadSize + if (withCrc) CRC_SIZE else 0
        val buffer = ByteArray(totalSize)

        // Delimiter
        buffer[0] = 0xAA.toByte()
        buffer[1] = 0x55

        // Payload length: 2 字节大端序
        buffer[6] = (payloadSize shr 8).toByte()
        buffer[7] = (payloadSize and 0xFF).toByte()

        // Payload: 自定义内容循环填充
        if (payload.isNotEmpty()) {
            for (i in 0 until payloadSize) {
                buffer[HEADER_SIZE + i] = payload[i % payload.size]
            }
        }

        return buffer
    }

    /**
     * 将序号写入缓冲区，并在 CRC 模式下计算和写入 CRC。
     *
     * @param buffer 预分配缓冲区（由 [allocateBuffer] 创建）
     * @param sequenceNumber 包序号（Long，截断为 4 字节无符号）
     * @param payloadSize payload 字节数
     * @param withCrc 是否计算和写入 CRC
     */
    fun fillBuffer(buffer: ByteArray, sequenceNumber: Long, payloadSize: Int, withCrc: Boolean) {
        // 序号: 4 字节大端序 (offset 2..5)
        val seq = sequenceNumber and 0xFFFFFFFFL
        buffer[2] = (seq shr 24).toByte()
        buffer[3] = (seq shr 16).toByte()
        buffer[4] = (seq shr 8).toByte()
        buffer[5] = seq.toByte()

        // CRC: 计算范围 [0, HEADER_SIZE + payloadSize)，写入末尾 2 字节大端序
        if (withCrc) {
            val crcRange = HEADER_SIZE + payloadSize
            val crc = Crc16Ccitt.compute(buffer, 0, crcRange)
            buffer[crcRange] = (crc.toInt() shr 8).toByte()
            buffer[crcRange + 1] = (crc.toInt() and 0xFF).toByte()
        }
    }

    /**
     * 零分配热路径：仅更新序号和 CRC 字段（CRC 模式）。
     * 不创建新对象，直接修改缓冲区。
     *
     * @param buffer 预分配缓冲区
     * @param sequenceNumber 包序号
     * @param payloadSize payload 字节数
     */
    fun updateSequenceAndCrc(buffer: ByteArray, sequenceNumber: Long, payloadSize: Int) {
        // 序号: 4 字节大端序 (offset 2..5)
        val seq = sequenceNumber and 0xFFFFFFFFL
        buffer[2] = (seq shr 24).toByte()
        buffer[3] = (seq shr 16).toByte()
        buffer[4] = (seq shr 8).toByte()
        buffer[5] = seq.toByte()

        // CRC: 计算范围 [0, HEADER_SIZE + payloadSize)，写入末尾 2 字节大端序
        val crcRange = HEADER_SIZE + payloadSize
        val crc = Crc16Ccitt.compute(buffer, 0, crcRange)
        buffer[crcRange] = (crc.toInt() shr 8).toByte()
        buffer[crcRange + 1] = (crc.toInt() and 0xFF).toByte()
    }

    /**
     * NoCRC 模式热路径：仅更新序号字段。
     * 不创建新对象，直接修改缓冲区。
     *
     * @param buffer 预分配缓冲区
     * @param sequenceNumber 包序号
     */
    fun updateSequence(buffer: ByteArray, sequenceNumber: Long) {
        val seq = sequenceNumber and 0xFFFFFFFFL
        buffer[2] = (seq shr 24).toByte()
        buffer[3] = (seq shr 16).toByte()
        buffer[4] = (seq shr 8).toByte()
        buffer[5] = seq.toByte()
    }
}
