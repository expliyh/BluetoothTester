package top.expli.bluetoothtester.adb

import top.expli.bluetoothtester.model.AdbResponse

/**
 * ADB 命令参数校验器。
 * 根据命令路由表检查必填参数，校验 MAC 地址格式、UUID 格式、数值范围等。
 * 返回 null 表示校验通过，返回 AdbResponse 表示校验失败。
 */
object AdbParamValidator {

    private val MAC_REGEX = Regex("^[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}$")
    private val UUID_REGEX = Regex(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
    )

    /** 每个命令的必填参数列表 */
    private val requiredParams: Map<String, List<String>> = mapOf(
        "spp.register" to listOf("name", "address"),
        "spp.connect" to listOf("address"),
        "spp.disconnect" to listOf("address"),
        "spp.send" to listOf("address", "data"),
        "spp.speed_test.start" to listOf("address"),
        "spp.speed_test.stop" to listOf("address"),
        "gatt.connect" to listOf("address"),
        "gatt.disconnect" to listOf("address"),
        "gatt.discover" to listOf("address"),
        "gatt.read" to listOf("address", "service_uuid", "char_uuid"),
        "gatt.write" to listOf("address", "service_uuid", "char_uuid", "data"),
        "gatt.notify" to listOf("address", "service_uuid", "char_uuid", "enable"),
        "gatt.mtu" to listOf("address", "mtu"),
        "l2cap.connect" to listOf("address", "psm"),
        "l2cap.listen" to emptyList(),
        "l2cap.disconnect" to emptyList(),
        "l2cap.send" to listOf("data"),
        "l2cap.speed_test.start" to emptyList(),
        "l2cap.speed_test.stop" to emptyList(),
        "scan.ble.start" to emptyList(),
        "scan.ble.stop" to emptyList(),
        "scan.classic.start" to emptyList(),
        "scan.classic.stop" to emptyList(),
        "scan.results" to emptyList(),
        "advertise.start" to emptyList(),
        "advertise.stop" to emptyList(),
        "status" to listOf("module"),
        "devices" to emptyList(),
        "chat.clear" to listOf("address"),
    )

    /** 包含 MAC 地址的参数名 */
    private val macParams = setOf("address")

    /** 包含 UUID 的参数名 */
    private val uuidParams = setOf("uuid", "service_uuid", "char_uuid")

    fun validate(command: String, params: Map<String, String>): AdbResponse? {
        val required = requiredParams[command]
            ?: return AdbResponse(
                success = false,
                error = "unknown_command",
                message = "未知命令: $command"
            )

        // 检查必填参数
        for (param in required) {
            if (params[param].isNullOrBlank()) {
                return AdbResponse(
                    success = false,
                    error = "missing_param",
                    message = "缺少必填参数: $param (命令: $command)"
                )
            }
        }

        // 校验 MAC 地址格式
        for (key in macParams) {
            val value = params[key] ?: continue
            if (!MAC_REGEX.matches(value)) {
                return AdbResponse(
                    success = false,
                    error = "invalid_mac",
                    message = "MAC 地址格式错误: $value (应为 XX:XX:XX:XX:XX:XX)"
                )
            }
        }

        // 校验 UUID 格式
        for (key in uuidParams) {
            val value = params[key] ?: continue
            if (!UUID_REGEX.matches(value)) {
                return AdbResponse(
                    success = false,
                    error = "invalid_uuid",
                    message = "UUID 格式错误: $value (应为 xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx)"
                )
            }
        }

        // 校验数值参数
        params["mtu"]?.let { mtuStr ->
            val mtu = mtuStr.toIntOrNull()
            if (mtu == null || mtu < 23 || mtu > 517) {
                return AdbResponse(
                    success = false,
                    error = "invalid_mtu",
                    message = "MTU 值无效: $mtuStr (应为 23-517 的整数)"
                )
            }
        }

        params["psm"]?.let { psmStr ->
            val psm = psmStr.toIntOrNull()
            if (psm == null || psm < 1 || psm > 65535) {
                return AdbResponse(
                    success = false,
                    error = "invalid_psm",
                    message = "PSM 值无效: $psmStr (应为 1-65535 的整数)"
                )
            }
        }

        params["duration"]?.let { durationStr ->
            val duration = durationStr.toIntOrNull()
            if (duration == null || duration < 1) {
                return AdbResponse(
                    success = false,
                    error = "invalid_duration",
                    message = "时长值无效: $durationStr (应为正整数，单位秒)"
                )
            }
        }

        params["payload_size"]?.let { sizeStr ->
            val size = sizeStr.toIntOrNull()
            if (size == null || size < 1 || size > 65535) {
                return AdbResponse(
                    success = false,
                    error = "invalid_payload_size",
                    message = "Payload 大小无效: $sizeStr (应为 1-65535 的整数)"
                )
            }
        }

        params["rssi"]?.let { rssiStr ->
            val rssi = rssiStr.toIntOrNull()
            if (rssi == null || rssi < -127 || rssi > 0) {
                return AdbResponse(
                    success = false,
                    error = "invalid_rssi",
                    message = "RSSI 阈值无效: $rssiStr (应为 -127 到 0 的整数)"
                )
            }
        }

        params["enable"]?.let { enableStr ->
            if (enableStr !in listOf("true", "false", "1", "0")) {
                return AdbResponse(
                    success = false,
                    error = "invalid_enable",
                    message = "enable 参数无效: $enableStr (应为 true/false 或 1/0)"
                )
            }
        }

        return null // 校验通过
    }
}
