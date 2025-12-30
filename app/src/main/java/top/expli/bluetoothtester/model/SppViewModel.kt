package top.expli.bluetoothtester.model

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.concurrent.atomic.AtomicLong
import java.util.UUID
import top.expli.bluetoothtester.bluetooth.SppClientManager
import top.expli.bluetoothtester.bluetooth.SppServerManager
import top.expli.bluetoothtester.bluetooth.SocketLikeBluetoothClientManager
import top.expli.bluetoothtester.bluetooth.SocketLikeBluetoothServerManager
import top.expli.bluetoothtester.data.SppDeviceStore
import top.expli.bluetoothtester.bluetooth.SendRecvBluetoothProfileManager
import top.expli.bluetoothtester.model.SppRole

class SppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val adapter: BluetoothAdapter? =
        ctx.getSystemService(BluetoothManager::class.java)?.adapter

    private val _uiState = MutableStateFlow(SppUiState())
    val uiState: StateFlow<SppUiState> = _uiState.asStateFlow()

    private var currentClient: SppClientManager? = null
    private var currentServer: SppServerManager? = null
    private var receiveJob: Job? = null
    private var connectionJob: Job? = null
    private var speedTestJob: Job? = null
    private val chatId = AtomicLong(0L)
    private val speedSampleId = AtomicLong(0L)
    private var lastConnState: SppConnectionState? = null

    init {
        viewModelScope.launch {
            SppDeviceStore.observe(ctx).collect { list ->
                _uiState.update { it.copy(registered = list) }
            }
        }
    }

    fun updateSendingText(text: String) = _uiState.update { it.copy(sendingText = text) }
    fun updatePayloadSize(size: Int) =
        _uiState.update { it.copy(payloadSize = size.coerceAtLeast(1)) }

    fun clearChat() = _uiState.update { it.copy(chat = emptyList(), lastError = null) }

    fun select(device: SppDevice) {
        _uiState.update { it.copy(selected = device, lastError = null) }
    }

    fun addOrUpdate(device: SppDevice) {
        viewModelScope.launch {
            val newList = _uiState.value.registered.toMutableList().apply {
                val idx = indexOfFirst { it.key() == device.key() }
                if (idx >= 0) this[idx] = device else add(device)
            }
            SppDeviceStore.save(ctx, newList)
        }
    }

    fun remove(address: String) {
        viewModelScope.launch {
            val newList =
                _uiState.value.registered.filterNot { it.address == address || it.uuid == address }
            SppDeviceStore.save(ctx, newList)
            val selected = _uiState.value.selected
            if (selected != null && (selected.address == address || selected.uuid == address)) {
                _uiState.update { it.copy(selected = null) }
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect() {
        try {
            val sel = _uiState.value.selected ?: return
            val uuid = runCatching { UUID.fromString(sel.uuid) }.getOrElse {
                _uiState.update { it.copy(lastError = "UUID 格式错误") }; return
            }

            // 清理旧连接
            disconnect()
            _uiState.update { it.copy(lastError = null) }

            if (sel.role == SppRole.Client) {
                if (sel.address.isBlank()) {
                    _uiState.update { it.copy(lastError = "地址不能为空") }; return
                }
                val dev = adapter?.getRemoteDevice(sel.address) ?: run {
                    _uiState.update { it.copy(lastError = "无效的地址") }; return
                }
                val mgr = SppClientManager(ctx, dev, uuid)
                currentClient = mgr
                _uiState.update { it.copy(connectionState = SppConnectionState.Connecting) }
                observeConnectionState(mgr)
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        mgr.connect()
                    } catch (t: Throwable) {
                        _uiState.update {
                            it.copy(
                                connectionState = SppConnectionState.Error,
                                lastError = t.message
                            )
                        }
                    }
                }
            } else {
                val mgr = SppServerManager(ctx, uuid)
                currentServer = mgr
                _uiState.update { it.copy(connectionState = SppConnectionState.Listening) }
                observeConnectionState(mgr)
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val ok = mgr.register()
                        if (!ok) _uiState.update {
                            it.copy(
                                connectionState = SppConnectionState.Error,
                                lastError = "注册监听失败"
                            )
                        }
                    } catch (t: Throwable) {
                        _uiState.update {
                            it.copy(
                                connectionState = SppConnectionState.Error,
                                lastError = t.message
                            )
                        }
                    }
                }
            }
        } catch (_: SecurityException) {
            _uiState.update {
                it.copy(
                    connectionState = SppConnectionState.Error,
                    lastError = "缺少蓝牙权限（请授权“蓝牙连接/扫描”）"
                )
            }
        }
    }

    fun disconnect() {
        stopSpeedTest()
        currentClient?.disconnect()
        currentServer?.disconnect()
        stopReceiveLoop()
        connectionJob?.cancel(); connectionJob = null
        currentClient = null; currentServer = null
        _uiState.update { it.copy(connectionState = SppConnectionState.Closed) }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun toggleSpeedTest() {
        if (_uiState.value.speedTestRunning) stopSpeedTest() else startSpeedTest()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun startSpeedTest(testDurationMs: Long = Long.MAX_VALUE) {
        if (_uiState.value.speedTestRunning) return
        val mgr = currentClient ?: currentServer
        if (mgr == null || _uiState.value.connectionState != SppConnectionState.Connected) {
            _uiState.update { it.copy(lastError = "未连接，无法测速") }
            return
        }

        stopReceiveLoop()
        _uiState.update {
            it.copy(
                lastError = null,
                speedTestRunning = true,
                speedTestElapsedMs = 0,
                speedTestTxTotalBytes = 0,
                speedTestRxTotalBytes = 0,
                speedTestTxInstantBps = null,
                speedTestRxInstantBps = null,
                speedTestTxAvgBps = null,
                speedTestRxAvgBps = null,
                speedTestSamples = emptyList()
            )
        }
        appendChat(SppChatDirection.System, "测速开始")

        speedTestJob?.cancel()
        speedTestJob = viewModelScope.launch {
            try {
                val payloadSize = _uiState.value.payloadSize.coerceIn(1, 32 * 1024)
                val result = mgr.speedTestWithInstantSpeed(
                    testDurationMs = testDurationMs,
                    payloadSize = payloadSize,
                    progress = { txInstant, rxInstant, txAvg, rxAvg, txTotalBytes, rxTotalBytes, elapsedMs ->
                        _uiState.update { s ->
                            val sample = SppSpeedSample(
                                id = speedSampleId.incrementAndGet(),
                                elapsedMs = elapsedMs,
                                txInstantBps = txInstant,
                                rxInstantBps = rxInstant,
                                txAvgBps = txAvg,
                                rxAvgBps = rxAvg,
                                txTotalBytes = txTotalBytes,
                                rxTotalBytes = rxTotalBytes
                            )
                            val cappedSamples = (listOf(sample) + s.speedTestSamples).take(400)
                            s.copy(
                                speedTestElapsedMs = elapsedMs,
                                speedTestTxTotalBytes = txTotalBytes,
                                speedTestRxTotalBytes = rxTotalBytes,
                                speedTestTxInstantBps = txInstant,
                                speedTestRxInstantBps = rxInstant,
                                speedTestTxAvgBps = txAvg,
                                speedTestRxAvgBps = rxAvg,
                                speedTestSamples = cappedSamples
                            )
                        }
                    }
                )

                if (result != null) {
                    appendChat(
                        SppChatDirection.System,
                        "测速完成：TX ${formatBps(result.txAvgBps)} / RX ${formatBps(result.rxAvgBps)}"
                    )
                } else {
                    _uiState.update { it.copy(lastError = "测速失败") }
                    appendChat(SppChatDirection.System, "测速失败")
                }
            } catch (_: CancellationException) {
                appendChat(SppChatDirection.System, "测速已停止")
            } catch (t: Throwable) {
                _uiState.update { it.copy(lastError = t.message ?: "测速异常") }
                appendChat(
                    SppChatDirection.System,
                    "测速异常：${t.message ?: t::class.java.simpleName}"
                )
            } finally {
                _uiState.update { it.copy(speedTestRunning = false) }
                speedTestJob = null
                if (_uiState.value.connectionState == SppConnectionState.Connected && (currentClient != null || currentServer != null)) {
                    startReceiveLoop()
                }
            }
        }
    }

    fun stopSpeedTest() {
        speedTestJob?.cancel()
        speedTestJob = null
        _uiState.update { it.copy(speedTestRunning = false) }
    }

    fun sendOnce() {
        val mgr = currentClient ?: currentServer ?: return
        val text = _uiState.value.sendingText
        val bytes = text.encodeToByteArray()
        val ok = mgr.send(bytes)
        if (ok && bytes.isNotEmpty()) {
            appendChat(
                SppChatDirection.Out,
                text.ifBlank { bytes.joinToString(" ") { b -> "%02X".format(b) } })
            _uiState.update { it.copy(sendingText = "") }
        }
        if (!ok) {
            _uiState.update { it.copy(lastError = "发送失败") }
        }
    }

    private fun observeConnectionState(manager: SendRecvBluetoothProfileManager?) {
        manager ?: return
        connectionJob?.cancel()
        connectionJob = viewModelScope.launch {
            when (manager) {
                is SppClientManager -> manager.connectionState.collect { s ->
                    val mapped = when (s) {
                        SocketLikeBluetoothClientManager.ConnectionState.Idle -> SppConnectionState.Idle
                        SocketLikeBluetoothClientManager.ConnectionState.Connecting -> SppConnectionState.Connecting
                        SocketLikeBluetoothClientManager.ConnectionState.Connected -> SppConnectionState.Connected
                        SocketLikeBluetoothClientManager.ConnectionState.Closed -> SppConnectionState.Closed
                        SocketLikeBluetoothClientManager.ConnectionState.Error -> SppConnectionState.Error
                    }
                    handleConnectionState(mapped)
                }

                is SppServerManager -> manager.connectionState.collect { s ->
                    val mapped = when (s) {
                        SocketLikeBluetoothServerManager.ConnectionState.Idle -> SppConnectionState.Idle
                        SocketLikeBluetoothServerManager.ConnectionState.Listening -> SppConnectionState.Listening
                        SocketLikeBluetoothServerManager.ConnectionState.Connected -> SppConnectionState.Connected
                        SocketLikeBluetoothServerManager.ConnectionState.Closed -> SppConnectionState.Closed
                        SocketLikeBluetoothServerManager.ConnectionState.Error -> SppConnectionState.Error
                    }
                    handleConnectionState(mapped)
                }
            }
        }
    }

    private fun handleConnectionState(mapped: SppConnectionState) {
        _uiState.update { it.copy(connectionState = mapped) }
        if (lastConnState != mapped) {
            lastConnState = mapped
            val msg = when (mapped) {
                SppConnectionState.Idle -> "状态: 未连接"
                SppConnectionState.Connecting -> "状态: 连接中…"
                SppConnectionState.Listening -> "状态: 监听中…"
                SppConnectionState.Connected -> "状态: 已连接"
                SppConnectionState.Closed -> "状态: 已关闭"
                SppConnectionState.Error -> "状态: 异常"
            }
            appendChat(SppChatDirection.System, msg)
        }
        when (mapped) {
            SppConnectionState.Connected -> if (!_uiState.value.speedTestRunning) startReceiveLoop()
            SppConnectionState.Closed, SppConnectionState.Error -> {
                stopSpeedTest()
                stopReceiveLoop()
            }
            else -> {}
        }
    }

    private fun appendChat(direction: SppChatDirection, text: String) {
        val line = text.trim()
        if (line.isBlank()) return
        val item = SppChatItem(id = chatId.incrementAndGet(), direction = direction, text = line)
        _uiState.update { state ->
            state.copy(chat = (listOf(item) + state.chat).take(500))
        }
    }

    private fun startReceiveLoop() {
        stopReceiveLoop()
        val mgr = currentClient ?: currentServer ?: return
        receiveJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val buf = mgr.receive(_uiState.value.payloadSize)
                if (buf == null) break
                if (buf.isNotEmpty()) {
                    val hex = buf.joinToString(" ") { b -> "%02X".format(b) }
                    appendChat(SppChatDirection.In, hex)
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

    private fun formatBps(bps: Double): String {
        val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
        var value = bps
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024
            unitIndex++
        }
        return String.format(Locale.US, "%.2f %s", value, units[unitIndex])
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}

private fun SppDevice.key(): String = if (address.isNotBlank()) address else uuid
