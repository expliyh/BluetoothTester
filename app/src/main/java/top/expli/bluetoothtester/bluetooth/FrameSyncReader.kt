package top.expli.bluetoothtester.bluetooth

import java.io.InputStream

/**
 * 协议无关的帧同步读取器。包装 InputStream，维护跨 read 调用的解析状态。
 * 支持 CRC 和 NoCRC 两种模式。
 *
 * 状态机：SCAN_DELIMITER → READ_HEADER → READ_PAYLOAD → READ_CRC → EMIT_PACKET
 * NoCRC 模式跳过 READ_CRC 阶段。
 *
 * 不依赖 SPP 或 L2CAP 具体实现，纯读取组件，职责单一。
 *
 * 线程约束：readPacket() 是阻塞调用，必须在 Dispatchers.IO 上调用。
 * 取消通过关闭底层 socket 触发 IOException 实现。
 */
class FrameSyncReader(
    private val input: InputStream,
    private val withCrc: Boolean = true
) {
    private enum class State {
        SCAN_DELIMITER,
        READ_HEADER,
        READ_PAYLOAD,
        READ_CRC,
        EMIT_PACKET
    }

    private var state: State = State.SCAN_DELIMITER
    private var prevByte: Int = -1

    // Header: seq(4) + len(2) = 6 bytes
    private val headerBuf = ByteArray(6)
    private var headerPos = 0

    // Raw bytes buffer: pre-allocated after header is read (exact size known)
    private var rawBuf: ByteArray? = null
    private var rawPos = 0  // write position in rawBuf

    private var payloadLen = 0
    private var payloadPos = 0

    private val crcBuf = ByteArray(2)
    private var crcPos = 0

    /**
     * 从 InputStream 读取并解析下一个完整数据包。
     * 阻塞直到读到一个有效包或流结束。
     *
     * @return 解析成功的 TestPacket，流结束返回 null
     * @throws java.io.IOException 底层 IO 异常向上抛出
     */
    fun readPacket(): TestPacket? {
        while (true) {
            when (state) {
                State.SCAN_DELIMITER -> {
                    val b = input.read()
                    if (b == -1) return null
                    if (prevByte == 0xAA && b == 0x55) {
                        // Found delimiter 0xAA55, start reading header
                        headerPos = 0
                        state = State.READ_HEADER
                    }
                    prevByte = b
                }

                State.READ_HEADER -> {
                    val remaining = 6 - headerPos
                    val n = input.read(headerBuf, headerPos, remaining)
                    if (n == -1) return null
                    headerPos += n
                    if (headerPos == 6) {
                        // Decode payload length (last 2 bytes of header, big-endian)
                        val pLen = ((headerBuf[4].toInt() and 0xFF) shl 8) or
                                (headerBuf[5].toInt() and 0xFF)
                        if (pLen < 1 || pLen > TestPacketBuilder.MAX_PAYLOAD_SIZE) {
                            // False delimiter: length out of range
                            resetToScan()
                            continue
                        }
                        payloadLen = pLen
                        payloadPos = 0

                        // Pre-allocate rawBuf with exact size: delimiter(2) + header(6) + payload + CRC(0 or 2)
                        val totalRaw = 2 + 6 + payloadLen + if (withCrc) 2 else 0
                        rawBuf = ByteArray(totalRaw)
                        rawPos = 0

                        // Back-fill delimiter and header into rawBuf
                        val raw = rawBuf!!
                        raw[rawPos++] = 0xAA.toByte()
                        raw[rawPos++] = 0x55
                        System.arraycopy(headerBuf, 0, raw, rawPos, 6)
                        rawPos += 6

                        state = State.READ_PAYLOAD
                    }
                }

                State.READ_PAYLOAD -> {
                    val raw = rawBuf ?: run { resetToScan(); continue }
                    val remaining = payloadLen - payloadPos
                    // Read directly into rawBuf at the correct offset
                    val n = input.read(raw, rawPos, remaining)
                    if (n == -1) return null
                    rawPos += n
                    payloadPos += n
                    if (payloadPos == payloadLen) {
                        if (withCrc) {
                            crcPos = 0
                            state = State.READ_CRC
                        } else {
                            state = State.EMIT_PACKET
                        }
                    }
                }

                State.READ_CRC -> {
                    val raw = rawBuf ?: run { resetToScan(); continue }
                    val remaining = 2 - crcPos
                    val n = input.read(crcBuf, crcPos, remaining)
                    if (n == -1) return null
                    // Copy CRC bytes into rawBuf
                    System.arraycopy(crcBuf, crcPos, raw, rawPos, n)
                    rawPos += n
                    crcPos += n
                    if (crcPos == 2) {
                        // Verify CRC: compute over rawBuf from delimiter to payload end (exclude CRC)
                        val crcDataLen = 2 + 6 + payloadLen  // delimiter + header + payload
                        val computed = Crc16Ccitt.compute(raw, 0, crcDataLen)
                        val received = ((crcBuf[0].toInt() and 0xFF) shl 8) or
                                (crcBuf[1].toInt() and 0xFF)
                        if (computed.toInt() != received) {
                            // CRC mismatch: false delimiter
                            resetToScan()
                            continue
                        }
                        state = State.EMIT_PACKET
                    }
                }

                State.EMIT_PACKET -> {
                    val raw = rawBuf ?: run { resetToScan(); continue }

                    // Decode sequence number: 4 bytes big-endian from headerBuf[0..3]
                    val seq = ((headerBuf[0].toLong() and 0xFF) shl 24) or
                            ((headerBuf[1].toLong() and 0xFF) shl 16) or
                            ((headerBuf[2].toLong() and 0xFF) shl 8) or
                            (headerBuf[3].toLong() and 0xFF)

                    // Extract payload from rawBuf (offset 8, length payloadLen)
                    val payload = ByteArray(payloadLen)
                    System.arraycopy(raw, 8, payload, 0, payloadLen)

                    val packet = TestPacket(
                        sequenceNumber = seq and 0xFFFFFFFFL,
                        payload = payload,
                        crcValid = true, // CRC mode reaches here only if valid; NoCRC always true
                        rawBytes = raw
                    )

                    // Reset for next packet
                    state = State.SCAN_DELIMITER
                    prevByte = -1
                    rawBuf = null

                    return packet
                }
            }
        }
    }

    /**
     * 重置状态机所有状态（用于模式切换时）。
     */
    fun reset() {
        resetToScan()
    }

    /**
     * 假定界符回退：重置到 SCAN_DELIMITER 状态。
     * prevByte = -1，从下一个字节开始全新扫描。
     */
    private fun resetToScan() {
        state = State.SCAN_DELIMITER
        prevByte = -1
        headerPos = 0
        payloadPos = 0
        payloadLen = 0
        crcPos = 0
        rawBuf = null
        rawPos = 0
    }
}
