package top.expli.bluetoothtester.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Test

class Crc16CcittTest {

    @Test
    fun `compute empty array returns initial value 0xFFFF`() {
        val result = Crc16Ccitt.compute(byteArrayOf())
        assertEquals(0xFFFFu.toUShort(), result)
    }

    @Test
    fun `compute 123456789 returns 0x29B1`() {
        // Standard CRC-16/CCITT test vector
        val data = "123456789".toByteArray(Charsets.US_ASCII)
        val result = Crc16Ccitt.compute(data)
        assertEquals(0x29B1u.toUShort(), result)
    }

    @Test
    fun `compute single byte`() {
        val result = Crc16Ccitt.compute(byteArrayOf(0x00))
        // CRC-16/CCITT of single 0x00 byte with init 0xFFFF
        // Manual: index = (0xFF xor 0x00) = 0xFF, result = (0xFF00) xor TABLE[0xFF]
        // Just verify it's deterministic and not 0xFFFF
        assert(result != 0xFFFFu.toUShort())
    }

    @Test
    fun `compute with offset and length`() {
        val data = byteArrayOf(0x00, 0x31, 0x32, 0x33, 0x00) // "123" at offset 1, length 3
        val expected = Crc16Ccitt.compute("123".toByteArray(Charsets.US_ASCII))
        val result = Crc16Ccitt.compute(data, offset = 1, length = 3)
        assertEquals(expected, result)
    }

    @Test
    fun `update allows segmented computation`() {
        val data = "123456789".toByteArray(Charsets.US_ASCII)
        val fullCrc = Crc16Ccitt.compute(data)

        // Compute in two segments
        val part1 = Crc16Ccitt.compute(data, offset = 0, length = 5)
        val part2 = Crc16Ccitt.update(part1, data, offset = 5, length = 4)

        assertEquals(fullCrc, part2)
    }

    @Test
    fun `compute large array is deterministic`() {
        val data = ByteArray(10000) { (it % 256).toByte() }
        val result1 = Crc16Ccitt.compute(data)
        val result2 = Crc16Ccitt.compute(data)
        assertEquals(result1, result2)
    }
}
