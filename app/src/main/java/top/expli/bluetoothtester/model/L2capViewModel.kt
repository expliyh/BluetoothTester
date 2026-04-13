package top.expli.bluetoothtester.model

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.expli.bluetoothtester.bluetooth.L2capClientManager
import top.expli.bluetoothtester.bluetooth.L2capServerManager
import top.expli.bluetoothtester.bluetooth.SendRecvBluetoothProfileManager
import top.expli.bluetoothtester.bluetooth.SocketLikeBluetoothClientManager
import top.expli.bluetoothtester.bluetooth.SocketLikeBluetoothServerManager
import top.expli.bluetoothtester.util.formatBps
import java.util.concurrent.atomic.AtomicLong

/**
 * L2CAP CoC ViewModel：支持客户端/服务端模式切换，单会话。
 * 复用 SppChatItem / SppChatDirection 用于聊天消息。
 */
class L2capViewModel(app: Application) : AndroidViewModel(app) {

    private val adapter: BluetoothAdapter? =
        app.getSystemService(BluetoothManager::class.java)?.adapter

    private val _uiState = MutableStateFlow(L2capUiState())
    val uiState: StateFlow<L2capUiState> = _uiState.asStateFlow()

    private var clientManager: L2capClientManager? = null
    private var serverManager: L2capServerManager? = null
    private var connectionJob: Job? = null
    private var receiveJob: Job? = null
    private var speedTestJob: Job? = null

    private val chatId = AtomicLong(0L)

    private val activeManager: SendRecvBluetoothProfileManager?
        get() = clientManager ?: serverManager

    // ─── Route 参数初始化 ───

    fun initFromRoute(address: String, name: String) {
        if (address.isNotBlank()) {
            _uiState.update {
                it.copy(
                    targetAddress = address,
                    role = L2capRole.Client
                )
            }
        }
    }

    // ─── 角色切换 ───

    fun switchRole(role: L2capRole) {
        val current = _uiState.value
        if (current.connectionState != L2capConnectionState.Idle &&
            current.connectionState != L2capConnectionState.Closed
        ) return
        _uiState.update { it.copy(role = role) }
    }

    // ─── 输入更新 ───

    fun updateTargetAddress(address: String) {
        _uiState.update { it.copy(targetAddress = address) }
    }

    fun updatePsm(psm: Int?) {
        _uiState.update { it.copy(psm = psm) }
    }

    fun updateSendingText(text: String) {
        _uiState.update { it.copy(sendingText = text) }
    }

