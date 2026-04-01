package top.expli.bluetoothtester.adb

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import top.expli.bluetoothtester.model.AdbResponse

/**
 * 统一 ADB 命令 BroadcastReceiver。
 * 所有 ADB 命令通过 Intent Extra 中的 "command" 字段区分。
 * 支持有序广播返回结果。
 */
class AdbCommandReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION = "top.expli.bluetoothtester.action.ADB_COMMAND"
        const val EXTRA_COMMAND = "command"
        const val LOG_TAG = "BtTesterADB"
    }

    private val json = Json { prettyPrint = false; encodeDefaults = true }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return

        // 检查 App 前台状态
        if (!AppStateChecker.isAppReady(context)) {
            val error = AdbResponse(
                success = false,
                error = "app_not_ready",
                message = "请先启动应用并保持在前台"
            )
            sendResult(error)
            return
        }

        // 解析 command
        val command = intent.getStringExtra(EXTRA_COMMAND)
        if (command.isNullOrBlank()) {
            val error = AdbResponse(
                success = false,
                error = "missing_command",
                message = "缺少 command 参数"
            )
            sendResult(error)
            return
        }

        // 解析所有其他 extras 作为参数
        val params = mutableMapOf<String, String>()
        intent.extras?.let { bundle ->
            for (key in bundle.keySet()) {
                if (key == EXTRA_COMMAND) continue
                bundle.getString(key)?.let { params[key] = it }
            }
        }

        // 校验参数
        val validationError = AdbParamValidator.validate(command, params)
        if (validationError != null) {
            sendResult(validationError)
            return
        }

        // 使用 goAsync() 进行异步执行
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val response = AdbCommandQueue.execute {
                    routeCommand(command, params)
                }
                val jsonResult = json.encodeToString(response)
                pendingResult.setResultCode(
                    if (response.success) Activity.RESULT_OK else Activity.RESULT_CANCELED
                )
                pendingResult.setResultData(jsonResult)
                Log.i(LOG_TAG, jsonResult)
            } catch (e: Exception) {
                val error = AdbResponse(
                    success = false,
                    error = "internal_error",
                    message = "内部错误: ${e.message}"
                )
                val jsonResult = json.encodeToString(error)
                pendingResult.setResultCode(Activity.RESULT_CANCELED)
                pendingResult.setResultData(jsonResult)
                Log.i(LOG_TAG, jsonResult)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * 命令路由：根据 command 字符串分发到对应的处理逻辑。
     * 当前返回 stub 响应，实际 ViewModel 集成将在应用运行时完成。
     */
    private suspend fun routeCommand(command: String, params: Map<String, String>): AdbResponse {
        return when {
            // SPP 操作
            command == "spp.register" -> stubResponse(command, "SPP 设备注册", params)
            command == "spp.connect" -> stubResponse(command, "SPP 连接", params)
            command == "spp.disconnect" -> stubResponse(command, "SPP 断开", params)
            command == "spp.send" -> stubResponse(command, "SPP 发送数据", params)
            command == "spp.speed_test.start" -> stubResponse(command, "SPP 测速启动", params)
            command == "spp.speed_test.stop" -> stubResponse(command, "SPP 测速停止", params)

            // GATT 操作
            command == "gatt.connect" -> stubResponse(command, "GATT 连接", params)
            command == "gatt.disconnect" -> stubResponse(command, "GATT 断开", params)
            command == "gatt.discover" -> stubResponse(command, "GATT 服务发现", params)
            command == "gatt.read" -> stubResponse(command, "GATT 读取特征值", params)
            command == "gatt.write" -> stubResponse(command, "GATT 写入特征值", params)
            command == "gatt.notify" -> stubResponse(command, "GATT 通知设置", params)
            command == "gatt.mtu" -> stubResponse(command, "GATT MTU 协商", params)

            // L2CAP 操作
            command == "l2cap.connect" -> stubResponse(command, "L2CAP 连接", params)
            command == "l2cap.listen" -> stubResponse(command, "L2CAP 监听", params)
            command == "l2cap.disconnect" -> stubResponse(command, "L2CAP 断开", params)
            command == "l2cap.send" -> stubResponse(command, "L2CAP 发送数据", params)
            command == "l2cap.speed_test.start" -> stubResponse(command, "L2CAP 测速启动", params)
            command == "l2cap.speed_test.stop" -> stubResponse(command, "L2CAP 测速停止", params)

            // 扫描/广播操作
            command == "scan.ble.start" -> stubResponse(command, "BLE 扫描启动", params)
            command == "scan.ble.stop" -> stubResponse(command, "BLE 扫描停止", params)
            command == "scan.classic.start" -> stubResponse(command, "经典蓝牙扫描启动", params)
            command == "scan.classic.stop" -> stubResponse(command, "经典蓝牙扫描停止", params)
            command == "scan.results" -> stubResponse(command, "扫描结果查询", params)
            command == "advertise.start" -> stubResponse(command, "BLE 广播启动", params)
            command == "advertise.stop" -> stubResponse(command, "BLE 广播停止", params)

            // 通用操作
            command == "status" -> stubResponse(command, "状态查询", params)
            command == "devices" -> stubResponse(command, "设备列表查询", params)
            command == "chat.clear" -> stubResponse(command, "清除聊天记录", params)

            else -> AdbResponse(
                success = false,
                error = "unknown_command",
                message = "未知命令: $command"
            )
        }
    }

    /**
     * Stub 响应：返回命令已接收但尚未实现实际逻辑的占位响应。
     */
    private fun stubResponse(
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

    private fun sendResult(response: AdbResponse) {
        val jsonResult = json.encodeToString(response)
        resultCode = if (response.success) Activity.RESULT_OK else Activity.RESULT_CANCELED
        resultData = jsonResult
        Log.i(LOG_TAG, jsonResult)
    }
}
