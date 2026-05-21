package top.expli.bluetoothtester.adb

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * 统一 ADB 命令 BroadcastReceiver。
 * 所有 ADB 命令通过 Intent Extra 中的 "command" 字段区分。
 * 支持有序广播返回结果。
 * 命令路由逻辑委托给 AdbCommandRouter。
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
            val error = top.expli.bluetoothtester.model.AdbResponse(
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
            val error = top.expli.bluetoothtester.model.AdbResponse(
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
                    AdbCommandRouter.route(command, params)
                }
                val jsonResult = json.encodeToString(
                    top.expli.bluetoothtester.model.AdbResponse.serializer(), response
                )
                pendingResult.setResultCode(
                    if (response.success) Activity.RESULT_OK else Activity.RESULT_CANCELED
                )
                pendingResult.setResultData(jsonResult)
                Log.i(LOG_TAG, jsonResult)
            } catch (e: Exception) {
                val error = top.expli.bluetoothtester.model.AdbResponse(
                    success = false,
                    error = "internal_error",
                    message = "内部错误: ${e.message}"
                )
                val jsonResult = json.encodeToString(
                    top.expli.bluetoothtester.model.AdbResponse.serializer(), error
                )
                pendingResult.setResultCode(Activity.RESULT_CANCELED)
                pendingResult.setResultData(jsonResult)
                Log.i(LOG_TAG, jsonResult)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun sendResult(response: top.expli.bluetoothtester.model.AdbResponse) {
        val jsonResult = json.encodeToString(
            top.expli.bluetoothtester.model.AdbResponse.serializer(), response
        )
        resultCode = if (response.success) Activity.RESULT_OK else Activity.RESULT_CANCELED
        resultData = jsonResult
        Log.i(LOG_TAG, jsonResult)
    }
}
