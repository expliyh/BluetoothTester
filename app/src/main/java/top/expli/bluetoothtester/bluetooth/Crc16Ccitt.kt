package top.expli.bluetoothtester.bluetooth

/**
 * CRC-16/CCITT 查表法公共组件。
 * 多项式 0x1021，初始值 0xFFFF，256 项查找表。
 * 不依赖任何蓝牙 API，纯算法实现。
 */
object Crc16Ccitt {

    private val TABLE = ShortArray(256)

    init {
        for (i in 0 until 256) {
            var crc = (i shl 8).toUShort()
            for (bit in 0 until 8) {
                crc = if (crc.toInt() and 0x8000 != 0) {
                    ((crc.toInt() shl 1) xor 0x1021).toUShort()
                } else {
                    (crc.toInt() shl 1).toUShort()
                }
            }
            TABLE[i] = (crc.toInt() and 0xFFFF).toShort()
        }
    }

    /**
     * 计算 CRC-16/CCITT，返回 UShort（大端序写入时高字节在前）。
     * @param data 输入字节数组
     * @param offset 起始偏移
     * @param length 计算长度
     */
    fun compute(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): UShort {
        return update(0xFFFFu, data, offset, length)
    }

    /**
     * 在已有 CRC 基础上继续计算（用于分段计算）。
     * @param crc 当前 CRC 值
     * @param data 输入字节数组
     * @param offset 起始偏移
     * @param length 计算长度
     */
    fun update(crc: UShort, data: ByteArray, offset: Int = 0, length: Int = data.size - offset): UShort {
        var result = crc.toInt() and 0xFFFF
        for (i in offset until offset + length) {
            val index = ((result shr 8) xor (data[i].toInt() and 0xFF)) and 0xFF
            result = ((result shl 8) and 0xFFFF) xor (TABLE[index].toInt() and 0xFFFF)
        }
        return result.toUShort()
    }
}