    // ─── 连接操作 ───

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(address: String, psm: Int) {
        disconnect()
        _uiState.update { it.copy(lastError = null) }

        if (address.isBlank()) {
            _uiState.update { it.copy(lastError = "地址不能为空") }
            return
        }

        try {
            val dev = adapter?.getRemoteDevice(address) ?: run {
                _uiState.update { it.copy(lastError = "无效的地址") }
                return
            }
            val mgr = L2capClientManager(
                getApplication<Application>().applicationContext, dev, psm
            )
            clientManager = mgr
            serverManager = null
            _uiState.update { it.copy(connectionState = L2capConnectionState.Connecting) }
            observeClientState(mgr)
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    // TODO: ForegroundService integration — call addReason(Reason.L2capConnection) on successful connect
                    mgr.connect()
                } catch (t: Throwable) {
                    _uiState.update {
                        it.copy(
                            connectionState = L2capConnectionState.Error(
                                t.message ?: "连接失败"
                            ),
                            lastError = t.message
                        )
                    }
                }
            }
        } catch (_: SecurityException) {
            _uiState.update {
                it.copy(
                    connectionState = L2capConnectionState.Error("缺少蓝牙权限"),
                    lastError = "缺少蓝牙权限（请授权蓝牙连接/扫描）"
                )
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun listen() {
        disconnect()
        _uiState.update { it.copy(lastError = null) }

        try {
            val mgr = L2capServerManager(getApplication<Application>().applicationContext)
            serverManager = mgr
            clientManager = null
            _uiState.update { it.copy(connectionState = L2capConnectionState.Listening) }
            observeServerState(mgr)
            // Observe assigned PSM
            viewModelScope.launch {
                mgr.assignedPsm.collect { psm ->
                    _uiState.update { it.copy(assignedPsm = psm) }
                }
            }
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val ok = mgr.register()
                    if (!ok) {
                        _uiState.update {
                            it.copy(
                                connectionState = L2capConnectionState.Error("注册监听失败"),
                                lastError = "注册监听失败"
                            )
                        }
                    }
                } catch (t: Throwable) {
                    _uiState.update {
                        it.copy(
                            connectionState = L2capConnectionState.Error(
                                t.message ?: "监听失败"
                            ),
                            lastError = t.message
                        )
                    }
                }
            }
        } catch (_: SecurityException) {
            _uiState.update {
                it.copy(
                    connectionState = L2capConnectionState.Error("缺少蓝牙权限"),
                    lastError = "缺少蓝牙权限（请授权蓝牙连接/扫描）"
                )
            }
        }
    }

    fun disconnect() {
        speedTestJob?.cancel(); speedTestJob = null
        receiveJob?.cancel(); receiveJob = null
        connectionJob?.cancel(); connectionJob = null
        clientManager?.disconnect(); clientManager = null
        serverManager?.disconnect(); serverManager = null
        // TODO: ForegroundService integration — call removeReason(Reason.L2capConnection)
        _uiState.update {
            it.copy(
                connectionState = L2capConnectionState.Closed,
                assignedPsm = null
            )
        }
    }

    // ─── 发送 ───

    fun sendOnce(text: String) {
        val mgr = activeManager ?: return
        val bytes = text.encodeToByteArray()
        if (bytes.isEmpty()) return
        val ok = mgr.send(bytes)
        if (ok) {
            appendChat(SppChatDirection.Out, text)
            _uiState.update { it.copy(sendingText = "") }
        } else {
            _uiState.update { it.copy(lastError = "发送失败") }
        }
    }

    // ─── 测速 ───

    fun toggleSpeedTest() {
        if (_uiState.value.connectionState != L2capConnectionState.Connected) {
            _uiState.update { it.copy(lastError = "未连接，无法测速") }
            return
        }
        if (speedTestJob?.isActive == true) {
            speedTestJob?.cancel()
            speedTestJob = null
        } else {
            startSpeedTest()
        }
    }

    private fun startSpeedTest() {
        val mgr = activeManager ?: return
        stopReceiveLoop()
        // TODO: ForegroundService integration — call addReason(Reason.SpeedTest)
        appendChat(SppChatDirection.System, "测速开始")

        speedTestJob = viewModelScope.launch {
            try {
                val result = mgr.speedTestWithInstantSpeed(
                    testDurationMs = Long.MAX_VALUE,
                    payloadSize = 4096,
                    txEnabled = true,
                    rxEnabled = false,
                    progress = { txInstant, _, txAvg, _, txTotal, _, elapsed ->
                        appendChat(
                            SppChatDirection.System,
                            "TX: ${formatBps(txInstant)} (avg ${formatBps(txAvg)}) ${txTotal}B ${elapsed}ms"
                        )
                    }
                )
                if (result != null) {
                    appendChat(
                        SppChatDirection.System,
                        "测速完成：TX ${formatBps(result.txAvgBps)} / RX ${formatBps(result.rxAvgBps)}"
                    )
                }
            } catch (_: CancellationException) {
                appendChat(SppChatDirection.System, "测速已停止")
            } catch (t: Throwable) {
                _uiState.update { it.copy(lastError = t.message ?: "测速异常") }
            } finally {
                speedTestJob = null
                // TODO: ForegroundService integration — call removeReason(Reason.SpeedTest)
                if (_uiState.value.connectionState == L2capConnectionState.Connected && activeManager != null) {
                    startReceiveLoop()
                }
            }
        }
    }

    // ─── 聊天 ───

    fun clearChat() {
        _uiState.update { it.copy(chat = emptyList(), lastError = null) }
    }

    private fun appendChat(direction: SppChatDirection, text: String) {
        val line = text.trim()
        if (line.isBlank()) return
        val item = SppChatItem(id = chatId.incrementAndGet(), direction = direction, text = line)
        _uiState.update { state ->
            state.copy(chat = (listOf(item) + state.chat).take(500))
        }
    }

    // ─── 连接状态观察 ───

    private fun observeClientState(mgr: L2capClientManager) {
        connectionJob?.cancel()
        connectionJob = viewModelScope.launch {
            mgr.connectionState.collect { s ->
                val mapped = when (s) {
                    SocketLikeBluetoothClientManager.ConnectionState.Idle -> L2capConnectionState.Idle
                    SocketLikeBluetoothClientManager.ConnectionState.Connecting -> L2capConnectionState.Connecting
                    SocketLikeBluetoothClientManager.ConnectionState.Connected -> L2capConnectionState.Connected
                    SocketLikeBluetoothClientManager.ConnectionState.Closed -> L2capConnectionState.Closed
                    SocketLikeBluetoothClientManager.ConnectionState.Error -> L2capConnectionState.Error("连接失败")
                }
                handleConnectionState(mapped)
            }
        }
    }

    private fun observeServerState(mgr: L2capServerManager) {
        connectionJob?.cancel()
        connectionJob = viewModelScope.launch {
            mgr.connectionState.collect { s ->
                val mapped = when (s) {
                    SocketLikeBluetoothServerManager.ConnectionState.Idle -> L2capConnectionState.Idle
                    SocketLikeBluetoothServerManager.ConnectionState.Listening -> L2capConnectionState.Listening
                    SocketLikeBluetoothServerManager.ConnectionState.Connected -> L2capConnectionState.Connected
                    SocketLikeBluetoothServerManager.ConnectionState.Closed -> L2capConnectionState.Closed
                    SocketLikeBluetoothServerManager.ConnectionState.Error -> L2capConnectionState.Error("连接异常")
                }
                handleConnectionState(mapped)
            }
        }
    }

    private var lastConnState: L2capConnectionState? = null

    private fun handleConnectionState(state: L2capConnectionState) {
        _uiState.update { it.copy(connectionState = state) }

        if (lastConnState != state) {
            lastConnState = state
            val msg = when (state) {
                L2capConnectionState.Idle -> "状态: 未连接"
                L2capConnectionState.Connecting -> "状态: 连接中…"
                L2capConnectionState.Listening -> "状态: 监听中…"
                L2capConnectionState.Connected -> "状态: 已连接"
                L2capConnectionState.Closed -> "状态: 已关闭"
                is L2capConnectionState.Error -> "状态: 异常 - ${state.message}"
            }
            appendChat(SppChatDirection.System, msg)
        }

        when (state) {
            L2capConnectionState.Connected -> {
                if (speedTestJob?.isActive != true) startReceiveLoop()
            }
            L2capConnectionState.Closed, is L2capConnectionState.Error -> {
                speedTestJob?.cancel(); speedTestJob = null
                stopReceiveLoop()
            }
            else -> {}
        }
    }

    // ─── 接收循环 ───

    private fun startReceiveLoop() {
        stopReceiveLoop()
        val mgr = activeManager ?: return
        receiveJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val buf = mgr.receive(4096) ?: break
                if (buf.isNotEmpty()) {
                    val text = formatIncoming(buf)
                    appendChat(SppChatDirection.In, text)
                } else {
                    delay(10)
                }
            }
        }
    }

    private fun stopReceiveLoop() {
        receiveJob?.cancel()
        receiveJob = null
    }

    // ─── 工具方法 ───

    private fun formatIncoming(bytes: ByteArray): String {
        val text = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull()
            ?: return bytes.toHexString()
        if (text.isBlank()) return bytes.toHexString()
        val bad = text.count { ch ->
            ch == '\uFFFD' || (ch.code < 0x20 && ch != '\n' && ch != '\r' && ch != '\t') || ch.code == 0x7F
        }
        val ratio = bad.toDouble() / text.length.coerceAtLeast(1)
        return if (ratio <= 0.15) text else bytes.toHexString()
    }

    private fun ByteArray.toHexString(): String =
        joinToString(" ") { b -> "%02X".format(b.toInt() and 0xFF) }

    override fun onCleared() {
        super.onCleared()
        speedTestJob?.cancel()
        receiveJob?.cancel()
        connectionJob?.cancel()
        clientManager?.disconnect()
        serverManager?.disconnect()
    }
}
