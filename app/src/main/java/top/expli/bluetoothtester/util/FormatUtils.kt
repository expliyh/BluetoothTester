package top.expli.bluetoothtester.util

/** 通用的人类可读单位格式化。 */
private fun formatHumanReadable(
    value: Double,
    units: Array<String>,
    decimals: Int,
    separator: String
): String {
    var v = value
    var idx = 0
    while (v >= 1024 && idx < units.lastIndex) {
        v /= 1024
        idx++
    }
    return "%.${decimals}f${separator}${units[idx]}".format(v)
}

/**
 * 格式化比特率/字节率为人类可读字符串。
 * @param bps 每秒字节数
 * @param decimals 小数位数，默认 2
 */
fun formatBps(bps: Double, decimals: Int = 2): String =
    formatHumanReadable(bps, arrayOf("B/s", "KB/s", "MB/s", "GB/s"), decimals, " ")

/** 紧凑格式（1 位小数，无空格），适用于卡片摘要等空间受限场景。 */
fun formatBpsCompact(bps: Double): String =
    formatHumanReadable(bps, arrayOf("B/s", "KB/s", "MB/s", "GB/s"), 1, "")

/** 格式化毫秒为可读时间字符串（如 "1.5s"、"2m30.0s"）。 */
fun formatElapsedMs(ms: Long): String {
    if (ms <= 0) return "0.0s"
    val seconds = ms / 1000.0
    return if (seconds < 60) {
        "%.1fs".format(seconds)
    } else {
        val m = (seconds / 60).toInt()
        val s = seconds % 60
        "${m}m${"%.1f".format(s)}s"
    }
}

/** 格式化字节数为人类可读字符串（如 "1.50 MB"）。 */
fun formatBytes(bytes: Long): String =
    formatHumanReadable(bytes.toDouble(), arrayOf("B", "KB", "MB", "GB"), 2, " ")
