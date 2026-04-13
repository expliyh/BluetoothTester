package top.expli.bluetoothtester.bluetooth

import java.util.UUID

/**
 * UUID 短格式扩展/校验工具。
 * 支持 4 位（16-bit）、8 位（32-bit）十六进制短格式和完整 128 位 UUID 的解析与互转。
 */
object UuidHelper {

    private const val BASE_UUID_TEMPLATE = "0000%s-0000-1000-8000-00805F9B34FB"
    private const val BASE_UUID_32_TEMPLATE = "%s-0000-1000-8000-00805F9B34FB"

    /** Bluetooth Base UUID 的后缀部分（小写），用于 toShortForm 匹配 */
    private const val BASE_UUID_SUFFIX = "-0000-1000-8000-00805f9b34fb"

    /** 4 位十六进制正则 */
    private val HEX_4 = Regex("^[0-9a-fA-F]{4}$")

    /** 8 位十六进制正则 */
    private val HEX_8 = Regex("^[0-9a-fA-F]{8}$")

    /** 标准 128 位 UUID 正则（8-4-4-4-12，含连字符） */
    private val UUID_128 = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    )

    sealed interface UuidParseResult {
        data class Valid(val uuid: UUID) : UuidParseResult
        data class Invalid(val reason: String) : UuidParseResult
    }

    /**
     * 解析 4 位/8 位/完整 UUID 字符串，返回解析结果。
     *
     * - 4 位十六进制 → 扩展为 0000XXXX-0000-1000-8000-00805F9B34FB
     * - 8 位十六进制 → 扩展为 XXXXXXXX-0000-1000-8000-00805F9B34FB
     * - 完整 128 位 UUID（含连字符）→ 直接使用
     * - 其他格式 → Invalid
     */
    fun parse(input: String): UuidParseResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return UuidParseResult.Invalid("UUID 不能为空")
        }

        return when {
            HEX_4.matches(trimmed) -> {
                val full = String.format(BASE_UUID_TEMPLATE, trimmed.uppercase())
                UuidParseResult.Valid(UUID.fromString(full))
            }

            HEX_8.matches(trimmed) -> {
                val full = String.format(BASE_UUID_32_TEMPLATE, trimmed.uppercase())
                UuidParseResult.Valid(UUID.fromString(full))
            }

            UUID_128.matches(trimmed) -> {
                UuidParseResult.Valid(UUID.fromString(trimmed))
            }

            else -> {
                UuidParseResult.Invalid("无效的 UUID 格式，请输入 4 位/8 位十六进制或完整 128 位 UUID")
            }
        }
    }

    /**
     * 将完整 UUID 转为短格式显示。
     * 若为 Bluetooth Base UUID 则返回 4 位或 8 位短格式，否则返回原始字符串。
     *
     * - 0000XXXX-0000-1000-8000-00805F9B34FB → "XXXX"（4 位）
     * - XXXXXXXX-0000-1000-8000-00805F9B34FB → "XXXXXXXX"（8 位，前 4 位非 0000）
     * - 其他 → 原样返回
     */
    fun toShortForm(uuid: String): String {
        val lower = uuid.trim().lowercase()
        if (lower.length != 36) return uuid

        // 检查是否匹配 Base UUID 后缀（第 8 位之后）
        if (!lower.endsWith(BASE_UUID_SUFFIX)) return uuid

        // 提取前 8 位十六进制（去掉连字符前的部分）
        val prefix8 = lower.substring(0, 8)

        return if (prefix8.startsWith("0000")) {
            // 16-bit UUID：返回 4 位大写
            prefix8.substring(4).uppercase()
        } else {
            // 32-bit UUID：返回 8 位大写
            prefix8.uppercase()
        }
    }
}
