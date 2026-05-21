package top.expli.bluetoothtester.adb

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import top.expli.bluetoothtester.bluetooth.SppClientManager
import top.expli.bluetoothtester.bluetooth.SocketLikeBluetoothClientManager.ConnectionState
import top.expli.bluetoothtester.model.AdbResponse
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object AdbSessionManager : AdbCommandHandler {

    private const val TAG = "AdbSessionManager"
    private const val DEFAULT_SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
    private const val MAX_LOG_ENTRIES = 200

    // ─── Public state ───

    data class DeviceState(
        val address: String,
        val name: String,
        val uuid: String,
        val connectionState: String
    )

    data class LogEntry(
        val id: Long,
        val timestamp: Long,
        val command: String,
        val success: Boolean,
        val message: String
    )

    data class SpeedTestStatus(
        val address: String,
        val running: Boolean,
        val txBps: Double = 0.0,
        val rxBps: Double = 0.0,
        val txTotalBytes: Long = 0,
        val rxTotalBytes: Long = 0,
        val elapsedMs: Long = 0
    )

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _devices = MutableStateFlow<List<DeviceState>>(emptyList())
    val devices: StateFlow<List<DeviceState>> = _devices.asStateFlow()

    private val _commandLog = MutableStateFlow<List<LogEntry>>(emptyList())
    val commandLog: StateFlow<List<LogEntry>> = _commandLog.asStateFlow()

    private val _speedTestStatus = MutableStateFlow<SpeedTestStatus?>(null)
    val speedTestStatus: StateFlow<SpeedTestStatus?> = _speedTestStatus.asStateFlow()

    // ─── Internal state ───

    private var appContext: Context? = null
    private var adapter: BluetoothAdapter? = null
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connections = ConcurrentHashMap<String, SppClientManager>()
    private val speedTestJobs = ConcurrentHashMap<String, Job>()
    private val connectionJobs = ConcurrentHashMap<String, MutableList<Job>>()
    private var logId = 0L

    fun init(context: Context) {
        appContext = context.applicationContext
        adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        AdbCommandRouter.handler = this
    }

    fun exitAdbMode() {
        // Cancel all tracked connection coroutines
        connectionJobs.values.forEach { jobs -> jobs.forEach { it.cancel() } }
        connectionJobs.clear()
        connections.keys.toList().forEach { addr ->
            connections.remove(addr)?.disconnect()
        }
        speedTestJobs.values.forEach { it.cancel() }
        speedTestJobs.clear()
        _devices.value = emptyList()
        _commandLog.value = emptyList()
        _speedTestStatus.value = null
        _isActive.value = false
        // Cancel all coroutines and recreate scope for next ADB mode session
        scope.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        Log.i(TAG, "ADB mode exited, all connections cleaned up")
    }

    // ─── AdbCommandHandler implementation ───

    override suspend fun handleSppRegister(params: Map<String, String>): AdbResponse {
        activate()
        val name = params["name"] ?: return logged("spp.register", AdbResponse(
            success = false, error = "missing_param", message = "缺少 name 参数"
        ))
        val address = params["address"] ?: return logged("spp.register", AdbResponse(
            success = false, error = "missing_param", message = "缺少 address 参数"
        ))
        val uuid = params["uuid"] ?: DEFAULT_SPP_UUID

        updateDeviceState(address, name, uuid, "Idle")
        return logged("spp.register", AdbResponse(
            success = true,
            data = buildJsonObject {
                put("name", JsonPrimitive(name))
                put("address", JsonPrimitive(address))
                put("uuid", JsonPrimitive(uuid))
            },
            message = "设备已注册"
        ))
    }

    override suspend fun handleSppConnect(params: Map<String, String>): AdbResponse {
        activate()
        val address = params["address"] ?: return logged("spp.connect", AdbResponse(
            success = false, error = "missing_param", message = "缺少 address 参数"
        ))
        val name = params["name"] ?: address
        val uuid = params["uuid"] ?: DEFAULT_SPP_UUID
        val ctx = appContext ?: return logged("spp.connect", AdbResponse(
            success = false, error = "not_initialized", message = "AdbSessionManager 未初始化"
        ))

        val existing = connections[address]
        if (existing != null) {
            val state = existing.connectionState.value
            if (state == ConnectionState.Connected || state == ConnectionState.Connecting) {
                return logged("spp.connect", AdbResponse(
                    success = true,
                    data = buildJsonObject {
                        put("address", JsonPrimitive(address))
                        put("state", JsonPrimitive(state.name))
                    },
                    message = "设备已处于 ${state.name} 状态"
                ))
            }
            existing.disconnect()
        }

        val btAdapter = adapter ?: return logged("spp.connect", AdbResponse(
            success = false, error = "no_adapter", message = "蓝牙适配器不可用"
        ))
        val device = try {
            btAdapter.getRemoteDevice(address)
        } catch (e: Exception) {
            return logged("spp.connect", AdbResponse(
                success = false, error = "invalid_address", message = "无效的蓝牙地址: $address"
            ))
        }

        val sppUuid = try {
            UUID.fromString(uuid)
        } catch (_: Exception) {
            return logged("spp.connect", AdbResponse(
                success = false, error = "invalid_uuid", message = "UUID 格式错误: $uuid"
            ))
        }

        val mgr = SppClientManager(ctx, device, sppUuid, secure = true)
        connections[address] = mgr
        updateDeviceState(address, name, uuid, "Connecting")

        // Track coroutine jobs for this connection so they can be cancelled on disconnect
        val jobs = mutableListOf<Job>()
        connectionJobs[address] = jobs

        jobs += scope.launch {
            try {
                mgr.connect()
            } catch (_: Exception) {}

            // Observe final state
            val finalState = mgr.connectionState.value
            updateDeviceState(address, name, uuid, finalState.name)
        }

        // Observe state changes
        jobs += scope.launch {
            mgr.connectionState.collect { state ->
                updateDeviceState(address, name, uuid, state.name)
            }
        }

        return logged("spp.connect", AdbResponse(
            success = true,
            data = buildJsonObject {
                put("address", JsonPrimitive(address))
                put("state", JsonPrimitive("Connecting"))
            },
            message = "连接命令已发送"
        ))
    }

    override suspend fun handleSppDisconnect(params: Map<String, String>): AdbResponse {
        val address = params["address"] ?: return logged("spp.disconnect", AdbResponse(
            success = false, error = "missing_param", message = "缺少 address 参数"
        ))
        val mgr = connections.remove(address)
            ?: return logged("spp.disconnect", AdbResponse(
                success = false, error = "not_found", message = "未找到地址 $address 的连接"
            ))

        speedTestJobs.remove(address)?.cancel()
        connectionJobs.remove(address)?.forEach { it.cancel() }
        mgr.disconnect()
        updateDeviceState(address, "", "", "Disconnected")

        // Remove from device list
        _devices.update { list -> list.filter { it.address != address } }

        return logged("spp.disconnect", AdbResponse(
            success = true,
            data = buildJsonObject { put("address", JsonPrimitive(address)) },
            message = "已断开连接"
        ))
    }

    override suspend fun handleSppSend(params: Map<String, String>): AdbResponse {
        val address = params["address"] ?: return logged("spp.send", AdbResponse(
            success = false, error = "missing_param", message = "缺少 address 参数"
        ))
        val data = params["data"] ?: return logged("spp.send", AdbResponse(
            success = false, error = "missing_param", message = "缺少 data 参数"
        ))
        val hex = params["hex"]?.let { it == "true" || it == "1" } ?: false

        val mgr = connections[address]
            ?: return logged("spp.send", AdbResponse(
                success = false, error = "not_connected", message = "设备未连接"
            ))

        if (mgr.connectionState.value != ConnectionState.Connected) {
            return logged("spp.send", AdbResponse(
                success = false, error = "not_connected", message = "设备未处于已连接状态"
            ))
        }

        val bytes = if (hex) {
            try {
                data.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            } catch (_: Exception) {
                return logged("spp.send", AdbResponse(
                    success = false, error = "invalid_hex", message = "HEX 数据格式错误"
                ))
            }
        } else {
            data.encodeToByteArray()
        }

        val ok = mgr.send(bytes)
        return logged("spp.send", if (ok) {
            AdbResponse(
                success = true,
                data = buildJsonObject { put("bytes_sent", JsonPrimitive(bytes.size)) },
                message = "数据已发送 (${bytes.size} bytes)"
            )
        } else {
            AdbResponse(success = false, error = "send_failed", message = "发送失败")
        })
    }

    override suspend fun handleSppSpeedTestStart(params: Map<String, String>): AdbResponse {
        val address = params["address"] ?: return logged("spp.speed_test.start", AdbResponse(
            success = false, error = "missing_param", message = "缺少 address 参数"
        ))
        val mgr = connections[address]
            ?: return logged("spp.speed_test.start", AdbResponse(
                success = false, error = "not_connected", message = "设备未连接"
            ))

        if (mgr.connectionState.value != ConnectionState.Connected) {
            return logged("spp.speed_test.start", AdbResponse(
                success = false, error = "not_connected", message = "设备未处于已连接状态"
            ))
        }

        if (speedTestJobs[address]?.isActive == true) {
            return logged("spp.speed_test.start", AdbResponse(
                success = true, message = "测速已在运行中"
            ))
        }

        val duration = (params["duration"]?.toLongOrNull() ?: 5) * 1000L
        val payloadSize = params["payload_size"]?.toIntOrNull() ?: 512

        _speedTestStatus.value = SpeedTestStatus(address = address, running = true)

        val job = scope.launch {
            mgr.speedTestWithInstantSpeed(
                testDurationMs = duration,
                payloadSize = payloadSize,
                txEnabled = true,
                rxEnabled = true,
                progress = { txInst, rxInst, txAvg, rxAvg, txTotal, rxTotal, elapsed ->
                    _speedTestStatus.value = SpeedTestStatus(
                        address = address,
                        running = true,
                        txBps = txAvg,
                        rxBps = rxAvg,
                        txTotalBytes = txTotal,
                        rxTotalBytes = rxTotal,
                        elapsedMs = elapsed
                    )
                }
            )
            _speedTestStatus.update { status ->
                status?.copy(running = false)
            }
        }
        speedTestJobs[address] = job

        return logged("spp.speed_test.start", AdbResponse(
            success = true,
            data = buildJsonObject {
                put("address", JsonPrimitive(address))
                put("duration_ms", JsonPrimitive(duration))
                put("payload_size", JsonPrimitive(payloadSize))
            },
            message = "测速已启动"
        ))
    }

    override suspend fun handleSppSpeedTestStop(params: Map<String, String>): AdbResponse {
        val address = params["address"] ?: return logged("spp.speed_test.stop", AdbResponse(
            success = false, error = "missing_param", message = "缺少 address 参数"
        ))
        val job = speedTestJobs.remove(address)
            ?: return logged("spp.speed_test.stop", AdbResponse(
                success = true, message = "测速未在运行"
            ))

        job.cancel()
        val status = _speedTestStatus.value
        _speedTestStatus.value = status?.copy(running = false)

        return logged("spp.speed_test.stop", AdbResponse(
            success = true,
            data = buildJsonObject {
                put("address", JsonPrimitive(address))
                put("tx_bps", JsonPrimitive(status?.txBps ?: 0.0))
                put("rx_bps", JsonPrimitive(status?.rxBps ?: 0.0))
            },
            message = "测速已停止"
        ))
    }

    override suspend fun handleStatus(params: Map<String, String>): AdbResponse {
        val module = params["module"] ?: return logged("status", AdbResponse(
            success = false, error = "missing_param", message = "缺少 module 参数"
        ))

        return when (module) {
            "spp" -> {
                val devList = _devices.value.map { dev ->
                    buildJsonObject {
                        put("address", JsonPrimitive(dev.address))
                        put("name", JsonPrimitive(dev.name))
                        put("uuid", JsonPrimitive(dev.uuid))
                        put("state", JsonPrimitive(dev.connectionState))
                    }
                }
                logged("status", AdbResponse(
                    success = true,
                    data = buildJsonObject {
                        put("module", JsonPrimitive("spp"))
                        put("device_count", JsonPrimitive(devList.size))
                        put("devices", JsonArray(devList))
                    },
                    message = "SPP 模块状态"
                ))
            }
            else -> logged("status", AdbResponse(
                success = false, error = "unsupported_module", message = "不支持的模块: $module"
            ))
        }
    }

    override suspend fun handleDevices(): AdbResponse {
        val devList = _devices.value.map { dev ->
            buildJsonObject {
                put("address", JsonPrimitive(dev.address))
                put("name", JsonPrimitive(dev.name))
                put("uuid", JsonPrimitive(dev.uuid))
                put("state", JsonPrimitive(dev.connectionState))
            }
        }
        return logged("devices", AdbResponse(
            success = true,
            data = buildJsonObject {
                put("count", JsonPrimitive(devList.size))
                put("devices", JsonArray(devList))
            },
            message = "共 ${devList.size} 个设备"
        ))
    }

    override suspend fun handleChatClear(params: Map<String, String>): AdbResponse {
        return logged("chat.clear", AdbResponse(
            success = true, message = "ADB 模式无聊天记录"
        ))
    }

    // ─── Internal helpers ───

    private fun activate() {
        if (!_isActive.value) {
            _isActive.value = true
            Log.i(TAG, "ADB mode activated")
        }
    }

    private fun updateDeviceState(address: String, name: String, uuid: String, state: String) {
        _devices.update { list ->
            val existing = list.indexOfFirst { it.address == address }
            val entry = DeviceState(address, name, uuid, state)
            if (existing >= 0) {
                list.toMutableList().apply { this[existing] = entry }
            } else {
                list + entry
            }
        }
    }

    private fun logged(command: String, response: AdbResponse): AdbResponse {
        val entry = LogEntry(
            id = ++logId,
            timestamp = System.currentTimeMillis(),
            command = command,
            success = response.success,
            message = response.message ?: ""
        )
        _commandLog.update { log ->
            (log + entry).takeLast(MAX_LOG_ENTRIES)
        }
        return response
    }
}
