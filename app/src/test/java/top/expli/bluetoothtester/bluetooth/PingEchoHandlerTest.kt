package top.expli.bluetoothtester.bluetooth

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * PingEchoHandler 单元测试 + 属性测试。
 *
 * **Validates: Requirements 7.9**
 */
@OptIn(io.kotest.common.ExperimentalKotest::class)
class PingEchoHandlerTest {

    // ── Helper: 构建一个合法的 Ping Request TestPacket ──

    /**
     * 手动构建一个完整的 Test_Packet（CRC 模式）。
     * 布局: [AA 55] [seq 4B BE] [len 2B BE] [payload NB] [CRC 2B BE]
     */
    private fun buildTestPacket(
        sequenceNumber: Long,
        payload: ByteArray
    ): TestPacket {
        val payloadSize = payload.size
        val totalSize = TestPacketBuilder.HEADER_SIZE + payloadSize + TestPacketBuilder.CRC_SIZE
        val raw = ByteArray(totalSize)

        // Delimiter: 0xAA55 big-endian
        raw[0] = 0xAA.toByte()
        raw[1] = 0x55

        // Sequence number: 4 bytes big-endian
        val seq = sequenceNumber.toInt()
        raw[2] = (seq shr 24).toByte()
        raw[3] = (seq shr 16).toByte()
        raw[4] = (seq shr 8).toByte()
        raw[5] = seq.toByte()

        // Payload length: 2 bytes big-endian
        raw[6] = (payloadSize shr 8).toByte()
        raw[7] = payloadSize.toByte()

        // Payload
        System.arraycopy(payload, 0, raw, TestPacketBuilder.HEADER_SIZE, payloadSize)

        // CRC: compute over [delimiter .. payload end]
        val crcDataLen = TestPacketBuilder.HEADER_SIZE + payloadSize
        val crc = Crc16Ccitt.compute(raw, 0, crcDataLen)
        raw[crcDataLen] = (crc.toInt() shr 8).toByte()
        raw[crcDataLen + 1] = (crc.toInt() and 0xFF).toByte()

        return TestPacket(
            sequenceNumber = sequenceNumber,
            payload = payload,
            crcValid = true,
            rawBytes = raw
        )
    }

    /**
     * 构建 Ping Request payload: [0x01] [8字节 timestamp BE] [optional padding]
     */
    private fun buildPingRequestPayload(timestamp: Long, paddingSize: Int = 0): ByteArray {
        val size = PingEchoHandler.MIN_PAYLOAD_SIZE + paddingSize
        val payload = ByteArray(size)
        payload[0] = PingEchoHandler.PING_REQUEST
        // Timestamp: 8 bytes big-endian
        for (i in 0 until 8) {
            payload[1 + i] = (timestamp shr (56 - i * 8)).toByte()
        }
        // Padding bytes remain 0
        return payload
    }

    /**
     * 从 rawBytes 中提取 payload 首字节（type 字段）。
     */
    private fun extractTypeFromRaw(raw: ByteArray): Byte {
        return raw[TestPacketBuilder.HEADER_SIZE]
    }

    /**
     * 从 rawBytes 中提取 8 字节 timestamp（payload offset 1..8）。
     */
    private fun extractTimestampFromRaw(raw: ByteArray): Long {
        val offset = TestPacketBuilder.HEADER_SIZE + PingEchoHandler.TIMESTAMP_OFFSET
        var ts = 0L
        for (i in 0 until 8) {
            ts = (ts shl 8) or (raw[offset + i].toLong() and 0xFF)
        }
        return ts
    }

    /**
     * 验证 rawBytes 中的 CRC 是否正确。
     */
    private fun verifyCrc(raw: ByteArray, payloadSize: Int): Boolean {
        val crcDataLen = TestPacketBuilder.HEADER_SIZE + payloadSize
        val computed = Crc16Ccitt.compute(raw, 0, crcDataLen)
        val stored = ((raw[crcDataLen].toInt() and 0xFF) shl 8) or
                (raw[crcDataLen + 1].toInt() and 0xFF)
        return computed.toInt() == stored
    }

    // ══════════════════════════════════════════════════════
    // 单元测试
    // ══════════════════════════════════════════════════════

    @Test
    fun `handlePacket returns echo reply for valid ping request`() {
        val timestamp = 1234567890123456789L
        val payload = buildPingRequestPayload(timestamp)
        val packet = buildTestPacket(sequenceNumber = 42, payload = payload)

        val result = PingEchoHandler.handlePacket(packet)

        assertNotNull(result)
        result!!
        // Type should be changed to PING_REPLY
        assertEquals(PingEchoHandler.PING_REPLY, extractTypeFromRaw(result))
        // Timestamp should be preserved
        assertEquals(timestamp, extractTimestampFromRaw(result))
        // CRC should be valid
        assertTrue(verifyCrc(result, payload.size))
        // Total size should match original
        assertEquals(packet.rawBytes.size, result.size)
    }

    @Test
    fun `handlePacket returns null for non-ping packet`() {
        // Type byte = 0x03 (not PING_REQUEST)
        val payload = ByteArray(PingEchoHandler.MIN_PAYLOAD_SIZE)
        payload[0] = 0x03
        val packet = buildTestPacket(sequenceNumber = 0, payload = payload)

        val result = PingEchoHandler.handlePacket(packet)

        assertNull(result)
    }

