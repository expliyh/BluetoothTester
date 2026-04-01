package top.expli.bluetoothtester.adb

/**
 * ADB 命令参数文档。
 */
data class AdbParamDoc(
    val name: String,
    val type: String,
    val required: Boolean,
    val defaultValue: String?,
    val description: String
)

/**
 * ADB 命令文档。
 */
data class AdbCommandDoc(
    val command: String,
    val group: String,
    val description: String,
    val params: List<AdbParamDoc>,
    val exampleArgs: String,
    val returnExample: String
)

/**
 * 所有 ADB 命令的完整文档列表，与 AdbCommandReceiver 路由表保持同步。
 * AdbHelpScreen 直接引用此列表。
 */
object AdbCommandDocs {

    private const val ACTION = AdbCommandReceiver.ACTION
    private const val PREFIX = "adb shell am broadcast -W -a $ACTION"

    val commands: List<AdbCommandDoc> = listOf(
        // ─── SPP 操作 ───
        AdbCommandDoc(
            command = "spp.register",
            group = "SPP",
            description = "注册 SPP 设备",
            params = listOf(
                AdbParamDoc("name", "String", true, null, "设备名称"),
                AdbParamDoc("address", "String", true, null, "MAC 地址 (XX:XX:XX:XX:XX:XX)"),
                AdbParamDoc("uuid", "String", false, "00001101-0000-1000-8000-00805F9B34FB", "服务 UUID"),
                AdbParamDoc("role", "String", false, "client", "角色 (client/server)")
            ),
            exampleArgs = "$PREFIX --es command \"spp.register\" --es name \"MyDevice\" --es address \"AA:BB:CC:DD:EE:FF\"",
            returnExample = """{"success":true,"data":{"state":"registered"},"message":"设备已注册"}"""
        ),
        AdbCommandDoc(
            command = "spp.connect",
            group = "SPP",
            description = "连接已注册的 SPP 设备",
            params = listOf(
                AdbParamDoc("address", "String", true, null, "目标设备 MAC 地址")
            ),
            exampleArgs = "$PREFIX --es command \"spp.connect\" --es address \"AA:BB:CC:DD:EE:FF\"",
            returnExample = """{"success":true,"data":{"state":"connected"},"message":"已连接"}"""
        ),
        AdbCommandDoc(
            command = "spp.disconnect",
            group = "SPP",
            description = "断开 SPP 连接",
            params = listOf(
                AdbParamDoc("address", "String", true, null, "目标设备 MAC 地址")
            ),
            exampleArgs = "$PREFIX --es command \"spp.disconnect\" --es address \"AA:BB:CC:DD:EE:FF\"",
            returnExample = """{"success":true,"data":{"state":"disconnected"},"message":"已断开"}"""
        ),
        AdbCommandDoc(
            command = "spp.send",
            group = "SPP",
            description = "通过 SPP 连接发送数据",
            params = listOf(
                AdbParamDoc("address", "String", true, null, "目标设备 MAC 地址"),
                AdbParamDoc("data", "String", true, null, "发送的数据内容"),
                AdbParamDoc("hex", "String", false, "false", "是否为十六进制数据 (true/false)")
            ),
            exampleArgs = "$PREFIX --es command \"spp.send\" --es address \"AA:BB:CC:DD:EE:FF\" --es data \"Hello\"",
            returnExample = """{"success":true,"message":"数据已发送"}"""
        ),
        AdbCommandDoc(
            command = "spp.speed_test.start",
            group = "SPP",
            description = "启动 SPP 测速",
            params = listOf(
                AdbParamDoc("address", "String", true, null, "目标设备 MAC 地址"),
                AdbParamDoc("duration", "Int", false, "10", "测速时长 (秒)"),
                AdbParamDoc("payload_size", "Int", false, "1024", "每次发送的数据大小 (字节)"),
                AdbParamDoc("mode", "String", false, "send", "测速模式 (send/receive/duplex)")
            ),
            exampleArgs = "$PREFIX --es command \"spp.speed_test.start\" --es address \"AA:BB:CC:DD:EE:FF\" --es duration \"10\"",
            returnExample = """{"success":true,"data":{"state":"running"},"message":"测速已启动"}"""
        ),
        AdbCommandDoc(
            command = "spp.speed_test.stop",
            group = "SPP",
            description = "停止 SPP 测速",
            params = listOf(
                AdbParamDoc("address", "String", true, null, "目标设备 MAC 地址")
            ),
            exampleArgs = "$PREFIX --es command \"spp.speed_test.stop\" --es address \"AA:BB:CC:DD:EE:FF\"",
            returnExample = """{"success":true,"data":{"bytes_sent":102400,"duration_ms":10000,"speed_bps":81920},"message":"测速已停止"}"""
        ),

        // ─── GATT 操作 ───
        AdbCommandDoc(
            command = "gatt.connect",
            group = "GATT",
            description = "连接 GATT 设备",
            params = listOf(
                AdbParamDoc("address", "String", true, null, "目标 BLE 设备 MAC 地址")
            ),
            exampleArgs = "$PREFIX --es command \"gatt.connect\" --es address \"AA:BB:CC:DD:EE:FF\"",
            returnExample = """{"success":true,"data":{"state":"connected"},"message":"GATT 已连接"}"""
        ),
        AdbCommandDoc(
            command = "gatt.disconnect",
            group = "GATT",
            description = "断开 GATT 连接",
            params = listOf(
                AdbParamDoc("address", "String", true, null, "目标设备 MAC 地址")
            ),
            exampleArgs = "$PREFIX --es command \"gatt.disconnect\" --es address \"AA:BB:CC:DD:EE:FF\"",
            returnExample = """{"success":true,"data":{"state":"disconnected"},"message":"GATT 已断开"}"""
        ),
        AdbCommandDoc(
            command = "gatt.discover",
            group = "GATT",
            description = "触发 GATT 服务发现",
            params = listOf(
                AdbParamDoc("address", "String", true, null, "目标设备 MAC 地址")
            ),
            exampleArgs = "$PREFIX --es command \"gatt.discover\" --es address \"AA:BB:CC:DD:EE:FF\"",
            returnExample = """{"success":true,"data":{"services":[{"uuid":"0000180a-0000-1000-8000-00805f9b34fb","characteristics":[]}]},"message":"服务发现完成"}"""
        ),
        AdbCommandDoc(
            command = "gatt.read",
            group = "GATT",
            description = "读取 GATT 特征值",
            params = listOf(
                AdbParamDoc("address", "String", true, null, "目标设备 MAC 地址"),
                AdbParamDoc("service_uuid", "String", true, null, "服务 UUID"),
                AdbParamDoc("char_uuid", "String", true, null, "特征值 UUID")
            ),
            exampleArgs = "$PREFIX --es command \"gatt.read\" --es address \"AA:BB:CC:DD:EE:FF\" --es service_uuid \"0000180a-0000-1000-8000-00805f9b34fb\" --es char_uuid \"00002a29-0000-1000-8000-00805f9b34fb\"",
            returnExample = """{"success":true,"data":{"hex":"48656C6C6F","utf8":"Hello"},"message":"读取成功"}"""
        ),
        AdbCommandDoc(
            command = "gatt.write",
            group = "GATT",
            description = "写入 GATT 特征值",
            params = listOf(
                AdbParamDoc("address", "String", true, null, "目标设备 MAC 地址"),
                AdbParamDoc("service_uuid", "String", true, null, "服务 UUID"),
                AdbParamDoc("char_uuid", "String", true, null, "特征值 UUID"),
                AdbParamDoc("data", "String", true, null, "写入的数据"),
                AdbParamDoc("hex", "String", false, "false", "是否为十六进制数据 (true/false)"),
                AdbParamDoc("write_type", "String", false, "default", "写入类型 (default/no_response)")
            ),
            exampleArgs = "$PREFIX --es command \"gatt.write\" --es address \"AA:BB:CC:DD:EE:FF\" --es service_uuid \"0000180a-0000-1000-8000-00805f9b34fb\" --es char_uuid \"00002a29-0000-1000-8000-00805f9b34fb\" --es data \"Hello\"",
            returnExample = """{"success":true,"message":"写入成功"}"""
        ),
        AdbCommandDoc(
            command = "gatt.notify",
            group = "GATT",
            description = "启用/禁用 GATT 特征值通知",
            params = listOf(
                AdbParamDoc("address", "String", true, null, "目标设备 MAC 地址"),
                AdbParamDoc("service_uuid", "String", true, null, "服务 UUID"),
                AdbParamDoc("char_uuid", "String", true, null, "特征值 UUID"),
                AdbParamDoc("enable", "Boolean", true, null, "启用/禁用 (true/false)")
            ),
            exampleArgs = "$PREFIX --es command \"gatt.notify\" --es address \"AA:BB:CC:DD:EE:FF\" --es service_uuid \"0000180a-0000-1000-8000-00805f9b34fb\" --es char_uuid \"00002a29-0000-1000-8000-00805f9b34fb\" --es enable \"true\"",
            returnExample = """{"success":true,"message":"通知已启用"}"""
        ),
        AdbCommandDoc(
            command = "gatt.mtu",
            group = "GATT",
            description = "请求 MTU 协商",
            params = listOf(
                AdbParamDoc("address", "String", true, null, "目标设备 MAC 地址"),
                AdbParamDoc("mtu", "Int", true, null, "目标 MTU 值 (23-517)")
            ),
            exampleArgs = "$PREFIX --es command \"gatt.mtu\" --es address \"AA:BB:CC:DD:EE:FF\" --es mtu \"512\"",
            returnExample = """{"success":true,"data":{"mtu":512},"message":"MTU 协商成功"}"""
        ),

        // ─── L2CAP 操作 ───
        AdbCommandDoc(
            command = "l2cap.connect",
            group = "L2CAP",
            description = "以客户端模式连接 L2CAP 通道",
            params = listOf(
                AdbParamDoc("address", "String", true, null, "目标设备 MAC 地址"),
                AdbParamDoc("psm", "Int", true, null, "PSM 值 (1-65535)")
            ),
            exampleArgs = "$PREFIX --es command \"l2cap.connect\" --es address \"AA:BB:CC:DD:EE:FF\" --es psm \"128\"",
            returnExample = """{"success":true,"data":{"state":"connected","psm":128},"message":"L2CAP 已连接"}"""
        ),
        AdbCommandDoc(
            command = "l2cap.listen",
            group = "L2CAP",
            description = "以服务端模式启动 L2CAP 监听",
            params = emptyList(),
            exampleArgs = "$PREFIX --es command \"l2cap.listen\"",
            returnExample = """{"success":true,"data":{"state":"listening","psm":192},"message":"L2CAP 监听中，PSM: 192"}"""
        ),
        AdbCommandDoc(
            command = "l2cap.disconnect",
            group = "L2CAP",
            description = "断开 L2CAP 连接",
            params = emptyList(),
            exampleArgs = "$PREFIX --es command \"l2cap.disconnect\"",
            returnExample = """{"success":true,"data":{"state":"disconnected"},"message":"L2CAP 已断开"}"""
        ),
        AdbCommandDoc(
            command = "l2cap.send",
            group = "L2CAP",
            description = "通过 L2CAP 通道发送数据",
            params = listOf(
                AdbParamDoc("data", "String", true, null, "发送的数据内容"),
                AdbParamDoc("hex", "String", false, "false", "是否为十六进制数据 (true/false)")
            ),
            exampleArgs = "$PREFIX --es command \"l2cap.send\" --es data \"Hello\"",
            returnExample = """{"success":true,"message":"数据已发送"}"""
        ),
        AdbCommandDoc(
            command = "l2cap.speed_test.start",
            group = "L2CAP",
            description = "启动 L2CAP 测速",
            params = listOf(
                AdbParamDoc("duration", "Int", false, "10", "测速时长 (秒)"),
                AdbParamDoc("payload_size", "Int", false, "1024", "每次发送的数据大小 (字节)"),
                AdbParamDoc("mode", "String", false, "send", "测速模式 (send/receive/duplex)")
            ),
            exampleArgs = "$PREFIX --es command \"l2cap.speed_test.start\" --es duration \"10\"",
            returnExample = """{"success":true,"data":{"state":"running"},"message":"L2CAP 测速已启动"}"""
        ),
        AdbCommandDoc(
            command = "l2cap.speed_test.stop",
            group = "L2CAP",
            description = "停止 L2CAP 测速",
            params = emptyList(),
            exampleArgs = "$PREFIX --es command \"l2cap.speed_test.stop\"",
            returnExample = """{"success":true,"data":{"bytes_sent":102400,"duration_ms":10000,"speed_bps":81920},"message":"L2CAP 测速已停止"}"""
        ),

        // ─── 扫描/广播操作 ───
        AdbCommandDoc(
            command = "scan.ble.start",
            group = "扫描/广播",
            description = "启动 BLE 扫描",
            params = listOf(
                AdbParamDoc("name", "String", false, null, "设备名称关键字过滤"),
                AdbParamDoc("uuid", "String", false, null, "服务 UUID 过滤"),
                AdbParamDoc("rssi", "Int", false, null, "RSSI 阈值过滤 (-127 到 0)")
            ),
            exampleArgs = "$PREFIX --es command \"scan.ble.start\" --es name \"MyDevice\"",
            returnExample = """{"success":true,"data":{"state":"scanning"},"message":"BLE 扫描已启动"}"""
        ),
        AdbCommandDoc(
            command = "scan.ble.stop",
            group = "扫描/广播",
            description = "停止 BLE 扫描",
            params = emptyList(),
            exampleArgs = "$PREFIX --es command \"scan.ble.stop\"",
            returnExample = """{"success":true,"data":{"state":"stopped"},"message":"BLE 扫描已停止"}"""
        ),
        AdbCommandDoc(
            command = "scan.classic.start",
            group = "扫描/广播",
            description = "启动经典蓝牙扫描",
            params = emptyList(),
            exampleArgs = "$PREFIX --es command \"scan.classic.start\"",
            returnExample = """{"success":true,"data":{"state":"scanning"},"message":"经典蓝牙扫描已启动"}"""
        ),
        AdbCommandDoc(
            command = "scan.classic.stop",
            group = "扫描/广播",
            description = "停止经典蓝牙扫描",
            params = emptyList(),
            exampleArgs = "$PREFIX --es command \"scan.classic.stop\"",
            returnExample = """{"success":true,"data":{"state":"stopped"},"message":"经典蓝牙扫描已停止"}"""
        ),
        AdbCommandDoc(
            command = "scan.results",
            group = "扫描/广播",
            description = "查询当前扫描结果",
            params = listOf(
                AdbParamDoc("type", "String", false, "all", "结果类型 (ble/classic/all)")
            ),
            exampleArgs = "$PREFIX --es command \"scan.results\" --es type \"ble\"",
            returnExample = """{"success":true,"data":{"devices":[{"address":"AA:BB:CC:DD:EE:FF","name":"MyDevice","rssi":-65,"type":"BLE"}]},"message":"扫描结果"}"""
        ),
        AdbCommandDoc(
            command = "advertise.start",
            group = "扫描/广播",
            description = "启动 BLE 广播",
            params = listOf(
                AdbParamDoc("mode", "Int", false, "1", "广播模式 (0=LOW_POWER, 1=BALANCED, 2=LOW_LATENCY)"),
                AdbParamDoc("tx_power", "Int", false, "1", "TX 功率 (0=ULTRA_LOW, 1=LOW, 2=MEDIUM, 3=HIGH)"),
                AdbParamDoc("connectable", "Boolean", false, "true", "是否可连接 (true/false)"),
                AdbParamDoc("service_uuid", "String", false, null, "广播的服务 UUID"),
                AdbParamDoc("name", "Boolean", false, "true", "是否包含设备名称 (true/false)")
            ),
            exampleArgs = "$PREFIX --es command \"advertise.start\" --es mode \"1\" --es connectable \"true\"",
            returnExample = """{"success":true,"data":{"state":"advertising"},"message":"BLE 广播已启动"}"""
        ),
        AdbCommandDoc(
            command = "advertise.stop",
            group = "扫描/广播",
            description = "停止 BLE 广播",
            params = emptyList(),
            exampleArgs = "$PREFIX --es command \"advertise.stop\"",
            returnExample = """{"success":true,"data":{"state":"stopped"},"message":"BLE 广播已停止"}"""
        ),

        // ─── 通用操作 ───
        AdbCommandDoc(
            command = "status",
            group = "通用",
            description = "查询指定模块的当前状态",
            params = listOf(
                AdbParamDoc("module", "String", true, null, "模块名称 (spp/gatt/l2cap/advertiser/scan)")
            ),
            exampleArgs = "$PREFIX --es command \"status\" --es module \"spp\"",
            returnExample = """{"success":true,"data":{"module":"spp","state":"connected","devices":["AA:BB:CC:DD:EE:FF"]},"message":"SPP 模块状态"}"""
        ),
        AdbCommandDoc(
            command = "devices",
            group = "通用",
            description = "查询已注册设备列表",
            params = emptyList(),
            exampleArgs = "$PREFIX --es command \"devices\"",
            returnExample = """{"success":true,"data":{"devices":[{"address":"AA:BB:CC:DD:EE:FF","name":"MyDevice","state":"connected"}]},"message":"设备列表"}"""
        ),
        AdbCommandDoc(
            command = "chat.clear",
            group = "通用",
            description = "清除指定设备的聊天记录",
            params = listOf(
                AdbParamDoc("address", "String", true, null, "目标设备 MAC 地址")
            ),
            exampleArgs = "$PREFIX --es command \"chat.clear\" --es address \"AA:BB:CC:DD:EE:FF\"",
            returnExample = """{"success":true,"message":"聊天记录已清除"}"""
        )
    )
}
