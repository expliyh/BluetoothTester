package top.expli.bluetoothtester.model

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import java.util.UUID
import top.expli.bluetoothtester.bluetooth.SendRecvBluetoothProfileManager
import top.expli.bluetoothtester.bluetooth.SocketLikeBluetoothClientManager
import top.expli.bluetoothtester.bluetooth.SocketLikeBluetoothServerManager
import top.expli.bluetoothtester.bluetooth.SppClientManager
import top.expli.bluetoothtester.bluetooth.SppServerManager
import top.expli.bluetoothtester.data.SppDeviceStore

class SppViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx = app.applicationContext
    private val adapter: BluetoothAdapter? =
        ctx.getSystemService(BluetoothManager::class.java)?.adapter

    private val _uiState = MutableStateFlow(SppUiState())
    val uiState: StateFlow<SppUiState> = _uiState.asStateFlow()

    private data class SessionRuntime(
        var client: SppClientManager? = null,
        var server: SppServerManager? = null,
        var receiveJob: Job? = null,
        var connectionJob: Job? = null,
        var speedTestJob: Job? = null,
        var lastConnState: SppConnectionState? = null
    ) {
        val manager: SendRecvBluetoothProfileManager?
            get() = client ?: server
    }

    private val runtimes = mutableMapOf<String, SessionRuntime>()
    private val chatId = AtomicLong(0L)
    private val speedSampleId = AtomicLong(0L)

    private fun defaultSpeedTestMode(role: SppRole): SppSpeedTestMode =
        when (role) {
            SppRole.Client -> SppSpeedTestMode.TxOnly
            SppRole.Server -> SppSpeedTestMode.RxOnly
        }

    init {
        viewModelScope.launch {
            SppDeviceStore.observe(ctx).collect { list ->
                _uiState.update { state ->
                    val sessions = state.sessions.mapValues { (key, session) ->
                        val updatedDevice = list.firstOrNull { it.key() == key } ?: session.device
                        if (updatedDevice == session.device) {
                            session
                        } else {
                            val updatedMode =
                                if (session.speedTestMode == defaultSpeedTestMode(session.device.role)) {
                                    defaultSpeedTestMode(updatedDevice.role)
                                } else {
                                    session.speedTestMode
                                }
                            session.copy(device = updatedDevice, speedTestMode = updatedMode)
                        }
                    }
                    state.copy(registered = list, sessions = sessions)
                }
            }
        }
    }

    fun updateSendingText(text: String) {
        val key = _uiState.value.selectedKey ?: return
        updateSession(key) { it.copy(sendingText = text) }
    }

    fun updatePayloadSize(size: Int) {
        val key = _uiState.value.selectedKey ?: return
        updateSession(key) { it.copy(payloadSize = size.coerceAtLeast(1)) }
    }

    fun updateParseIncomingAsText(enabled: Boolean) {
        val key = _uiState.value.selectedKey ?: return
        updateSession(key) { it.copy(parseIncomingAsText = enabled) }
    }

    fun clearChat() {
        val key = _uiState.value.selectedKey ?: return
        updateSession(key) { it.copy(chat = emptyList(), lastError = null) }
    }

    fun select(device: SppDevice) {
        val key = device.key()
        _uiState.update { state ->
            val existing = state.sessions[key]
            val session =
                existing?.copy(device = device, lastError = null) ?: SppSession(
                    device = device,
                    speedTestMode = defaultSpeedTestMode(device.role)
                )
            state.copy(
                selectedKey = key,
                sessions = state.sessions + (key to session)
            )
        }
    }

    fun addOrUpdate(device: SppDevice) {
        viewModelScope.launch {
            val newList = _uiState.value.registered.toMutableList().apply {
                val idx = indexOfFirst { it.key() == device.key() }
                if (idx >= 0) this[idx] = device else add(device)
            }
            SppDeviceStore.save(ctx, newList)
        }

        val key = device.key()
        _uiState.update { state ->
            val existing = state.sessions[key] ?: return@update state
            val updatedMode =
                if (existing.speedTestMode == defaultSpeedTestMode(existing.device.role)) {
                    defaultSpeedTestMode(device.role)
                } else {
                    existing.speedTestMode
                }
            state.copy(
                sessions =
                    state.sessions + (key to existing.copy(
                        device = device,
                        speedTestMode = updatedMode
                    ))
            )
        }
    }

    fun setSpeedTestWindowOpen(open: Boolean) {
        val key = _uiState.value.selectedKey ?: return
        updateSession(key) { it.copy(speedTestWindowOpen = open) }
    }

    fun remove(address: String) {
        disconnect(address)
        viewModelScope.launch {
            val newList =
                _uiState.value.registered.filterNot { it.address == address || it.uuid == address }
            SppDeviceStore.save(ctx, newList)
            _uiState.update { state ->
                val selectedKey = if (state.selectedKey == address) null else state.selectedKey
                state.copy(
                    selectedKey = selectedKey,
                    sessions = state.sessions - address
                )
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect() {
        val key = _uiState.value.selectedKey ?: return
        val session = _uiState.value.sessions[key] ?: return
        try {
            val uuid = runCatching { UUID.fromString(session.device.uuid) }.getOrElse {
                updateSession(key) { it.copy(lastError = "UUID 格式错误") }
                return
            }

            // 清理旧连接
            disconnect(key)
            updateSession(key) { it.copy(lastError = null) }

            val runtime = runtimes.getOrPut(key) { SessionRuntime() }

            if (session.device.role == SppRole.Client) {
                if (session.device.address.isBlank()) {
                    updateSession(key) { it.copy(lastError = "地址不能为空") }
                    return
                }
                val dev = adapter?.getRemoteDevice(session.device.address) ?: run {
                    updateSession(key) { it.copy(lastError = "无效的地址") }
                    return
                }
                val mgr = SppClientManager(ctx, dev, uuid)
                runtime.client = mgr
                runtime.server = null
                updateSession(key) { it.copy(connectionState = SppConnectionState.Connecting) }
                observeConnectionState(key, mgr)
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        mgr.connect()
                    } catch (t: Throwable) {
                        updateSession(key) {
                            it.copy(
                                connectionState = SppConnectionState.Error,
                                lastError = t.message
                            )
                        }
                    }
                }
            } else {
                val mgr = SppServerManager(ctx, uuid)
                runtime.server = mgr
                runtime.client = null
                updateSession(key) { it.copy(connectionState = SppConnectionState.Listening) }
                observeConnectionState(key, mgr)
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val ok = mgr.register()
                        if (!ok) {
                            updateSession(key) {
                                it.copy(
                                    connectionState = SppConnectionState.Error,
                                    lastError = "注册监听失败"
                                )
                            }
                        }
                    } catch (t: Throwable) {
                        updateSession(key) {
                            it.copy(
                                connectionState = SppConnectionState.Error,
                                lastError = t.message
                            )
                        }
                    }
                }
            }
        } catch (_: SecurityException) {
            updateSession(key) {
                it.copy(
                    connectionState = SppConnectionState.Error,
                    lastError = "缺少蓝牙权限（请授权“蓝牙连接/扫描”）"
                )
            }
        }
    }

    fun disconnect() {
        val key = _uiState.value.selectedKey ?: return
        disconnect(key)
    }

    private fun disconnect(key: String) {
        val runtime = runtimes[key] ?: return
        stopSpeedTest(key)
        runtime.client?.disconnect()
        runtime.server?.disconnect()
        stopReceiveLoop(key)
        runtime.connectionJob?.cancel(); runtime.connectionJob = null
        runtime.client = null; runtime.server = null
        runtime.lastConnState = null
        updateSession(key) { it.copy(connectionState = SppConnectionState.Closed) }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun toggleSpeedTest() {
        val key = _uiState.value.selectedKey ?: return
        val session = _uiState.value.sessions[key] ?: return
        if (session.speedTestRunning) stopSpeedTest(key) else startSpeedTest(key, Long.MAX_VALUE)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun startSpeedTest(testDurationMs: Long = Long.MAX_VALUE) {
        val key = _uiState.value.selectedKey ?: return
        startSpeedTest(key, testDurationMs)
    }

    private fun startSpeedTest(key: String, testDurationMs: Long) {
        val session = _uiState.value.sessions[key] ?: return
        if (session.speedTestRunning) return
        val runtime = runtimes[key]
        val mgr = runtime?.manager
        if (mgr == null || session.connectionState != SppConnectionState.Connected) {
            updateSession(key) { it.copy(lastError = "未连接，无法测速") }
            return
        }

        stopReceiveLoop(key)
        updateSession(key) {
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
        appendChat(key, SppChatDirection.System, "测速开始")

        runtime.speedTestJob?.cancel()
        runtime.speedTestJob = viewModelScope.launch {
            try {
                val payloadSize = (_uiState.value.sessions[key]?.payloadSize ?: 256)
                    .coerceIn(1, 32 * 1024)
                val mode =
                    _uiState.value.sessions[key]?.speedTestMode
                        ?: defaultSpeedTestMode(session.device.role)
                val txEnabled = mode == SppSpeedTestMode.TxOnly || mode == SppSpeedTestMode.Duplex
                val rxEnabled = mode == SppSpeedTestMode.RxOnly || mode == SppSpeedTestMode.Duplex
                val result = mgr.speedTestWithInstantSpeed(
                    testDurationMs = testDurationMs,
                    payloadSize = payloadSize,
                    txEnabled = txEnabled,
                    rxEnabled = rxEnabled,
                    progress = { txInstant, rxInstant, txAvg, rxAvg, txTotalBytes, rxTotalBytes, elapsedMs ->
                        updateSession(key) { s ->
                            val sample =
                                SppSpeedSample(
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
                        key,
                        SppChatDirection.System,
                        "测速完成：TX ${formatBps(result.txAvgBps)} / RX ${formatBps(result.rxAvgBps)}"
                    )
                } else {
                    updateSession(key) { it.copy(lastError = "测速失败") }
                    appendChat(key, SppChatDirection.System, "测速失败")
                }
            } catch (_: CancellationException) {
                appendChat(key, SppChatDirection.System, "测速已停止")
            } catch (t: Throwable) {
                updateSession(key) { it.copy(lastError = t.message ?: "测速异常") }
                appendChat(
                    key,
                    SppChatDirection.System,
                    "测速异常：${t.message ?: t::class.java.simpleName}"
                )
            } finally {
                updateSession(key) { it.copy(speedTestRunning = false) }
                runtime.speedTestJob = null
                val latest = _uiState.value.sessions[key]
                if (latest?.connectionState == SppConnectionState.Connected && runtime.manager != null) {
                    startReceiveLoop(key)
                }
            }
        }
    }

    fun stopSpeedTest() {
        val key = _uiState.value.selectedKey ?: return
        stopSpeedTest(key)
    }

    private fun stopSpeedTest(key: String) {
        val runtime = runtimes[key] ?: return
        runtime.speedTestJob?.cancel()
        runtime.speedTestJob = null
        updateSession(key) { it.copy(speedTestRunning = false) }
    }

    fun sendOnce() {
        val key = _uiState.value.selectedKey ?: return
        val runtime = runtimes[key] ?: return
        val mgr = runtime.manager ?: return
        val text = _uiState.value.sessions[key]?.sendingText ?: return
        val bytes = text.encodeToByteArray()
        val ok = mgr.send(bytes)
        if (ok && bytes.isNotEmpty()) {
            appendChat(
                key,
                SppChatDirection.Out,
                text.ifBlank { bytes.joinToString(" ") { b -> "%02X".format(b) } })
            updateSession(key) { it.copy(sendingText = "") }
        }
        if (!ok) {
            updateSession(key) { it.copy(lastError = "发送失败") }
        }
    }

    private fun observeConnectionState(key: String, manager: SppClientManager) {
        val runtime = runtimes.getOrPut(key) { SessionRuntime() }
        runtime.connectionJob?.cancel()
        runtime.connectionJob = viewModelScope.launch {
            manager.connectionState.collect { s ->
                val mapped = when (s) {
                    SocketLikeBluetoothClientManager.ConnectionState.Idle -> SppConnectionState.Idle
                    SocketLikeBluetoothClientManager.ConnectionState.Connecting -> SppConnectionState.Connecting
                    SocketLikeBluetoothClientManager.ConnectionState.Connected -> SppConnectionState.Connected
                    SocketLikeBluetoothClientManager.ConnectionState.Closed -> SppConnectionState.Closed
                    SocketLikeBluetoothClientManager.ConnectionState.Error -> SppConnectionState.Error
                }
                handleConnectionState(key, mapped)
            }
        }
    }

    private fun observeConnectionState(key: String, manager: SppServerManager) {
        val runtime = runtimes.getOrPut(key) { SessionRuntime() }
        runtime.connectionJob?.cancel()
        runtime.connectionJob = viewModelScope.launch {
            manager.connectionState.collect { s ->
                val mapped = when (s) {
                    SocketLikeBluetoothServerManager.ConnectionState.Idle -> SppConnectionState.Idle
                    SocketLikeBluetoothServerManager.ConnectionState.Listening -> SppConnectionState.Listening
                    SocketLikeBluetoothServerManager.ConnectionState.Connected -> SppConnectionState.Connected
                    SocketLikeBluetoothServerManager.ConnectionState.Closed -> SppConnectionState.Closed
                    SocketLikeBluetoothServerManager.ConnectionState.Error -> SppConnectionState.Error
                }
                handleConnectionState(key, mapped)
            }
        }
    }

    private fun handleConnectionState(key: String, mapped: SppConnectionState) {
        updateSession(key) { it.copy(connectionState = mapped) }

        val runtime = runtimes.getOrPut(key) { SessionRuntime() }
        if (runtime.lastConnState != mapped) {
            runtime.lastConnState = mapped
            val msg = when (mapped) {
                SppConnectionState.Idle -> "状态: 未连接"
                SppConnectionState.Connecting -> "状态: 连接中…"
                SppConnectionState.Listening -> "状态: 监听中…"
                SppConnectionState.Connected -> "状态: 已连接"
                SppConnectionState.Closed -> "状态: 已关闭"
                SppConnectionState.Error -> "状态: 异常"
            }
            appendChat(key, SppChatDirection.System, msg)
        }

        when (mapped) {
            SppConnectionState.Connected -> {
                val session = _uiState.value.sessions[key]
                if (session != null && !session.speedTestRunning) startReceiveLoop(key)
            }

            SppConnectionState.Closed, SppConnectionState.Error -> {
                stopSpeedTest(key)
                stopReceiveLoop(key)
            }

            else -> {}
        }
    }

    private fun appendChat(key: String, direction: SppChatDirection, text: String) {
        val line = text.trim()
        if (line.isBlank()) return
        val session = _uiState.value.sessions[key] ?: return
        val muteConsole = session.speedTestWindowOpen || session.speedTestRunning
        if (muteConsole) return
        val item = SppChatItem(id = chatId.incrementAndGet(), direction = direction, text = line)
        updateSession(key) { state ->
            state.copy(chat = (listOf(item) + state.chat).take(500))
        }
    }

    private fun startReceiveLoop(key: String) {
        stopReceiveLoop(key)
        val runtime = runtimes[key] ?: return
        val mgr = runtime.manager ?: return
        runtime.receiveJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val session = _uiState.value.sessions[key] ?: break
                val buf = mgr.receive(session.payloadSize)
                if (buf == null) break
                if (buf.isNotEmpty()) {
                    val muteConsole = session.speedTestWindowOpen || session.speedTestRunning
                    if (!muteConsole) {
                        val text = formatIncoming(buf, session.parseIncomingAsText)
                        appendChat(key, SppChatDirection.In, text)
                    }
                } else {
                    delay(10)
                }
            }
        }
    }

    private fun stopReceiveLoop(key: String) {
        val runtime = runtimes[key] ?: return
        runtime.receiveJob?.cancel()
        runtime.receiveJob = null
    }

    private fun updateSession(key: String, block: (SppSession) -> SppSession) {
        _uiState.update { state ->
            val session = state.sessions[key] ?: return@update state
            state.copy(sessions = state.sessions + (key to block(session)))
        }
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

    private fun formatIncoming(bytes: ByteArray, parseAsText: Boolean): String {
        if (!parseAsText) return bytes.toHexString()

        val text =
            runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull() ?: return bytes.toHexString()
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
        val keys = runtimes.keys.toList()
        keys.forEach { disconnect(it) }
        runtimes.clear()
    }
}

private fun SppDevice.key(): String = if (address.isNotBlank()) address else uuid
