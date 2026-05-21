package top.expli.bluetoothtester.adb

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import top.expli.bluetoothtester.model.AdbResponse

/**
 * ADB 命令协议（Control Socket JSON 行协议）。
 * 每行一个 JSON 对象，换行符分隔。
 */
@Serializable
data class AdbJsonRequest(
    val command: String,
    val params: Map<String, String> = emptyMap()
)

/**
 * ADB 命令处理器接口。
 * 由 SppViewModel 实现并注册到 AdbCommandRouter，使 ADB Control Socket 命令能调用真实的蓝牙操作。
 */
interface AdbCommandHandler {
    suspend fun handleSppRegister(params: Map<String, String>): AdbResponse
    suspend fun handleSppConnect(params: Map<String, String>): AdbResponse
    suspend fun handleSppDisconnect(params: Map<String, String>): AdbResponse
    suspend fun handleSppSend(params: Map<String, String>): AdbResponse
    suspend fun handleSppSpeedTestStart(params: Map<String, String>): AdbResponse
    suspend fun handleSppSpeedTestStop(params: Map<String, String>): AdbResponse
    suspend fun handleStatus(params: Map<String, String>): AdbResponse
    suspend fun handleDevices(): AdbResponse
    suspend fun handleChatClear(params: Map<String, String>): AdbResponse
}

/**
 * 共享 ADB 命令路由器。
 * 从 AdbCommandReceiver 提取，供 BroadcastReceiver 和 Control Socket Server 两个通道共用。
 */
object AdbCommandRouter {

    private val json = Json { prettyPrint = false; encodeDefaults = true }

    /** 命令处理器，由 SppViewModel 在初始化时注册 */
    @Volatile
    var handler: AdbCommandHandler? = null

    /**
     * 根据 command 字符串分发到对应的处理逻辑。
     */
    suspend fun route(command: String, params: Map<String, String>): AdbResponse {
        val h = handler
        return when {
            // ─── SPP 操作 ───
            command == "spp.register" -> h?.handleSppRegister(params) ?: handlerNotReady()
            command == "spp.connect" -> h?.handleSppConnect(params) ?: handlerNotReady()
            command == "spp.disconnect" -> h?.handleSppDisconnect(params) ?: handlerNotReady()
            command == "spp.send" -> h?.handleSppSend(params) ?: handlerNotReady()
            command == "spp.speed_test.start" -> h?.handleSppSpeedTestStart(params) ?: handlerNotReady()
            command == "spp.speed_test.stop" -> h?.handleSppSpeedTestStop(params) ?: handlerNotReady()

            // ─── GATT 操作 ───
            command == "gatt.connect" -> stub(command, "GATT 连接", params)
            command == "gatt.disconnect" -> stub(command, "GATT 断开", params)
            command == "gatt.discover" -> stub(command, "GATT 服务发现", params)
            command == "gatt.read" -> stub(command, "GATT 读取特征值", params)
            command == "gatt.write" -> stub(command, "GATT 写入特征值", params)
            command == "gatt.notify" -> stub(command, "GATT 通知设置", params)
            command == "gatt.mtu" -> stub(command, "GATT MTU 协商", params)

            // ─── L2CAP 操作 ───
            command == "l2cap.connect" -> stub(command, "L2CAP 连接", params)
            command == "l2cap.listen" -> stub(command, "L2CAP 监听", params)
            command == "l2cap.disconnect" -> stub(command, "L2CAP 断开", params)
            command == "l2cap.send" -> stub(command, "L2CAP 发送数据", params)
            command == "l2cap.speed_test.start" -> stub(command, "L2CAP 测速启动", params)
            command == "l2cap.speed_test.stop" -> stub(command, "L2CAP 测速停止", params)

            // ─── 扫描/广播操作 ───
            command == "scan.ble.start" -> stub(command, "BLE 扫描启动", params)
            command == "scan.ble.stop" -> stub(command, "BLE 扫描停止", params)
            command == "scan.classic.start" -> stub(command, "经典蓝牙扫描启动", params)
            command == "scan.classic.stop" -> stub(command, "经典蓝牙扫描停止", params)
            command == "scan.results" -> stub(command, "扫描结果查询", params)
            command == "advertise.start" -> stub(command, "BLE 广播启动", params)
            command == "advertise.stop" -> stub(command, "BLE 广播停止", params)

            // ─── 通用操作 ───
            command == "status" -> h?.handleStatus(params) ?: handlerNotReady()
            command == "devices" -> h?.handleDevices() ?: handlerNotReady()
            command == "chat.clear" -> h?.handleChatClear(params) ?: handlerNotReady()

            // ─── 内部调试命令 ───
            command == "ping" -> AdbResponse(
                success = true,
                data = buildJsonObject {
                    put("pong", JsonPrimitive(true))
                    put("timestamp", JsonPrimitive(System.currentTimeMillis()))
                },
                message = "pong"
            )

            else -> AdbResponse(
                success = false,
                error = "unknown_command",
                message = "未知命令: $command"
            )
        }
    }

    private fun handlerNotReady() = AdbResponse(
        success = false,
        error = "handler_not_ready",
        message = "命令处理器未就绪，请打开 SPP 页面"
    )

    /**
     * Stub 响应：返回命令已接收但尚未实现实际逻辑的占位响应。
     */
    private fun stub(
        command: String,
        description: String,
        params: Map<String, String>
    ): AdbResponse {
        return AdbResponse(
            success = true,
            data = buildJsonObject {
                put("command", JsonPrimitive(command))
                put("description", JsonPrimitive(description))
                put("stub", JsonPrimitive(true))
                for ((key, value) in params) {
                    put(key, JsonPrimitive(value))
                }
            },
            message = "$description - 命令已接收 (stub)"
        )
    }

    /**
     * 将 AdbResponse 序列化为 JSON 字符串（用于 Control Socket 响应）。
     */
    fun toJson(response: AdbResponse): String = json.encodeToString(AdbResponse.serializer(), response)

    /**
     * 解析 JSON 请求字符串为 AdbJsonRequest（用于 Control Socket 请求）。
     */
    fun parseRequest(jsonLine: String): AdbJsonRequest? {
        return try {
            json.decodeFromString(AdbJsonRequest.serializer(), jsonLine)
        } catch (_: Exception) {
            null
        }
    }
}
