package top.expli.bluetoothtester.bluetooth

/**
 * 协议无关的 Ping Echo 处理器。
 * 接收 TestPacket，若 payload 首字节为 PING_REQUEST (0x01)，
 * 则将首字节改为 PING_REPLY (0x02)，重新计算 CRC，返回可直接发送的字节数组。
 *
 * 不依赖 SPP 或 L2CAP 具体实现，可被任意传输层复用。
 */
object PingEchoHandler {
    const val PING_REQUEST: Byte = 0x01
    const val PING_REPLY: Byte = 0x02
    const val TIMESTAMP_OFFSET = 1       // payload 中时间戳起始偏移
    const val TIMESTAMP_SIZE = 8         // 8 字节 nanoTime
    const val MIN_PAYLOAD_SIZE = 9       // type(1) + timestamp(8)

    /**
     * 处理收到的 TestPacket，若为 Ping Request 则生成 Echo Reply 字节数组。
     *
     * 处理逻辑：
     * 1. 检查 payload 首字节是否为 PING_REQUEST (0x01)
     * 2. 检查 payload 长度 >= MIN_PAYLOAD_SIZE (9)
     * 3. 复制 rawBytes
     * 4. 将 payload 区域的首字节改为 PING_REPLY (0x02)
     * 5. 使用 Crc16Ccitt 重新计算 CRC（从 delimiter 到 payload 末尾）
     * 6. 将新 CRC 写入包末尾 2 字节（大端序）
     * 7. 返回修改后的完整包字节数组
     *
     * @param packet 接收到的 TestPacket
     * @return Echo Reply 的完整包字节数组，非 Ping Request 返回 null
     */
    fun handlePacket(packet: TestPacket): ByteArray? {
        val payload = packet.payload
        // 检查 payload 长度和类型标识
        if (payload.size < MIN_PAYLOAD_SIZE) return null
        if (payload[0] != PING_REQUEST) return null

        // 复制 rawBytes 以避免修改原始数据
        val reply = packet.rawBytes.copyOf()

        // payload 在 rawBytes 中的偏移 = HEADER_SIZE (delimiter 2 + seq 4 + len 2 = 8)
        val payloadOffset = TestPacketBuilder.HEADER_SIZE

        // 将 payload 首字节从 PING_REQUEST 改为 PING_REPLY
        reply[payloadOffset] = PING_REPLY

        // 重新计算 CRC：校验范围从 delimiter 到 payload 末尾（不含 CRC 字段本身）
        val crcDataLength = TestPacketBuilder.HEADER_SIZE + payload.size
        val crc = Crc16Ccitt.compute(reply, offset = 0, length = crcDataLength)

        // 将新 CRC 写入包末尾 2 字节（大端序：高字节在前）
        val crcOffset = crcDataLength
        reply[crcOffset] = (crc.toInt() shr 8).toByte()
        reply[crcOffset + 1] = (crc.toInt() and 0xFF).toByte()

        return reply
    }
}
