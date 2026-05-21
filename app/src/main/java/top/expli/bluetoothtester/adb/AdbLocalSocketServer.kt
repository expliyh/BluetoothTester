package top.expli.bluetoothtester.adb

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicInteger

/**
 * ADB over LocalSocket 服务端。
 *
 * 在抽象命名空间 "bt_tester_adb_control" 上监听，
 * 通过 `adb forward tcp:PORT localabstract:bt_tester_adb_control` 将 ADB TCP 端口转发到此 socket。
 *
 * ## 使用方法
 * ```
 * # 1. 设置端口转发（在电脑上执行）
 * adb forward tcp:9876 localabstract:bt_tester_adb_control
 *
 * # 2. 发送命令（JSON 行协议，每行一个命令）
 * echo '{"command":"spp.connect","params":{"address":"AA:BB:CC:DD:EE:FF"}}' | nc localhost 9876
 *
 * # 3. 查看响应（每行一个 JSON 对象）
 * # {"success":true,"data":{...},"message":"..."}
 *
 * # 4. 交互模式
 * nc localhost 9876
 * # 然后逐行输入 JSON 命令
 * ```
 *
 * ## 协议
 * - 请求: `{"command":"<命令>","params":{"<参数名>":"<参数值>"}}` + 换行
 * - 响应: `{"success":true|false,"data":{...},"error":"<错误码>","message":"<描述>"}` + 换行
 * - 连接保持活跃，可发送多条命令
 */
object AdbLocalSocketServer {

    private const val SOCKET_NAME = "bt_tester_adb_control"
    private const val LOG_TAG = "BtTesterADB_LS"

    enum class State { Stopped, Starting, Running, Error }

    private val _state = MutableStateFlow(State.Stopped)
    val state: StateFlow<State> = _state

    private var serverSocket: LocalServerSocket? = null
    private var serverScope: CoroutineScope? = null
    private var acceptJob: Job? = null
    private val connectionCount = AtomicInteger(0)
    private val activeConnections = AtomicInteger(0)

    /** 累计接收的连接数 */
    val totalConnections: Int get() = connectionCount.get()

    /** 当前活跃连接数 */
    val currentConnections: Int get() = activeConnections.get()

    /**
     * 启动 LocalSocket 服务端。
     * 在 IO 调度器上创建 LocalServerSocket 并循环 accept。
     * 每个连接在独立协程中处理。
     */
    fun start(): Boolean {
        if (_state.value == State.Running) return true

        _state.value = State.Starting
        val scope = CoroutineScope(Dispatchers.IO)
        serverScope = scope

        return try {
            val srv = try {
                LocalServerSocket(SOCKET_NAME)
            } catch (e: IOException) {
                Log.e(LOG_TAG, "创建 LocalServerSocket 失败: ${e.message}", e)
                _state.value = State.Error
                return false
            }

            serverSocket = srv
            _state.value = State.Running
            Log.i(LOG_TAG, "ADB LocalSocket 服务端已启动: $SOCKET_NAME")

            acceptJob = scope.launch {
                while (isActive) {
                    try {
                        val client = srv.accept()
                        connectionCount.incrementAndGet()
                        activeConnections.incrementAndGet()
                        Log.d(LOG_TAG, "新连接 #${connectionCount.get()} (活跃: ${activeConnections.get()})")
                        launch { handleConnection(client) }
                    } catch (e: IOException) {
                        if (isActive) {
                            Log.e(LOG_TAG, "accept 异常: ${e.message}")
                            _state.value = State.Error
                        }
                        break
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(LOG_TAG, "启动失败: ${e.message}", e)
            _state.value = State.Error
            false
        }
    }

    /**
     * 停止 LocalSocket 服务端。
     * 关闭 serverSocket 和所有活跃连接。
     */
    fun stop() {
        Log.i(LOG_TAG, "正在停止 ADB LocalSocket 服务端...")
        _state.value = State.Stopped

        acceptJob?.cancel()
        acceptJob = null

        try {
            serverSocket?.close()
        } catch (_: IOException) {
        }
        serverSocket = null

        serverScope?.cancel()
        serverScope = null
    }

    /**
     * 处理单个客户端连接。
     * 逐行读取 JSON 请求，路由到 AdbCommandRouter，写回 JSON 响应。
     */
    private suspend fun handleConnection(client: LocalSocket) {
        try {
            val reader = BufferedReader(InputStreamReader(client.inputStream))
            val writer = BufferedWriter(OutputStreamWriter(client.outputStream))

            while (coroutineContext.isActive) {
                val line = try {
                    reader.readLine()
                } catch (e: IOException) {
                    Log.d(LOG_TAG, "连接读取异常: ${e.message}")
                    break
                }

                if (line == null) {
                    // EOF — 对端断开
                    Log.d(LOG_TAG, "客户端断开连接")
                    break
                }

                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                val request = AdbCommandRouter.parseRequest(trimmed)
                if (request == null) {
                    val errorJson = AdbCommandRouter.toJson(
                        top.expli.bluetoothtester.model.AdbResponse(
                            success = false,
                            error = "invalid_json",
                            message = "无法解析 JSON 请求"
                        )
                    )
                    try {
                        writer.write(errorJson)
                        writer.newLine()
                        writer.flush()
                    } catch (_: IOException) {
                        break
                    }
                    continue
                }

                // 检查 App 前台状态
                if (!AppStateChecker.isAppReady(null)) {
                    val errorJson = AdbCommandRouter.toJson(
                        top.expli.bluetoothtester.model.AdbResponse(
                            success = false,
                            error = "app_not_ready",
                            message = "请先启动应用并保持在前台"
                        )
                    )
                    try {
                        writer.write(errorJson)
                        writer.newLine()
                        writer.flush()
                    } catch (_: IOException) {
                        break
                    }
                    continue
                }

                // 校验参数
                val validationError = AdbParamValidator.validate(
                    request.command, request.params
                )
                if (validationError != null) {
                    try {
                        writer.write(AdbCommandRouter.toJson(validationError))
                        writer.newLine()
                        writer.flush()
                    } catch (_: IOException) {
                        break
                    }
                    continue
                }

                // 路由命令
                val response = try {
                    AdbCommandQueue.execute {
                        AdbCommandRouter.route(request.command, request.params)
                    }
                } catch (e: Exception) {
                    top.expli.bluetoothtester.model.AdbResponse(
                        success = false,
                        error = "internal_error",
                        message = "内部错误: ${e.message}"
                    )
                }

                // 写回响应
                try {
                    writer.write(AdbCommandRouter.toJson(response))
                    writer.newLine()
                    writer.flush()
                } catch (_: IOException) {
                    break
                }
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "连接处理异常: ${e.message}", e)
        } finally {
            activeConnections.decrementAndGet()
            try {
                client.close()
            } catch (_: IOException) {
            }
        }
    }
}