    @Test
    fun `handlePacket returns null for ping reply packet`() {
        // Type byte = 0x02 (PING_REPLY, not PING_REQUEST)
        val payload = ByteArray(PingEchoHandler.MIN_PAYLOAD_SIZE)
        payload[0] = PingEchoHandler.PING_REPLY
        val packet = buildTestPacket(sequenceNumber = 0, payload = payload)

        val result = PingEchoHandler.handlePacket(packet)

        assertNull(result)
    }

    @Test
    fun `handlePacket returns null for payload shorter than MIN_PAYLOAD_SIZE`() {
        // Payload of 8 bytes (less than MIN_PAYLOAD_SIZE = 9)
        val payload = ByteArray(8)
        payload[0] = PingEchoHandler.PING_REQUEST
        val packet = buildTestPacket(sequenceNumber = 0, payload = payload)

        val result = PingEchoHandler.handlePacket(packet)

        assertNull(result)
    }

    @Test
    fun `handlePacket returns null for empty payload`() {
        val payload = ByteArray(0)
        val packet = buildTestPacket(sequenceNumber = 0, payload = payload)

        val result = PingEchoHandler.handlePacket(packet)

        assertNull(result)
    }

    @Test
    fun `handlePacket does not modify original packet rawBytes`() {
        val timestamp = System.nanoTime()
        val payload = buildPingRequestPayload(timestamp)
        val packet = buildTestPacket(sequenceNumber = 1, payload = payload)
        val originalRaw = packet.rawBytes.copyOf()

        PingEchoHandler.handlePacket(packet)

        assertArrayEquals(originalRaw, packet.rawBytes)
    }

    @Test
    fun `handlePacket with padding preserves all padding bytes`() {
        val timestamp = 9999999999999L
        val paddingSize = 100
        val payload = buildPingRequestPayload(timestamp, paddingSize)
        // Fill padding with recognizable pattern
        for (i in PingEchoHandler.MIN_PAYLOAD_SIZE until payload.size) {
            payload[i] = (i % 256).toByte()
        }
        val packet = buildTestPacket(sequenceNumber = 5, payload = payload)

        val result = PingEchoHandler.handlePacket(packet)!!

        // Verify padding bytes are preserved in the reply
        val replyPayloadOffset = TestPacketBuilder.HEADER_SIZE
        for (i in PingEchoHandler.MIN_PAYLOAD_SIZE until payload.size) {
            assertEquals(
                "Padding byte at index $i should be preserved",
                payload[i],
                result[replyPayloadOffset + i]
            )
        }
    }

    // ══════════════════════════════════════════════════════
    // Property 11: PingEchoHandler 回声正确性
    // ══════════════════════════════════════════════════════

    /**
     * Feature: bluetooth-socket-testing, Property 11: PingEchoHandler echo correctness
     *
     * 对任意合法 Ping Request TestPacket（payload 首字节为 0x01，payload 长度 ≥ 9），
     * handlePacket 返回的包解析后 payload 首字节为 0x02，时间戳一致，CRC 通过。
     *
     * **Validates: Requirements 7.9**
     */
    @Test
    fun `property - echo reply has correct type, preserved timestamp, and valid CRC`() {
        runBlocking {
            // Arb for padding size: 0 to 1000 (payload = 9 + padding)
            val arbPaddingSize = Arb.int(0..1000)
            val arbTimestamp = Arb.long(Long.MIN_VALUE..Long.MAX_VALUE)
            val arbSeqNum = Arb.long(0..0xFFFFFFFFL)

            checkAll(PropTestConfig(iterations = 100), arbSeqNum, arbTimestamp, arbPaddingSize) { seq, ts, padding ->
                val payload = buildPingRequestPayload(ts, padding)
                val packet = buildTestPacket(sequenceNumber = seq, payload = payload)

                val result = PingEchoHandler.handlePacket(packet)

                // Must return non-null for valid Ping Request
                assertNotNull("handlePacket should return non-null for valid Ping Request", result)
                result!!

                // 1. Payload 首字节应为 PING_REPLY (0x02)
                assertEquals(
                    "Reply type should be PING_REPLY",
                    PingEchoHandler.PING_REPLY,
                    extractTypeFromRaw(result)
                )

                // 2. 时间戳应与原始请求一致
                assertEquals(
                    "Timestamp should be preserved",
                    ts,
                    extractTimestampFromRaw(result)
                )

                // 3. CRC 应通过校验
                assertTrue(
                    "CRC should be valid in echo reply",
                    verifyCrc(result, payload.size)
                )

                // 4. 总大小应与原始包一致
                assertEquals(
                    "Reply size should match original",
                    packet.rawBytes.size,
                    result.size
                )

                // 5. 原始包不应被修改
                // (rawBytes is copied, so original should be unchanged)
                assertEquals(
                    "Original packet type should still be PING_REQUEST",
                    PingEchoHandler.PING_REQUEST,
                    packet.rawBytes[TestPacketBuilder.HEADER_SIZE]
                )
            }
        }
    }
}
