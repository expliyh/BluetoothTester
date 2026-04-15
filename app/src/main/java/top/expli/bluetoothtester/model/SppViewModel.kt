package top.expli.bluetoothtester.model

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import top.expli.bluetoothtester.bluetooth.FrameSyncReader
import top.expli.bluetoothtester.bluetooth.LocalSocketClientManager
import top.expli.bluetoothtester.bluetooth.LocalSocketServerManager
import top.expli.bluetoothtester.bluetooth.PacketSpeedTestConfig
import top.expli.bluetoothtester.bluetooth.PingEchoHandler
import top.expli.bluetoothtester.bluetooth.SendRecvBluetoothProfileManager
import top.expli.bluetoothtester.bluetooth.SocketLikeBluetoothClientManager
import top.expli.bluetoothtester.bluetooth.SocketLikeBluetoothServerManager
import top.expli.bluetoothtester.bluetooth.SpeedTestDiagnostics
import top.expli.bluetoothtester.bluetooth.SppClientManager
import top.expli.bluetoothtester.bluetooth.SppServerManager
import top.expli.bluetoothtester.bluetooth.StopCondition
import top.expli.bluetoothtester.bluetooth.TestPacketBuilder
import top.expli.bluetoothtester.data.ClientSessionStore
import top.expli.bluetoothtester.data.ServerSessionStore
import top.expli.bluetoothtester.data.ServerTabStore
import top.expli.bluetoothtester.data.SettingsStore
import top.expli.bluetoothtester.data.SppDeviceStore
import top.expli.bluetoothtester.util.formatBps
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class SppViewModel(app: Application) : AndroidViewModel(app) {
    private val adapter: BluetoothAdapter? =
        getApplication<Application>().getSystemService(BluetoothManager::class.java)?.adapter

    private val _uiState = MutableStateFlow(SppUiState())
    val uiState: StateFlow<SppUiState> = _uiState.asStateFlow()

    private data class SessionRuntime(
        var client: SendRecvBluetoothProfileManager? = null,
        var server: SendRecvBluetoothProfileManager? = null,
        var receiveJob: Job? = null,
        var connectionJob: Job? = null,
        var speedTestJob: Job? = null,
        var connectionCycleJob: Job? = null,
        var pingTestJob: Job? = null,
        var periodicTestJob: Job? = null,
        var reconnectJob: Job? = null,
        var lastConnState: SppConnectionState? = null,
        var lastListenerState: ServerListenerState? = null,
        val controlBuffer: StringBuilder = StringBuilder()
    ) {
        val manager: SendRecvBluetoothProfileManager?
            get() = client ?: server
    }

    private val runtimes = ConcurrentHashMap<String, SessionRuntime>()
    private val chatId = AtomicLong(0L)
    private val speedSampleId = AtomicLong(0L)
    private var localSocketMode = false

    // ═══ Client Control Card 状态 ═══
    val clientControlAddress = MutableStateFlow("")
    val clientControlUuid = MutableStateFlow("1101")
    val clientControlSecurityMode = MutableStateFlow(SecurityMode.Secure)

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            val state = intent.getIntExtra(
                BluetoothAdapter.EXTRA_STATE,
                BluetoothAdapter.ERROR
            )
            if (state == BluetoothAdapter.STATE_TURNING_OFF ||
                state == BluetoothAdapter.STATE_OFF
            ) {
                onBluetoothOff()
            }
        }
    }

    private companion object {
        private const val REMOTE_START_PREFIX = "START:"
        private const val REMOTE_START_ACK = "START_ACK"
        private const val REMOTE_EOF = "EOF"
        private const val REMOTE_PERIODIC_PREFIX = "PERIODIC:"
        private const val REMOTE_PERIODIC_ACK = "PERIODIC_ACK"
        private const val REMOTE_PERIODIC_DONE = "PERIODIC_DONE"
        private const val REMOTE_PING_PREFIX = "PING:"
        private const val REMOTE_PING_ACK = "PING_ACK"
        private const val REMOTE_PING_DONE = "PING_DONE"
        private const val REMOTE_RX_MAX_DURATION_MS = 60_000L
        private const val IDLE_TIMEOUT_MS = 3_000L  // 3 秒无新包则认为发送端已停止
        private const val IDLE_POLL_INTERVAL_MS = 50L // available() 轮询间隔
        val ALL_SIGNAL_PREFIXES = listOf(
            REMOTE_START_PREFIX, REMOTE_PERIODIC_PREFIX, REMOTE_PING_PREFIX
        )
        /** 完整匹配的信令关键字，用于接收循环过滤 */
        val SIGNAL_KEYWORDS = setOf(
            REMOTE_START_ACK, REMOTE_EOF,
            REMOTE_PERIODIC_ACK, REMOTE_PERIODIC_DONE,
            REMOTE_PING_ACK, REMOTE_PING_DONE
        )
    }

    sealed interface SignalCommand {
        data class Start(val targetBytes: Long) : SignalCommand
        data class Periodic(val payloadSize: Int, val stopCondition: String) : SignalCommand
        data class Ping(val payloadSize: Int, val count: Int) : SignalCommand
    }

    private fun defaultSpeedTestMode(role: SppRole): SppSpeedTestMode =
        when (role) {
            SppRole.Client -> SppSpeedTestMode.TxOnly
            SppRole.Server -> SppSpeedTestMode.RxOnly
        }

    init {
        // Register Bluetooth adapter state change receiver
        getApplication<Application>().registerReceiver(
            bluetoothStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        )

        viewModelScope.launch {
            SettingsStore.observeLocalSocketDebug(getApplication()).collect { enabled ->
                localSocketMode = enabled
            }
        }

        viewModelScope.launch {
            SppDeviceStore.observe(getApplication<Application>()).collect { list ->
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

        // ═══ Server Tab 恢复流程（一次性读取，避免协程泄漏） ═══
        viewModelScope.launch {
            val context = getApplication<Application>()
            val tabs = ServerTabStore.observe(context).first()
            _uiState.update { state ->
                val newSessions = state.sessions.toMutableMap()
                for (tab in tabs) {
                    val key = "server:${tab.tabId}"
                    if (!newSessions.containsKey(key)) {
                        newSessions[key] = SppSession(
                            device = SppDevice(
                                name = tab.name,
                                uuid = tab.uuid,
                                role = SppRole.Server
                            ),
                            speedTestMode = defaultSpeedTestMode(SppRole.Server)
                        )
                    }
                }
                state.copy(serverTabs = tabs, sessions = newSessions)
            }
            // 一次性恢复每个 Tab 的 sessionHistory
            for (tab in tabs) {
                val key = "server:${tab.tabId}"
                val history = ServerSessionStore.observe(context, tab.tabId).first()
                updateSession(key) { it.copy(sessionHistory = history) }
            }
        }

        // ═══ Client Control Card 默认 SecurityMode 恢复 ═══
        viewModelScope.launch {
            SettingsStore.observeClientDefaultSecurityMode(getApplication()).first().let { mode ->
                clientControlSecurityMode.value = mode
            }
        }

        // ═══ Client 历史会话恢复 ═══
        viewModelScope.launch {
            val history = ClientSessionStore.observe(getApplication()).first()
            _uiState.update { it.copy(clientSessionHistory = history) }
        }
    }

    fun updateSendingText(text: String) {
        val key = _uiState.value.selectedKey ?: return
        updateSession(key) { it.copy(sendingText = text) }
    }

    fun updatePayloadSize(size: Int) {
        val key = _uiState.value.selectedKey ?: return
        updateSession(key) { it.copy(receiveBufferSize = size.coerceAtLeast(1)) }
    }

    fun updateParseIncomingAsText(enabled: Boolean) {
        val key = _uiState.value.selectedKey ?: return
        updateSession(key) { it.copy(parseIncomingAsText = enabled) }
    }

    fun setMuteConsoleDuringTest(enabled: Boolean) {
        val key = _uiState.value.selectedKey ?: return
        updateSession(key) { it.copy(muteConsoleDuringTest = enabled) }
    }

    fun clearChat() {
        val key = _uiState.value.selectedKey ?: return
        updateSession(key) { it.copy(chat = emptyList(), lastError = null) }
    }

    // ═══ Client 会话管理 ═══

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun createAndConnectClientSession(
        address: String,
        uuid: String,
        name: String,
        securityMode: SecurityMode
    ): String {
        val sessionId = java.util.UUID.randomUUID().toString()
        val key = "client:$sessionId"

        val parseResult = top.expli.bluetoothtester.bluetooth.UuidHelper.parse(uuid)
        val fullUuid = when (parseResult) {
            is top.expli.bluetoothtester.bluetooth.UuidHelper.UuidParseResult.Valid -> parseResult.uuid.toString()
            is top.expli.bluetoothtester.bluetooth.UuidHelper.UuidParseResult.Invalid -> {
                // Still create session but with error
                _uiState.update { state ->
                    val session = SppSession(
                        device = SppDevice(name = name, address = address, uuid = uuid, role = SppRole.Client),
                        connectionState = SppConnectionState.Error,
                        lastError = "UUID 格式错误: ${parseResult.reason}",
                        speedTestMode = defaultSpeedTestMode(SppRole.Client)
                    )
                    state.copy(sessions = state.sessions + (key to session), selectedKey = key)
                }
                return sessionId
            }
        }

        val finalName = name.ifBlank { address.ifBlank { fullUuid } }
        val device = SppDevice(name = finalName, address = address, uuid = fullUuid, role = SppRole.Client)

        // Create session
        _uiState.update { state ->
            val session = SppSession(
                device = device,
                connectionState = SppConnectionState.Connecting,
                speedTestMode = defaultSpeedTestMode(SppRole.Client),
                securityMode = securityMode
            )
            state.copy(sessions = state.sessions + (key to session), selectedKey = key)
        }

        val runtime = runtimes.computeIfAbsent(key) { SessionRuntime() }

        // cancelDiscovery() 兜底
        try { adapter?.cancelDiscovery() } catch (_: SecurityException) {}

        if (localSocketMode) {
            val mgr = LocalSocketClientManager(getApplication<Application>().applicationContext, fullUuid)
            runtime.client = mgr
            runtime.server = null
            observeLocalSocketClientState(key, mgr)
            viewModelScope.launch(Dispatchers.IO) {
                try { mgr.connect() } catch (t: Throwable) {
                    updateSession(key) { it.copy(connectionState = SppConnectionState.Error, lastError = t.message) }
                }
            }
        } else {
            if (address.isBlank()) {
                updateSession(key) { it.copy(connectionState = SppConnectionState.Error, lastError = "地址不能为空") }
                return sessionId
            }
            val dev = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
            if (dev == null) {
                updateSession(key) { it.copy(connectionState = SppConnectionState.Error, lastError = "无效的蓝牙地址格式") }
                return sessionId
            }
            val sppUuid = UUID.fromString(fullUuid)
            val secure = securityMode == SecurityMode.Secure
            val mgr = SppClientManager(getApplication<Application>().applicationContext, dev, sppUuid, secure)
            runtime.client = mgr
            runtime.server = null
            observeConnectionState(key, mgr)
            viewModelScope.launch(Dispatchers.IO) {
                try { mgr.connect() } catch (t: Throwable) {
                    updateSession(key) { it.copy(connectionState = SppConnectionState.Error, lastError = t.message) }
                }
            }
        }

        // Persist SecurityMode default
        viewModelScope.launch {
            SettingsStore.updateClientDefaultSecurityMode(getApplication(), securityMode)
        }

        return sessionId
    }

    fun disconnectClientSession(sessionId: String) {
        val key = "client:$sessionId"
        disconnect(key)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun retryClientSession(sessionId: String) {
        val key = "client:$sessionId"
        val session = _uiState.value.sessions[key] ?: return
        // Use original parameters to reconnect
        disconnect(key)
        updateSession(key) { it.copy(connectionState = SppConnectionState.Connecting, lastError = null) }

        val runtime = runtimes.computeIfAbsent(key) { SessionRuntime() }
        try { adapter?.cancelDiscovery() } catch (_: SecurityException) {}

        val uuid = runCatching { UUID.fromString(session.device.uuid) }.getOrElse {
            updateSession(key) { it.copy(connectionState = SppConnectionState.Error, lastError = "UUID 格式错误") }
            return
        }

        if (localSocketMode) {
            val mgr = LocalSocketClientManager(getApplication<Application>().applicationContext, session.device.uuid)
            runtime.client = mgr; runtime.server = null
            observeLocalSocketClientState(key, mgr)
            viewModelScope.launch(Dispatchers.IO) {
                try { mgr.connect() } catch (t: Throwable) {
                    updateSession(key) { it.copy(connectionState = SppConnectionState.Error, lastError = t.message) }
                }
            }
        } else {
            if (session.device.address.isBlank()) {
                updateSession(key) { it.copy(connectionState = SppConnectionState.Error, lastError = "地址不能为空") }
                return
            }
            val dev = runCatching { adapter?.getRemoteDevice(session.device.address) }.getOrNull() ?: run {
                updateSession(key) { it.copy(connectionState = SppConnectionState.Error, lastError = "无效的蓝牙地址") }
                return
            }
            val secure = session.securityMode == SecurityMode.Secure
            val mgr = SppClientManager(getApplication<Application>().applicationContext, dev, uuid, secure)
            runtime.client = mgr; runtime.server = null
            observeConnectionState(key, mgr)
            viewModelScope.launch(Dispatchers.IO) {
                try { mgr.connect() } catch (t: Throwable) {
                    updateSession(key) { it.copy(connectionState = SppConnectionState.Error, lastError = t.message) }
                }
            }
        }
    }

    fun deleteClientHistorySession(sessionId: String) {
        val key = "client:$sessionId"
        _uiState.update { state ->
            state.copy(
                sessions = state.sessions - key,
                clientSessionHistory = state.clientSessionHistory.filter { it.sessionId != sessionId },
                selectedKey = if (state.selectedKey == key) null else state.selectedKey
            )
        }
        runtimes.remove(key)
        viewModelScope.launch {
            ClientSessionStore.removeSnapshot(getApplication(), sessionId)
        }
    }

    fun clearClientHistory() {
        _uiState.update { state ->
            val clientKeys = state.sessions.keys.filter { it.startsWith("client:") }
            val activeKeys = clientKeys.filter { key ->
                val s = state.sessions[key]
                s?.connectionState == SppConnectionState.Connected || s?.connectionState == SppConnectionState.Connecting
            }
            val keysToRemove = clientKeys - activeKeys.toSet()
            state.copy(
                sessions = state.sessions - keysToRemove,
                clientSessionHistory = emptyList(),
                selectedKey = if (state.selectedKey in keysToRemove) null else state.selectedKey
            )
        }
        viewModelScope.launch {
            ClientSessionStore.clearAll(getApplication())
        }
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
            SppDeviceStore.save(getApplication(), newList)
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

    fun updateSpeedTestPayload(payload: String) {
        val key = _uiState.value.selectedKey ?: return
        updateSession(key) { it.copy(speedTestPayload = payload) }
    }

    fun toggleSpeedTestMode() {
        val key = _uiState.value.selectedKey ?: return
        val session = _uiState.value.sessions[key] ?: return
        if (session.speedTestRunning) return

        val next =
            when (session.speedTestMode) {
                SppSpeedTestMode.TxOnly -> SppSpeedTestMode.RxOnly
                SppSpeedTestMode.RxOnly -> SppSpeedTestMode.TxOnly
                SppSpeedTestMode.Duplex -> SppSpeedTestMode.TxOnly
            }
        updateSession(key) { it.copy(speedTestMode = next) }
    }

    fun remove(address: String) {
        disconnect(address)
        viewModelScope.launch {
            val newList =
                _uiState.value.registered.filterNot { it.address == address || it.uuid == address }
            SppDeviceStore.save(getApplication(), newList)
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

            val runtime = runtimes.computeIfAbsent(key) { SessionRuntime() }

            if (session.device.role == SppRole.Client) {
                if (localSocketMode) {
                    // LocalSocket 模式
                    val mgr = LocalSocketClientManager(
                        getApplication<Application>().applicationContext,
                        session.device.uuid
                    )
                    runtime.client = mgr
                    runtime.server = null
                    updateSession(key) { it.copy(connectionState = SppConnectionState.Connecting) }
                    observeLocalSocketClientState(key, mgr)
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
                if (session.device.address.isBlank()) {
                    updateSession(key) { it.copy(lastError = "地址不能为空") }
                    return
                }
                val dev = runCatching { adapter?.getRemoteDevice(session.device.address) }.getOrNull() ?: run {
                    updateSession(key) { it.copy(lastError = "无效的蓝牙地址格式（需要 XX:XX:XX:XX:XX:XX）") }
                    return
                }
                val secure = session.securityMode == SecurityMode.Secure
                val mgr =
                    SppClientManager(getApplication<Application>().applicationContext, dev, uuid, secure)
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
                }
            } else {
                if (localSocketMode) {
                    val mgr = LocalSocketServerManager(
                        getApplication<Application>().applicationContext,
                        session.device.uuid
                    )
                    runtime.server = mgr
                    runtime.client = null
                    updateSession(key) { it.copy(serverListenerState = ServerListenerState.Listening) }
                    observeLocalSocketServerState(key, mgr)
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val ok = mgr.register()
                            if (!ok) {
                                updateSession(key) {
                                    it.copy(
                                        connectionState = SppConnectionState.Error,
                                        lastError = "LocalSocket 注册监听失败"
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
                } else {
                val mgr = SppServerManager(getApplication<Application>().applicationContext, uuid)
                runtime.server = mgr
                runtime.client = null
                updateSession(key) { it.copy(serverListenerState = ServerListenerState.Listening) }
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

    fun clearSelectedKey() {
        _uiState.update { it.copy(selectedKey = null) }
    }

    /**
     * 选中一个活跃的 Client 会话（用于从会话列表导航到详情页）。
     */
    fun selectClientSession(sessionId: String) {
        val key = "client:$sessionId"
        if (_uiState.value.sessions.containsKey(key)) {
            _uiState.update { it.copy(selectedKey = key) }
        }
    }

    /**
     * 选中一个历史 Client 会话（用于从会话列表导航到只读详情页）。
     * 从 clientSessionHistory 中找到快照，构建临时 SppSession 放入 sessions map。
     */
    fun selectClientHistorySession(sessionId: String) {
        val key = "client:$sessionId"
        val existing = _uiState.value.sessions[key]
        if (existing != null) {
            // Session still in memory (e.g. Error state)
            _uiState.update { it.copy(selectedKey = key) }
            return
        }
        // Build temporary session from history snapshot
        val snapshot = _uiState.value.clientSessionHistory.find { it.sessionId == sessionId } ?: return
        val tempSession = SppSession(
            device = SppDevice(
                name = snapshot.remoteDeviceName ?: snapshot.remoteDeviceAddress,
                address = snapshot.remoteDeviceAddress,
                uuid = snapshot.uuid,
                role = SppRole.Client
            ),
            connectionState = SppConnectionState.Closed,
            chat = snapshot.chat,
            speedTestTxAvgBps = snapshot.speedTestTxAvgBps,
            speedTestRxAvgBps = snapshot.speedTestRxAvgBps
        )
        _uiState.update { state ->
            state.copy(
                selectedKey = key,
                sessions = state.sessions + (key to tempSession)
            )
        }
    }

    private fun disconnect(key: String) {
        val runtime = runtimes[key] ?: return
        // Save client session snapshot BEFORE clearing state
        if (key.startsWith("client:")) {
            val sessionToSnapshot = _uiState.value.sessions[key]
            if (sessionToSnapshot != null &&
                sessionToSnapshot.connectionState != SppConnectionState.Idle &&
                sessionToSnapshot.connectionState != SppConnectionState.Closed
            ) {
                saveClientSessionSnapshot(key, sessionToSnapshot)
            }
        }
        stopSpeedTest(key)
        runtime.connectionCycleJob?.cancel(); runtime.connectionCycleJob = null
        runtime.pingTestJob?.cancel(); runtime.pingTestJob = null
        runtime.periodicTestJob?.cancel(); runtime.periodicTestJob = null
        runtime.reconnectJob?.cancel(); runtime.reconnectJob = null
        runtime.client?.disconnect()
        (runtime.server as? SocketLikeBluetoothServerManager)?.stopServer()
            ?: runtime.server?.disconnect()
        stopReceiveLoop(key)
        runtime.connectionJob?.cancel(); runtime.connectionJob = null
        runtime.client = null; runtime.server = null
        runtime.lastConnState = null
        updateSession(key) { it.copy(connectionState = SppConnectionState.Closed, serverListenerState = ServerListenerState.Idle, activeTestType = null) }
    }

    // ═══ Server 监听控制 ═══

    /**
     * 开始监听（仅 Server 角色）。
     * 创建 SppServerManager 并调用 register()，设置 serverListenerState=Listening。
     * 如果已在监听中则直接返回。
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun startListening() {
        val key = _uiState.value.selectedKey ?: return
        val session = _uiState.value.sessions[key] ?: return
        if (session.device.role != SppRole.Server) return
        if (session.serverListenerState == ServerListenerState.Listening) return

        try {
            val uuid = runCatching { UUID.fromString(session.device.uuid) }.getOrElse {
                updateSession(key) { it.copy(lastError = "UUID 格式错误") }
                return
            }

            updateSession(key) { it.copy(lastError = null) }
            val runtime = runtimes.computeIfAbsent(key) { SessionRuntime() }

            if (localSocketMode) {
                val mgr = LocalSocketServerManager(
                    getApplication<Application>().applicationContext,
                    session.device.uuid
                )
                runtime.server = mgr
                runtime.client = null
                updateSession(key) { it.copy(serverListenerState = ServerListenerState.Listening) }
                observeLocalSocketServerState(key, mgr)
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val ok = mgr.register()
                        if (!ok) {
                            updateSession(key) {
                                it.copy(
                                    serverListenerState = ServerListenerState.Error,
                                    lastError = "LocalSocket 注册监听失败"
                                )
                            }
                        }
                    } catch (t: Throwable) {
                        updateSession(key) {
                            it.copy(
                                serverListenerState = ServerListenerState.Error,
                                lastError = t.message
                            )
                        }
                    }
                }
            } else {
                val mgr = SppServerManager(getApplication<Application>().applicationContext, uuid)
                runtime.server = mgr
                runtime.client = null
                updateSession(key) { it.copy(serverListenerState = ServerListenerState.Listening) }
                observeConnectionState(key, mgr)
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val ok = mgr.register()
                        if (!ok) {
                            updateSession(key) {
                                it.copy(
                                    serverListenerState = ServerListenerState.Error,
                                    lastError = "注册监听失败"
                                )
                            }
                        }
                    } catch (t: Throwable) {
                        updateSession(key) {
                            it.copy(
                                serverListenerState = ServerListenerState.Error,
                                lastError = t.message
                            )
                        }
                    }
                }
            }
        } catch (_: SecurityException) {
            updateSession(key) {
                it.copy(
                    serverListenerState = ServerListenerState.Error,
                    lastError = "缺少蓝牙权限（请授权蓝牙连接/扫描）"
                )
            }
        }
    }

    /**
     * 停止监听（仅 Server 角色）。
     * 调用 stopServer()，重置 serverListenerState=Idle、connectionState=Closed、remoteDeviceAddress=null。
     */
    fun stopListening() {
        val key = _uiState.value.selectedKey ?: return
        val runtime = runtimes[key] ?: return

        // 停止所有运行中的测试
        stopSpeedTest(key)
        runtime.connectionCycleJob?.cancel(); runtime.connectionCycleJob = null
        runtime.pingTestJob?.cancel(); runtime.pingTestJob = null
        runtime.periodicTestJob?.cancel(); runtime.periodicTestJob = null

        (runtime.server as? SocketLikeBluetoothServerManager)?.stopServer()
            ?: runtime.server?.disconnect()
        stopReceiveLoop(key)
        runtime.connectionJob?.cancel(); runtime.connectionJob = null
        runtime.server = null
        runtime.lastConnState = null
        runtime.lastListenerState = null

        updateSession(key) {
            it.copy(
                serverListenerState = ServerListenerState.Idle,
                connectionState = SppConnectionState.Closed,
                remoteDeviceAddress = null,
                activeTestType = null
            )
        }
    }

    /**
     * 仅断开当前已连接的客户端，保持 Server 监听（仅 Server 角色）。
     * 调用 disconnectClient()，底层会自动重新 accept。
     */
    fun disconnectServerClient() {
        val key = _uiState.value.selectedKey ?: return
        val runtime = runtimes[key] ?: return

        // 停止运行中的测试
        stopSpeedTest(key)
        runtime.pingTestJob?.cancel(); runtime.pingTestJob = null
        runtime.periodicTestJob?.cancel(); runtime.periodicTestJob = null

        (runtime.server as? SocketLikeBluetoothServerManager)?.disconnectClient()
        stopReceiveLoop(key)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun toggleSpeedTest() {
        val key = _uiState.value.selectedKey ?: return
        val session = _uiState.value.sessions[key] ?: return
        if (session.speedTestRunning) {
            stopSpeedTest(key)
        } else {
            // 统一使用 Test_Packet 格式测速（含 START/EOF 信令协议）
            startPacketSpeedTest(key)
        }
    }


    private fun stopSpeedTest(key: String) {
        val runtime = runtimes[key] ?: return
        runtime.speedTestJob?.cancel()
        runtime.speedTestJob = null
        // TODO: ForegroundService integration — call removeReason(Reason.SpeedTest)
        updateSession(key) { it.copy(speedTestRunning = false) }
        clearActiveTest(key)
    }

    /**
     * 使用 Test_Packet 格式的定量/定时吞吐测速。
     * 新入口，第四阶段 UI 会提供切换。保持现有 toggleSpeedTest()/startSpeedTest() 不变。
     */
    fun startPacketSpeedTest(key: String) {
        val session = _uiState.value.sessions[key] ?: return
        if (!canStartTest(key)) {
            updateSession(key) { it.copy(lastError = "无法启动测速（已有测试运行或未连接）") }
            return
        }
        val runtime = runtimes[key] ?: return
        val mgr = runtime.manager ?: return

        setActiveTest(key, ActiveTestType.SpeedTest)
        stopReceiveLoop(key)

        // 读取配置
        val withCrc = session.speedTestWithCrc
        val targetBytes = session.speedTestTargetBytes
        val sendPayloadSize = session.sendPayloadSize.coerceIn(1, TestPacketBuilder.MAX_PAYLOAD_SIZE)

        // 发起方固定为 TxOnly：谁发起测速谁就是发送方，对端通过 START 信令自动进入被动接收
        val txEnabled = true
        val rxEnabled = false

        // 构建 PacketSpeedTestConfig
        val engineStopCondition = StopCondition.ByBytes(targetBytes)
        val config = PacketSpeedTestConfig(
            sendPayloadSize = sendPayloadSize,
            withCrc = withCrc,
            stopCondition = engineStopCondition,
            txEnabled = txEnabled,
            rxEnabled = rxEnabled
        )

        // 信令中 n = 目标完整包字节数
        val signalTargetBytes = targetBytes

        // 重置 UI 状态
        updateSession(key) {
            it.copy(
                lastError = null,
                speedTestRunning = true,
                speedTestWindowOpen = true,
                speedTestElapsedMs = 0,
                speedTestTxTotalBytes = 0,
                speedTestRxTotalBytes = 0,
                speedTestTxInstantBps = null,
                speedTestRxInstantBps = null,
                speedTestTxAvgBps = null,
                speedTestRxAvgBps = null,
                speedTestTxWriteAvgMs = null,
                speedTestTxWriteMaxMs = null,
                speedTestRxReadAvgMs = null,
                speedTestRxReadMaxMs = null,
                speedTestRxReadAvgBytes = null,
                speedTestTxFirstWriteDelayMs = null,
                speedTestRxFirstByteDelayMs = null,
                speedTestSamples = emptyList()
            )
        }
        appendChat(key, SppChatDirection.System,
            "定量测速开始 (payload=$sendPayloadSize, crc=$withCrc)")

        runtime.speedTestJob?.cancel()
        runtime.speedTestJob = viewModelScope.launch {
            try {
                // 发送 START:<n>; 信令
                val signal = "START:$signalTargetBytes;"
                val signalOk = withContext(Dispatchers.IO) {
                    mgr.send(signal.encodeToByteArray())
                }
                if (!signalOk) {
                    updateSession(key) { it.copy(lastError = "发送 START 信令失败") }
                    appendChat(key, SppChatDirection.System, "定量测速失败：发送信令失败")
                    return@launch
                }

                // 等待 START_ACK
                val ackReceived = withContext(Dispatchers.IO) {
                    waitForAck(mgr, REMOTE_START_ACK, 5000L)
                }
                if (!ackReceived) {
                    updateSession(key) { it.copy(lastError = "对端未响应 START_ACK") }
                    appendChat(key, SppChatDirection.System, "定量测速失败：对端未响应")
                    return@launch
                }

                // 调用 speedTestWithPacketFormat
                val result = mgr.speedTestWithPacketFormat(
                    config = config,
                    diagnostics = { diag -> applySpeedTestDiagnostics(key, diag) },
                    progress = { txInstant, rxInstant, txAvg, rxAvg, txTotalBytes, rxTotalBytes, elapsedMs ->
                        updateSession(key) { s ->
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

                // 等待对端 EOF（接收端发送 EOF 表示接收完成）
                // EOF 到达时间才是真正的端到端传输完成时间，用于计算真实 TX 吞吐
                val eofStartMs = System.currentTimeMillis()
                val eofReceived = withContext(Dispatchers.IO) {
                    waitForAck(mgr, REMOTE_EOF, 10000L)
                }
                val eofTimeMs = System.currentTimeMillis()

                if (!eofReceived) {
                    appendChat(key, SppChatDirection.System, "未收到对端 EOF 确认")
                }

                if (result != null) {
                    // 用 EOF 到达时间重新计算 TX 平均速率（端到端真实吞吐）
                    // result.txAvgBps 是发送方本地写缓冲速率，不反映链路速度
                    val txBytesTotal = result.txTotalBytes
                    val txFirstWriteDelayMs = result.txFirstWriteDelayMs ?: 0L
                    // 从第一次 write() 到 EOF 收到的总时间 = 真实端到端传输时间
                    val endToEndTxBps = if (eofReceived && txBytesTotal > 0) {
                        // 测速开始到 EOF 收到的总耗时（毫秒）
                        val totalElapsedMs = result.durationMs + (eofTimeMs - eofStartMs)
                        // 减去 START_ACK 等待时间（txFirstWriteDelayMs 包含了信令交互时间）
                        val txDurationSec = (totalElapsedMs - txFirstWriteDelayMs).coerceAtLeast(1L) / 1000.0
                        txBytesTotal.toDouble() / txDurationSec
                    } else {
                        result.txAvgBps  // EOF 未收到时回退到本地速率
                    }

                    updateSession(key) {
                        it.copy(
                            speedTestTxAvgBps = endToEndTxBps,
                            speedTestTxWriteAvgMs = result.txWriteAvgMs,
                            speedTestTxWriteMaxMs = result.txWriteMaxMs,
                            speedTestRxReadAvgMs = result.rxReadAvgMs,
                            speedTestRxReadMaxMs = result.rxReadMaxMs,
                            speedTestRxReadAvgBytes = result.rxReadAvgBytes,
                            speedTestTxFirstWriteDelayMs = result.txFirstWriteDelayMs,
                            speedTestRxFirstByteDelayMs = result.rxFirstByteDelayMs
                        )
                    }

                    // 校验模式下检查 sequenceErrors
                    if (withCrc && (result.sequenceErrors ?: 0) > 0) {
                        // 需求 2.9：序号不匹配时停止测试、断开连接、显示错误详情
                        val errMsg = "序号校验错误：${result.sequenceErrors} 个包序号不连续" +
                                "（已验证 ${result.validatedPackets} 包）"
                        updateSession(key) { it.copy(lastError = errMsg) }
                        appendChat(key, SppChatDirection.System, errMsg)
                        // 断开连接
                        disconnect(key)
                        return@launch
                    }

                    val crcInfo = if (withCrc) {
                        "，CRC 通过 ${result.validatedPackets ?: 0} 包"
                    } else ""
                    appendChat(key, SppChatDirection.System,
                        "定量测速完成：${formatBps(endToEndTxBps)}$crcInfo")
                } else {
                    updateSession(key) { it.copy(lastError = "测速失败") }
                    appendChat(key, SppChatDirection.System, "定量测速失败")
                }
            } catch (_: CancellationException) {
                // 取消时也发送 EOF
                runCatching {
                    withContext(NonCancellable + Dispatchers.IO) {
                        mgr.send(REMOTE_EOF.encodeToByteArray())
                    }
                }
                appendChat(key, SppChatDirection.System, "定量测速已停止")
            } catch (t: Throwable) {
                updateSession(key) { it.copy(lastError = t.message ?: "测速异常") }
                appendChat(key, SppChatDirection.System,
                    "定量测速异常：${t.message ?: t::class.java.simpleName}")
            } finally {
                updateSession(key) { it.copy(speedTestRunning = false) }
                runtime.speedTestJob = null
                clearActiveTest(key)
                val latest = _uiState.value.sessions[key]
                if (latest?.connectionState == SppConnectionState.Connected && runtime.manager != null) {
                    startReceiveLoop(key)
                }
            }
        }
    }

    private fun applySpeedTestDiagnostics(key: String, diag: SpeedTestDiagnostics) {
        updateSession(key) {
            it.copy(
                speedTestTxWriteAvgMs = diag.txWriteAvgMs,
                speedTestTxWriteMaxMs = diag.txWriteMaxMs,
                speedTestRxReadAvgMs = diag.rxReadAvgMs,
                speedTestRxReadMaxMs = diag.rxReadMaxMs,
                speedTestRxReadAvgBytes = diag.rxReadAvgBytes,
                speedTestTxFirstWriteDelayMs = diag.txFirstWriteDelayMs,
                speedTestRxFirstByteDelayMs = diag.rxFirstByteDelayMs
            )
        }
    }

    private fun startRemoteRxSpeedTest(key: String, targetBytes: Long) {
        if (targetBytes <= 0) return
        val session = _uiState.value.sessions[key] ?: return
        if (session.activeTestType != null) {
            return
        }
        val runtime = runtimes[key] ?: return
        val mgr = runtime.manager ?: return
        if (session.connectionState != SppConnectionState.Connected) {
            return
        }
        if (session.speedTestRunning) {
            return
        }
        runtime.controlBuffer.clear()

        setActiveTest(key, ActiveTestType.PassiveReceive)
        stopReceiveLoop(key)
        updateSession(key) {
            it.copy(
                lastError = null,
                speedTestMode = SppSpeedTestMode.RxOnly,
                speedTestWindowOpen = false,  // 被动接收不弹出测速面板
                speedTestRunning = true,
                speedTestElapsedMs = 0,
                speedTestTxTotalBytes = 0,
                speedTestRxTotalBytes = 0,
                speedTestTxInstantBps = null,
                speedTestRxInstantBps = null,
                speedTestTxAvgBps = null,
                speedTestRxAvgBps = null,
                speedTestTxWriteAvgMs = null,
                speedTestTxWriteMaxMs = null,
                speedTestRxReadAvgMs = null,
                speedTestRxReadMaxMs = null,
                speedTestRxReadAvgBytes = null,
                speedTestTxFirstWriteDelayMs = null,
                speedTestRxFirstByteDelayMs = null,
                speedTestSamples = emptyList()
            )
        }

        runtime.speedTestJob?.cancel()
        runtime.speedTestJob = viewModelScope.launch {
            var ackSent = false
            try {
                val currentSession = _uiState.value.sessions[key]
                val payloadSize = (currentSession?.receiveBufferSize ?: 256)
                    .coerceIn(1, 32 * 1024)

                val result = mgr.speedTestRxUntilBytes(
                    targetBytes = targetBytes,
                    maxDurationMs = REMOTE_RX_MAX_DURATION_MS,
                    payloadSize = payloadSize,
                    diagnostics = { diag -> applySpeedTestDiagnostics(key, diag) },
                    onReceiverReady = {
                        val ackOk =
                            withContext(Dispatchers.IO) {
                                mgr.send(REMOTE_START_ACK.encodeToByteArray())
                            }
                        ackSent = ackOk
                        if (!ackOk) {
                            updateSession(key) { it.copy(lastError = "发送 START_ACK 失败") }
                        }
                        ackOk
                    },
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
                    updateSession(key) {
                        it.copy(
                            speedTestElapsedMs = result.durationMs,
                            speedTestTxTotalBytes = result.txTotalBytes,
                            speedTestRxTotalBytes = result.rxTotalBytes,
                            speedTestTxAvgBps = result.txAvgBps,
                            speedTestRxAvgBps = result.rxAvgBps,
                            speedTestTxWriteAvgMs = result.txWriteAvgMs,
                            speedTestTxWriteMaxMs = result.txWriteMaxMs,
                            speedTestRxReadAvgMs = result.rxReadAvgMs,
                            speedTestRxReadMaxMs = result.rxReadMaxMs,
                            speedTestRxReadAvgBytes = result.rxReadAvgBytes,
                            speedTestTxFirstWriteDelayMs = result.txFirstWriteDelayMs,
                            speedTestRxFirstByteDelayMs = result.rxFirstByteDelayMs
                        )
                    }
                } else {
                    // 仅在非连接断开场景下显示错误
                    val latestConn = _uiState.value.sessions[key]?.connectionState
                    if (latestConn == SppConnectionState.Connected) {
                        val existingError = _uiState.value.sessions[key]?.lastError
                        if (existingError == null) {
                            updateSession(key) { it.copy(lastError = "测速失败") }
                        }
                    }
                    // 如果连接已断开（Closed/Error），不设置 lastError，因为这是预期的断连行为
                }
            } catch (_: CancellationException) {
            } catch (t: Throwable) {
                updateSession(key) { it.copy(lastError = t.message ?: "测速异常") }
            } finally {
                if (ackSent) {
                    // 注意：EOF（3 字节）在 speedTestRxUntilBytes 完成后发送给 A 端。
                    // 在按时间模式下（targetBytes=Long.MAX_VALUE），A 端的 EOF 可能在 B 端
                    // receiver 仍在运行时到达，其 3 字节会被计入 B 端接收统计。
                    // 此偏差在实际大数据量测速中可忽略（3 字节 vs MB/GB 级数据）。
                    runCatching {
                        withContext(NonCancellable + Dispatchers.IO) {
                            mgr.send(REMOTE_EOF.encodeToByteArray())
                        }
                    }
                }
                updateSession(key) { it.copy(speedTestRunning = false) }
                runtime.speedTestJob = null
                // 先重启接收循环，再清除 activeTestType
                val latest = _uiState.value.sessions[key]
                if (latest?.connectionState == SppConnectionState.Connected && runtime.manager != null) {
                    // 清空 socket 缓冲区中的残留数据（测速结束后可能还有未读取的包）
                    withContext(Dispatchers.IO) {
                        try {
                            var drained = 0
                            while (drained < 1024 * 1024) { // 最多清空 1MB
                                val leftover = mgr.receive(4096) ?: break
                                if (leftover.isEmpty()) break
                                drained += leftover.size
                            }
                        } catch (_: Exception) { /* ignore */ }
                    }
                    startReceiveLoop(key)
                }
                clearActiveTest(key)
            }
        }
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
        val runtime = runtimes.computeIfAbsent(key) { SessionRuntime() }
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
        val runtime = runtimes.computeIfAbsent(key) { SessionRuntime() }
        runtime.connectionJob?.cancel()
        runtime.connectionJob = viewModelScope.launch {
            manager.connectionState.collect { s ->
                when (s) {
                    SocketLikeBluetoothServerManager.ConnectionState.Listening -> {
                        updateSession(key) { it.copy(serverListenerState = ServerListenerState.Listening, connectionState = SppConnectionState.Idle) }
                        handleServerListenerStateChange(key, ServerListenerState.Listening)
                    }
                    SocketLikeBluetoothServerManager.ConnectionState.Connected -> {
                        updateSession(key) { it.copy(serverListenerState = ServerListenerState.Listening, connectionState = SppConnectionState.Connected) }
                        handleConnectionState(key, SppConnectionState.Connected)
                    }
                    SocketLikeBluetoothServerManager.ConnectionState.Idle -> {
                        updateSession(key) { it.copy(serverListenerState = ServerListenerState.Idle, connectionState = SppConnectionState.Idle) }
                        handleConnectionState(key, SppConnectionState.Idle)
                    }
                    SocketLikeBluetoothServerManager.ConnectionState.Closed -> {
                        updateSession(key) { it.copy(serverListenerState = ServerListenerState.Idle, connectionState = SppConnectionState.Closed) }
                        handleConnectionState(key, SppConnectionState.Closed)
                    }
                    SocketLikeBluetoothServerManager.ConnectionState.Error -> {
                        updateSession(key) { it.copy(serverListenerState = ServerListenerState.Error) }
                        handleConnectionState(key, SppConnectionState.Error)
                    }
                }
            }
        }
    }

    private fun observeLocalSocketClientState(key: String, manager: LocalSocketClientManager) {
        val runtime = runtimes.computeIfAbsent(key) { SessionRuntime() }
        runtime.connectionJob?.cancel()
        runtime.connectionJob = viewModelScope.launch {
            manager.connectionState.collect { s ->
                val mapped = when (s) {
                    LocalSocketClientManager.ConnectionState.Idle -> SppConnectionState.Idle
                    LocalSocketClientManager.ConnectionState.Connecting -> SppConnectionState.Connecting
                    LocalSocketClientManager.ConnectionState.Connected -> SppConnectionState.Connected
                    LocalSocketClientManager.ConnectionState.Closed -> SppConnectionState.Closed
                    LocalSocketClientManager.ConnectionState.Error -> SppConnectionState.Error
                }
                handleConnectionState(key, mapped)
            }
        }
    }

    private fun observeLocalSocketServerState(key: String, manager: LocalSocketServerManager) {
        val runtime = runtimes.computeIfAbsent(key) { SessionRuntime() }
        runtime.connectionJob?.cancel()
        runtime.connectionJob = viewModelScope.launch {
            manager.connectionState.collect { s ->
                when (s) {
                    LocalSocketServerManager.ConnectionState.Listening -> {
                        updateSession(key) { it.copy(serverListenerState = ServerListenerState.Listening, connectionState = SppConnectionState.Idle) }
                        handleServerListenerStateChange(key, ServerListenerState.Listening)
                    }
                    LocalSocketServerManager.ConnectionState.Connected -> {
                        updateSession(key) { it.copy(serverListenerState = ServerListenerState.Listening, connectionState = SppConnectionState.Connected) }
                        handleConnectionState(key, SppConnectionState.Connected)
                    }
                    LocalSocketServerManager.ConnectionState.Idle -> {
                        updateSession(key) { it.copy(serverListenerState = ServerListenerState.Idle, connectionState = SppConnectionState.Idle) }
                        handleConnectionState(key, SppConnectionState.Idle)
                    }
                    LocalSocketServerManager.ConnectionState.Closed -> {
                        updateSession(key) { it.copy(serverListenerState = ServerListenerState.Idle, connectionState = SppConnectionState.Closed) }
                        handleConnectionState(key, SppConnectionState.Closed)
                    }
                    LocalSocketServerManager.ConnectionState.Error -> {
                        updateSession(key) { it.copy(serverListenerState = ServerListenerState.Error) }
                        handleConnectionState(key, SppConnectionState.Error)
                    }
                }
            }
        }
    }

    private fun handleConnectionState(key: String, mapped: SppConnectionState) {
        updateSession(key) { it.copy(connectionState = mapped) }

        val runtime = runtimes.computeIfAbsent(key) { SessionRuntime() }
        if (mapped == SppConnectionState.Closed || mapped == SppConnectionState.Error) {
            runtime.controlBuffer.clear()
        }
        if (runtime.lastConnState != mapped) {
            runtime.lastConnState = mapped
            val msg = when (mapped) {
                SppConnectionState.Idle -> "状态: 未连接"
                SppConnectionState.Connecting -> "状态: 连接中…"
                SppConnectionState.Connected -> "状态: 已连接"
                SppConnectionState.Closed -> "状态: 已关闭"
                SppConnectionState.Error -> "状态: 异常"
            }
            appendChat(key, SppChatDirection.System, msg)
        }

        when (mapped) {
            SppConnectionState.Connected -> {
                updateSession(key) { it.copy(connectedAt = System.currentTimeMillis()) }
                // Server Tab 会话轮转：新客户端接入时，若当前 chat 非空则快照旧会话
                val preSession = _uiState.value.sessions[key]
                if (preSession != null && preSession.device.role == SppRole.Server && key.startsWith("server:")) {
                    handleServerSessionRotation(key)
                }

                // 填充 remoteDeviceAddress
                val session = _uiState.value.sessions[key]
                if (session != null) {
                    val remoteAddr = if (session.device.role == SppRole.Client) {
                        session.device.address.ifBlank { null }
                    } else {
                        (runtime.server as? SocketLikeBluetoothServerManager)?.connectedRemoteAddress
                            ?: if (runtime.server is LocalSocketServerManager) "localsocket" else null
                    }
                    updateSession(key) { it.copy(remoteDeviceAddress = remoteAddr) }
                }
                // TODO: ForegroundService integration — call addReason(Reason.SppConnection)
                if (session != null && !session.speedTestRunning) startReceiveLoop(key)
            }

            SppConnectionState.Closed, SppConnectionState.Error, SppConnectionState.Idle -> {
                // 清空 remoteDeviceAddress
                updateSession(key) { it.copy(remoteDeviceAddress = null) }
                if (mapped != SppConnectionState.Idle) {
                    // TODO: ForegroundService integration — call removeReason(Reason.SppConnection)
                    stopSpeedTest(key)
                    stopReceiveLoop(key)
                }
                // Save Client session snapshot for history
                if (mapped == SppConnectionState.Closed || mapped == SppConnectionState.Error) {
                    if (key.startsWith("client:")) {
                        val sessionToSnapshot = _uiState.value.sessions[key]
                        if (sessionToSnapshot != null) {
                            saveClientSessionSnapshot(key, sessionToSnapshot)
                        }
                    }
                }
            }

            else -> {}
        }
    }

    private fun handleServerListenerStateChange(key: String, state: ServerListenerState) {
        val runtime = runtimes.computeIfAbsent(key) { SessionRuntime() }
        if (runtime.lastListenerState != state) {
            runtime.lastListenerState = state
            val msg = when (state) {
                ServerListenerState.Idle -> "状态: 监听已停止"
                ServerListenerState.Listening -> "状态: 监听中…"
                ServerListenerState.Error -> "状态: 监听异常"
            }
            appendChat(key, SppChatDirection.System, msg)
        }
    }

    private fun appendChat(key: String, direction: SppChatDirection, text: String) {
        val line = text.trim()
        if (line.isBlank()) return
        val session = _uiState.value.sessions[key] ?: return
        val muteConsole =
            session.muteConsoleDuringTest && (session.speedTestWindowOpen || session.speedTestRunning || session.activeTestType == ActiveTestType.PassiveReceive)
        if (muteConsole) return
        val item = SppChatItem(id = chatId.incrementAndGet(), direction = direction, text = line)
        updateSession(key) { state ->
            state.copy(chat = (listOf(item) + state.chat).take(500))
        }
    }

    private fun isPotentialSignalBuffer(text: String): Boolean {
        val trimmed = text.trimStart()
        if (trimmed.isEmpty()) return false
        return ALL_SIGNAL_PREFIXES.any { prefix ->
            if (trimmed.length <= prefix.length) {
                prefix.startsWith(trimmed)
            } else {
                trimmed.startsWith(prefix)
            }
        }
    }

    private fun tryConsumeSignalCommand(runtime: SessionRuntime): SignalCommand? {
        val text = runtime.controlBuffer.toString()
        val start = text.indexOfFirst { !it.isWhitespace() }
        if (start == -1) return null

        // Try each signal prefix
        val matchedPrefix = ALL_SIGNAL_PREFIXES.firstOrNull { prefix ->
            text.startsWith(prefix, start, false)
        } ?: return null

        val paramsStart = start + matchedPrefix.length
        // Find the semicolon terminator
        val semiIndex = text.indexOf(';', paramsStart)
        if (semiIndex == -1) {
            // No semicolon yet — check if we're still accumulating valid content
            // If there's clearly invalid content (non-digit/non-colon for params), clear buffer
            val remaining = text.substring(paramsStart)
            if (remaining.any { it != ':' && !it.isDigit() && !it.isLetter() && it != '=' }) {
                runtime.controlBuffer.clear()
                return null
            }
            // Wait for ';' to arrive in a future chunk.
            return null
        }

        val paramsStr = text.substring(paramsStart, semiIndex)

        val command: SignalCommand? = when (matchedPrefix) {
            REMOTE_START_PREFIX -> {
                // START:<n>;
                val n = paramsStr.toLongOrNull()
                if (n != null) SignalCommand.Start(n) else null
            }
            REMOTE_PERIODIC_PREFIX -> {
                // PERIODIC:<payloadSize>:<stopCondition>;
                val colonIdx = paramsStr.indexOf(':')
                if (colonIdx > 0) {
                    val payloadSize = paramsStr.substring(0, colonIdx).toIntOrNull()
                    val stopCondition = paramsStr.substring(colonIdx + 1)
                    if (payloadSize != null && stopCondition.isNotEmpty()) {
                        SignalCommand.Periodic(payloadSize, stopCondition)
                    } else null
                } else null
            }
            REMOTE_PING_PREFIX -> {
                // PING:<payloadSize>:<count>;
                val colonIdx = paramsStr.indexOf(':')
                if (colonIdx > 0) {
                    val payloadSize = paramsStr.substring(0, colonIdx).toIntOrNull()
                    val count = paramsStr.substring(colonIdx + 1).toIntOrNull()
                    if (payloadSize != null && count != null) {
                        SignalCommand.Ping(payloadSize, count)
                    } else null
                } else null
            }
            else -> null
        }

        // Clear buffer regardless of parse success (we consumed up to the semicolon)
        runtime.controlBuffer.clear()
        return command
    }

    private fun startReceiveLoop(key: String) {
        stopReceiveLoop(key)
        val runtime = runtimes[key] ?: return
        val mgr = runtime.manager ?: return
        runtime.receiveJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                val session = _uiState.value.sessions[key] ?: break
                if (session.speedTestRunning && runtime.controlBuffer.isNotEmpty()) {
                    runtime.controlBuffer.clear()
                }
                val buf = mgr.receive(session.receiveBufferSize) ?: break
                if (buf.isNotEmpty()) {
                    var consumedByControl = false
                    if (!session.speedTestRunning) {
                        val decoded = runCatching { buf.toString(Charsets.UTF_8) }.getOrNull()
                        val isValidTextChunk =
                            decoded != null &&
                                    decoded.isNotEmpty() &&
                                    decoded.length <= 64 &&
                                    !decoded.contains('\uFFFD') &&
                                    decoded.all { ch -> ch.isWhitespace() || ch.code in 0x20..0x7E }
                        if (isValidTextChunk) {
                            val shouldBuffer =
                                runtime.controlBuffer.isNotEmpty() || isPotentialSignalBuffer(
                                    decoded
                                )
                            if (shouldBuffer) {
                                runtime.controlBuffer.append(decoded)
                                if (runtime.controlBuffer.length > 256) {
                                    runtime.controlBuffer.delete(
                                        0,
                                        runtime.controlBuffer.length - 256
                                    )
                                }

                                val cmd = tryConsumeSignalCommand(runtime)
                                if (cmd != null) {
                                    dispatchSignalCommand(key, cmd)
                                    return@launch
                                }

                                val bufferText = runtime.controlBuffer.toString()
                                val potential = isPotentialSignalBuffer(bufferText)
                                if (!potential) {
                                    runtime.controlBuffer.clear()
                                } else {
                                    consumedByControl = ALL_SIGNAL_PREFIXES.any { prefix ->
                                        bufferText.trimStart().startsWith(prefix)
                                    }
                                }
                            }
                        } else if (runtime.controlBuffer.isNotEmpty()) {
                            runtime.controlBuffer.clear()
                        }
                    }
                    val muteConsole =
                        session.muteConsoleDuringTest &&
                                (session.speedTestWindowOpen || session.speedTestRunning || session.activeTestType == ActiveTestType.PassiveReceive)
                    // 过滤所有控制信令，不显示到 chat
                    val trimmedText = if (buf.size <= 32) runCatching { buf.toString(Charsets.UTF_8).trim() }.getOrNull() else null
                    val isSignal = trimmedText != null && SIGNAL_KEYWORDS.contains(trimmedText)
                    if (!consumedByControl && !muteConsole && !isSignal) {
                        val text = formatIncoming(buf, session.parseIncomingAsText)
                        appendChat(key, SppChatDirection.In, text)
                    }
                } else {
                    if (!session.speedTestRunning && runtime.controlBuffer.isNotEmpty()) {
                        val cmd = tryConsumeSignalCommand(runtime)
                        if (cmd != null) {
                            dispatchSignalCommand(key, cmd)
                            return@launch
                        }
                        if (!isPotentialSignalBuffer(runtime.controlBuffer.toString())) {
                            runtime.controlBuffer.clear()
                        }
                    } else if (session.speedTestRunning && runtime.controlBuffer.isNotEmpty()) {
                        runtime.controlBuffer.clear()
                    }
                    delay(10)
                }
            }
        }
    }

    private fun dispatchSignalCommand(key: String, cmd: SignalCommand) {
        when (cmd) {
            is SignalCommand.Start -> {
                if (cmd.targetBytes > 0) {
                    viewModelScope.launch { startRemoteRxSpeedTest(key, cmd.targetBytes) }
                }
            }
            is SignalCommand.Periodic -> {
                viewModelScope.launch { startRemotePeriodicReceive(key, cmd) }
            }
            is SignalCommand.Ping -> {
                viewModelScope.launch { startRemotePingEcho(key, cmd) }
            }
        }
    }

    private suspend fun startRemotePeriodicReceive(key: String, cmd: SignalCommand.Periodic) {
        val session = _uiState.value.sessions[key] ?: return
        if (session.activeTestType != null) return  // 已有测试运行，拒绝被动模式
        val runtime = runtimes[key] ?: return
        val mgr = runtime.manager ?: return
        if (session.connectionState != SppConnectionState.Connected) return
        runtime.controlBuffer.clear()

        setActiveTest(key, ActiveTestType.PassiveReceive)

        // 回复 PERIODIC_ACK
        val ackOk = withContext(Dispatchers.IO) {
            mgr.send(REMOTE_PERIODIC_ACK.encodeToByteArray())
        }
        if (!ackOk) {
            appendChat(key, SppChatDirection.System, "发送 PERIODIC_ACK 失败")
            clearActiveTest(key)
            startReceiveLoop(key)
            return
        }
        appendChat(key, SppChatDirection.System, "收到定时发送请求 (payload=${cmd.payloadSize}, stop=${cmd.stopCondition})，开始接收")

        // 切换到 FrameSyncReader 接收模式
        val inp = mgr.inputStream ?: run {
            appendChat(key, SppChatDirection.System, "定时接收失败：无输入流")
            clearActiveTest(key)
            startReceiveLoop(key)
            return
        }
        val frameSyncReader = FrameSyncReader(inp, withCrc = true)

        var receivedCount = 0L
        var crcPassCount = 0L
        var crcFailCount = 0L
        var seqJumpCount = 0L
        var expectedSeq = 0L
        var totalBytes = 0L

        try {
            withContext(Dispatchers.IO) {
                // idle 超时检测：发送端发送 PERIODIC_DONE 后不再发送 Test_Packet，
                // 但 FrameSyncReader 在扫描定界符时会跳过纯文本字节（如 PERIODIC_DONE），
                // 导致 readPacket() 永远阻塞。通过 input.available() 轮询检测 idle 状态，
                // 连续 IDLE_TIMEOUT_MS 无新数据则认为发送端已停止，退出循环切回普通接收模式。
                var lastDataTime = System.currentTimeMillis()
                while (isActive) {
                    val available = inp.available()
                    if (available <= 0) {
                        if (System.currentTimeMillis() - lastDataTime > IDLE_TIMEOUT_MS) {
                            break // idle 超时，发送端可能已发送 PERIODIC_DONE 并停止
                        }
                        Thread.sleep(IDLE_POLL_INTERVAL_MS)
                        continue
                    }
                    lastDataTime = System.currentTimeMillis()
                    val packet = frameSyncReader.readPacket() ?: break // 流结束

                    receivedCount++
                    totalBytes += packet.rawBytes.size

                    if (packet.crcValid) {
                        crcPassCount++
                    } else {
                        crcFailCount++
                    }

                    // 序号跳跃检测（仅记录统计，不触发断连 — 需求 4.9）
                    if (receivedCount > 1 && packet.sequenceNumber != expectedSeq) {
                        seqJumpCount++
                    }
                    expectedSeq = (packet.sequenceNumber + 1) and 0xFFFFFFFFL
                }
            }
        } catch (_: CancellationException) {
            appendChat(key, SppChatDirection.System, "定时接收已取消")
        } catch (e: Exception) {
            // IOException 通常表示连接断开
            appendChat(key, SppChatDirection.System, "定时接收结束：${e.message ?: "连接中断"}")
        }

        // 显示接收摘要系统消息
        val summary = buildString {
            append("定时接收摘要：已接收 $receivedCount 包")
            append("，CRC 通过 $crcPassCount")
            if (crcFailCount > 0) append("，失败 $crcFailCount")
            if (seqJumpCount > 0) append("，序号跳跃 $seqJumpCount 次")
            append("，总字节 $totalBytes")
        }
        appendChat(key, SppChatDirection.System, summary)

        clearActiveTest(key)

        // 切回普通接收模式
        val latest = _uiState.value.sessions[key]
        if (latest?.connectionState == SppConnectionState.Connected && runtime.manager != null) {
            startReceiveLoop(key)
        }
    }

    private suspend fun startRemotePingEcho(key: String, cmd: SignalCommand.Ping) {
        val session = _uiState.value.sessions[key] ?: return
        if (session.activeTestType != null) return  // 已有测试运行，拒绝被动模式
        val runtime = runtimes[key] ?: return
        val mgr = runtime.manager ?: return
        if (session.connectionState != SppConnectionState.Connected) return
        runtime.controlBuffer.clear()

        setActiveTest(key, ActiveTestType.PassiveReceive)

        // 回复 PING_ACK
        val ackOk = withContext(Dispatchers.IO) {
            mgr.send(REMOTE_PING_ACK.encodeToByteArray())
        }
        if (!ackOk) {
            appendChat(key, SppChatDirection.System, "发送 PING_ACK 失败")
            clearActiveTest(key)
            startReceiveLoop(key)
            return
        }
        appendChat(key, SppChatDirection.System, "收到 Ping 请求，开始 Echo 回传")

        // 切换到 FrameSyncReader 接收模式
        val inp = mgr.inputStream ?: run {
            appendChat(key, SppChatDirection.System, "Ping Echo 失败：无输入流")
            clearActiveTest(key)
            startReceiveLoop(key)
            return
        }
        val frameSyncReader = FrameSyncReader(inp, withCrc = true)

        // 使用 count 来确定何时结束 Echo 循环
        // count=0 表示无限，此时依赖连接断开或超时来结束
        val expectedCount = cmd.count

        try {
            var echoCount = 0L
            withContext(Dispatchers.IO) {
                // idle 超时检测：发送端发送 PING_DONE 后不再发送 Test_Packet，
                // 但 FrameSyncReader 在扫描定界符时会跳过纯文本字节（如 PING_DONE），
                // 导致 readPacket() 永远阻塞。通过 input.available() 轮询检测 idle 状态，
                // 连续 IDLE_TIMEOUT_MS 无新数据则认为发送端已停止，退出循环切回普通接收模式。
                var lastDataTime = System.currentTimeMillis()
                while (isActive) {
                    val available = inp.available()
                    if (available <= 0) {
                        if (System.currentTimeMillis() - lastDataTime > IDLE_TIMEOUT_MS) {
                            break // idle 超时，发送端可能已发送 PING_DONE 并停止
                        }
                        Thread.sleep(IDLE_POLL_INTERVAL_MS)
                        continue
                    }
                    lastDataTime = System.currentTimeMillis()
                    val packet = frameSyncReader.readPacket() ?: break // 流结束
                    val reply = PingEchoHandler.handlePacket(packet)
                    if (reply != null) {
                        mgr.send(reply)
                        echoCount++
                        // 如果已知 count，达到后退出
                        if (expectedCount > 0 && echoCount >= expectedCount) break
                    }
                    // 非 Ping Request 包丢弃，继续读取
                }
            }
            appendChat(key, SppChatDirection.System, "Ping Echo 结束，共回传 $echoCount 包")
        } catch (_: CancellationException) {
            appendChat(key, SppChatDirection.System, "Ping Echo 已取消")
        } catch (e: Exception) {
            appendChat(key, SppChatDirection.System, "Ping Echo 异常：${e.message}")
        }

        clearActiveTest(key)

        // 切回普通接收模式
        val latest = _uiState.value.sessions[key]
        if (latest?.connectionState == SppConnectionState.Connected && runtime.manager != null) {
            startReceiveLoop(key)
        }
    }

    // ═══ 定时/循环发送 ═══

    fun startPeriodicTest(key: String) {
        val session = _uiState.value.sessions[key] ?: return
        if (!canStartTest(key)) {
            updateSession(key) { it.copy(lastError = "无法启动定时发送（已有测试运行或未连接）") }
            return
        }
        val runtime = runtimes[key] ?: return
        val mgr = runtime.manager ?: return

        setActiveTest(key, ActiveTestType.PeriodicSend)
        stopReceiveLoop(key)

        // 读取配置
        val sendPayloadSize = session.periodicTestSendPayloadSize.coerceIn(1, TestPacketBuilder.MAX_PAYLOAD_SIZE)
        val interval = if (session.periodicTestInterval == 0L) 0L else session.periodicTestInterval.coerceAtLeast(10)
        val stopCondition = session.periodicTestStopCondition

        // 重置统计
        updateSession(key) {
            it.copy(
                periodicTestRunning = true,
                periodicTestSentCount = 0,
                periodicTestSentBytes = 0,
                periodicTestSuccessCount = 0,
                periodicTestFailCount = 0,
                periodicTestElapsedMs = 0,
                lastError = null
            )
        }

        val stopConditionStr = when (stopCondition) {
            is PeriodicStopCondition.ByCount -> "count=${stopCondition.count}"
            is PeriodicStopCondition.ByTime -> "time=${stopCondition.seconds}"
        }
        appendChat(key, SppChatDirection.System,
            "定时发送开始 (payload=$sendPayloadSize, interval=${if (interval == 0L) "最大速率" else "${interval}ms"}, stop=$stopConditionStr)")

        runtime.periodicTestJob?.cancel()
        runtime.periodicTestJob = viewModelScope.launch {
            try {
                // 发送信令 PERIODIC:<sendPayloadSize>:<stop_condition>;
                val signal = "PERIODIC:$sendPayloadSize:$stopConditionStr;"
                val signalOk = withContext(Dispatchers.IO) {
                    mgr.send(signal.encodeToByteArray())
                }
                if (!signalOk) {
                    updateSession(key) { it.copy(lastError = "发送 PERIODIC 信令失败") }
                    appendChat(key, SppChatDirection.System, "定时发送失败：发送信令失败")
                    return@launch
                }

                // 等待 PERIODIC_ACK（5 秒超时）
                val ackReceived = withContext(Dispatchers.IO) {
                    waitForAck(mgr, REMOTE_PERIODIC_ACK, 5000L)
                }
                if (!ackReceived) {
                    updateSession(key) { it.copy(lastError = "对端未响应 PERIODIC_ACK") }
                    appendChat(key, SppChatDirection.System, "定时发送失败：对端未响应")
                    return@launch
                }

                // 启动发送协程
                var seq = 0L
                var consecutiveFailCount = 0
                var sentCount = 0L
                var sentBytes = 0L
                var successCount = 0L
                var failCount = 0L
                val buffer = TestPacketBuilder.allocateBuffer(sendPayloadSize, withCrc = true)
                val startTime = System.currentTimeMillis()
                var lastUiUpdateTime = startTime

                withContext(Dispatchers.IO) {
                    while (isActive) {
                        // 检查停止条件
                        when (stopCondition) {
                            is PeriodicStopCondition.ByCount -> {
                                if (sentCount >= stopCondition.count) break
                            }
                            is PeriodicStopCondition.ByTime -> {
                                val elapsed = System.currentTimeMillis() - startTime
                                if (elapsed >= stopCondition.seconds * 1000) break
                            }
                        }

                        // 更新序号和 CRC
                        TestPacketBuilder.updateSequenceAndCrc(buffer, seq, sendPayloadSize)

                        // 发送
                        val ok = mgr.send(buffer)
                        if (ok) {
                            sentCount++
                            sentBytes += buffer.size
                            successCount++
                            consecutiveFailCount = 0
                        } else {
                            failCount++
                            consecutiveFailCount++
                            if (consecutiveFailCount >= 10) {
                                appendChat(key, SppChatDirection.System, "连续发送失败 10 次，测试已停止")
                                break
                            }
                        }
                        seq++

                        // 间隔控制
                        if (interval > 0) {
                            delay(interval)
                        }

                        // 每 500ms 更新 UI 状态
                        val now = System.currentTimeMillis()
                        if (now - lastUiUpdateTime >= 500) {
                            val elapsed = now - startTime
                            updateSession(key) {
                                it.copy(
                                    periodicTestSentCount = sentCount,
                                    periodicTestSentBytes = sentBytes,
                                    periodicTestSuccessCount = successCount,
                                    periodicTestFailCount = failCount,
                                    periodicTestElapsedMs = elapsed
                                )
                            }
                            lastUiUpdateTime = now
                        }
                    }
                }

                // 最终更新 UI
                val finalElapsed = System.currentTimeMillis() - startTime
                updateSession(key) {
                    it.copy(
                        periodicTestSentCount = sentCount,
                        periodicTestSentBytes = sentBytes,
                        periodicTestSuccessCount = successCount,
                        periodicTestFailCount = failCount,
                        periodicTestElapsedMs = finalElapsed
                    )
                }

                // 发送 PERIODIC_DONE
                withContext(NonCancellable + Dispatchers.IO) {
                    mgr.send(REMOTE_PERIODIC_DONE.encodeToByteArray())
                }

                appendChat(key, SppChatDirection.System,
                    "定时发送完成：已发送 $sentCount 包，成功 $successCount，失败 $failCount，" +
                            "字节 $sentBytes，耗时 ${finalElapsed}ms")
            } catch (_: CancellationException) {
                // 用户手动停止 — 发送 PERIODIC_DONE
                runCatching {
                    withContext(NonCancellable + Dispatchers.IO) {
                        mgr.send(REMOTE_PERIODIC_DONE.encodeToByteArray())
                    }
                }
                appendChat(key, SppChatDirection.System, "定时发送已停止")
            } catch (e: Exception) {
                updateSession(key) { it.copy(lastError = e.message ?: "定时发送异常") }
                appendChat(key, SppChatDirection.System, "定时发送异常：${e.message}")
            } finally {
                updateSession(key) { it.copy(periodicTestRunning = false) }
                runtime.periodicTestJob = null
                clearActiveTest(key)
                // 恢复接收循环
                val latest = _uiState.value.sessions[key]
                if (latest?.connectionState == SppConnectionState.Connected && runtime.manager != null) {
                    startReceiveLoop(key)
                }
            }
        }
    }

    fun stopPeriodicTest(key: String) {
        val runtime = runtimes[key] ?: return
        runtime.periodicTestJob?.cancel()
        runtime.periodicTestJob = null
        clearActiveTest(key)
        updateSession(key) { it.copy(periodicTestRunning = false) }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun startConnectionCycleTest(key: String) {
        val session = _uiState.value.sessions[key] ?: return
        if (session.device.role != SppRole.Client) return
        if (!canStartTest(key)) return
        setActiveTest(key, ActiveTestType.ConnectionCycle)

        // 停止接收循环
        stopReceiveLoop(key)

        val runtime = runtimes[key] ?: return
        // 断开当前连接
        runtime.client?.disconnect()
        runtime.client = null

        val targetCount = session.connectionCycleTargetCount
        val timeout = session.connectionCycleTimeout
        val interval = session.connectionCycleInterval

        // 重置统计并标记运行中
        updateSession(key) {
            it.copy(
                connectionCycleRunning = true,
                connectionCycleTotalCount = 0,
                connectionCycleSuccessCount = 0,
                connectionCycleFailCount = 0,
                connectionCycleAvgMs = 0.0,
                connectionCycleMaxMs = 0,
                connectionCycleMinMs = Long.MAX_VALUE
            )
        }
        appendChat(key, SppChatDirection.System,
            "连接循环压测开始（目标: ${if (targetCount == 0) "无限" else targetCount}次）")

        runtime.connectionCycleJob = viewModelScope.launch(Dispatchers.IO) {
            var successCount = 0
            var failCount = 0
            var totalMs = 0L
            var maxMs = 0L
            var minMs = Long.MAX_VALUE
            var iteration = 0

            try {
                val limit = if (targetCount == 0) Int.MAX_VALUE else targetCount
                for (i in 1..limit) {
                    if (!isActive) break
                    iteration = i

                    val currentSession = _uiState.value.sessions[key] ?: break
                    val uuid = if (!localSocketMode) {
                        runCatching { UUID.fromString(currentSession.device.uuid) }
                            .getOrNull() ?: break
                    } else null
                    val dev = if (!localSocketMode) {
                        adapter?.getRemoteDevice(currentSession.device.address) ?: break
                    } else null

                    // 创建新的 SppClientManager
                    val secure = currentSession.securityMode == SecurityMode.Secure
                    val mgr = if (localSocketMode) {
                        LocalSocketClientManager(
                            getApplication<Application>().applicationContext,
                            currentSession.device.uuid
                        )
                    } else {
                        SppClientManager(
                            getApplication<Application>().applicationContext, dev!!, uuid!!, secure
                        )
                    }
                    runtime.client = mgr

                    var durationMs: Long? = null
                    var failReason: String? = null

                    try {
                        withTimeout(timeout) {
                            withContext(Dispatchers.IO) {
                                mgr.connect()
                            }
                        }
                        // 连接成功
                        durationMs = (mgr as? SocketLikeBluetoothClientManager)?.lastConnectDurationMs
                            ?: (mgr as? LocalSocketClientManager)?.lastConnectDurationMs
                        if (durationMs != null) {
                            successCount++
                            totalMs += durationMs
                            if (durationMs > maxMs) maxMs = durationMs
                            if (durationMs < minMs) minMs = durationMs
                        } else {
                            // connect() 没有抛异常但也没有记录耗时（不太可能，但防御性处理）
                            failCount++
                            failReason = "连接未记录耗时"
                        }
                    } catch (_: TimeoutCancellationException) {
                        failCount++
                        failReason = "连接超时"
                        runCatching { mgr.disconnect() }
                    } catch (e: CancellationException) {
                        // 协程被取消（用户停止），不计入失败
                        throw e
                    } catch (e: Exception) {
                        failCount++
                        failReason = e.message ?: e::class.java.simpleName
                    }

                    // 断开
                    runCatching { mgr.disconnect() }
                    runtime.client = null

                    // 更新统计
                    val avgMs = if (successCount > 0) totalMs.toDouble() / successCount else 0.0
                    updateSession(key) {
                        it.copy(
                            connectionCycleTotalCount = i,
                            connectionCycleSuccessCount = successCount,
                            connectionCycleFailCount = failCount,
                            connectionCycleAvgMs = avgMs,
                            connectionCycleMaxMs = maxMs,
                            connectionCycleMinMs = if (successCount > 0) minMs else Long.MAX_VALUE
                        )
                    }

                    // 追加每次结果到 chat
                    if (durationMs != null) {
                        appendChat(key, SppChatDirection.System,
                            "第${i}次: 成功 ${durationMs}ms")
                    } else {
                        appendChat(key, SppChatDirection.System,
                            "第${i}次: 失败 ($failReason)")
                    }

                    // 等待间隔
                    if (isActive && i < limit) {
                        delay(interval)
                    }
                }
            } catch (_: CancellationException) {
                appendChat(key, SppChatDirection.System, "连接循环压测已手动停止")
            } finally {
                // 汇总统计
                val avgMs = if (successCount > 0) totalMs.toDouble() / successCount else 0.0
                val displayMin = if (successCount > 0) minMs else 0L
                appendChat(key, SppChatDirection.System,
                    "连接循环压测结束：共${iteration}次，成功${successCount}次，失败${failCount}次" +
                            if (successCount > 0) "，平均${String.format(Locale.US, "%.1f", avgMs)}ms" +
                                    "，最大${maxMs}ms，最小${displayMin}ms"
                            else ""
                )
                updateSession(key) { it.copy(connectionCycleRunning = false) }
                clearActiveTest(key)
                runtime.connectionCycleJob = null
                runtime.client = null
            }
        }
    }

    fun stopConnectionCycleTest(key: String) {
        val runtime = runtimes[key] ?: return
        runtime.connectionCycleJob?.cancel()
        runtime.connectionCycleJob = null
        clearActiveTest(key)
        updateSession(key) { it.copy(connectionCycleRunning = false) }
    }

    // ═══ Ping RTT 测量 ═══

    fun startPingTest(key: String) {
        val session = _uiState.value.sessions[key] ?: return
        if (!canStartTest(key)) {
            updateSession(key) { it.copy(lastError = "无法启动 Ping 测试（已有测试运行或未连接）") }
            return
        }
        val runtime = runtimes[key] ?: return
        val mgr = runtime.manager ?: return

        setActiveTest(key, ActiveTestType.PingRtt)
        stopReceiveLoop(key)

        // 读取 Ping 配置
        val paddingSize = session.pingTestPaddingSize.coerceAtLeast(0)
        val sendPayloadSize = maxOf(PingEchoHandler.MIN_PAYLOAD_SIZE, PingEchoHandler.MIN_PAYLOAD_SIZE + paddingSize)
        if (sendPayloadSize > PingEchoHandler.MIN_PAYLOAD_SIZE + session.pingTestPaddingSize) {
            appendChat(key, SppChatDirection.System, "Ping payload 大小已自动调整为最小值 ${PingEchoHandler.MIN_PAYLOAD_SIZE} 字节")
        }
        val targetCount = session.pingTestTargetCount
        val pingInterval = session.pingTestInterval.coerceAtLeast(0)
        val pingTimeout = session.pingTestTimeout.coerceAtLeast(500)

        // 重置统计
        updateSession(key) {
            it.copy(
                pingTestRunning = true,
                pingTestCount = 0,
                pingTestSuccessCount = 0,
                pingTestTimeoutCount = 0,
                pingTestAvgRtt = 0.0,
                pingTestMaxRtt = 0.0,
                pingTestMinRtt = Double.MAX_VALUE,
                pingTestLastRtt = null,
                lastError = null
            )
        }
        appendChat(key, SppChatDirection.System, "Ping 测试开始 (payload=$sendPayloadSize, count=${if (targetCount == 0) "∞" else "$targetCount"})")

        runtime.pingTestJob?.cancel()
        runtime.pingTestJob = viewModelScope.launch {
            try {
                // 发送信令 PING:<sendPayloadSize>:<count>;
                val signal = "PING:$sendPayloadSize:$targetCount;"
                val signalOk = withContext(Dispatchers.IO) {
                    mgr.send(signal.encodeToByteArray())
                }
                if (!signalOk) {
                    updateSession(key) { it.copy(lastError = "发送 PING 信令失败") }
                    appendChat(key, SppChatDirection.System, "Ping 测试失败：发送信令失败")
                    return@launch
                }

                // 等待 PING_ACK（5 秒超时）
                val ackReceived = withContext(Dispatchers.IO) {
                    waitForAck(mgr, REMOTE_PING_ACK, 5000L)
                }
                if (!ackReceived) {
                    updateSession(key) { it.copy(lastError = "对端未响应 PING_ACK") }
                    appendChat(key, SppChatDirection.System, "Ping 测试失败：对端未响应")
                    return@launch
                }

                // 切换到 FrameSyncReader 接收模式
                val inp = mgr.inputStream ?: run {
                    updateSession(key) { it.copy(lastError = "无输入流") }
                    return@launch
                }
                val frameSyncReader = FrameSyncReader(inp, withCrc = true)

                // 循环发送 Ping Request
                var seq = 0L
                var successCount = 0
                var timeoutCount = 0
                var totalRtt = 0.0
                var maxRtt = 0.0
                var minRtt = Double.MAX_VALUE
                val iterations = if (targetCount == 0) Int.MAX_VALUE else targetCount

                for (i in 1..iterations) {
                    if (!isActive) break

                    // 构建 Ping Request Test_Packet
                    val buffer = TestPacketBuilder.allocateBuffer(sendPayloadSize, withCrc = true)
                    // 填充 Ping payload: [0x01][8字节 nanoTime][padding]
                    val sendNs = System.nanoTime()
                    buffer[TestPacketBuilder.HEADER_SIZE] = PingEchoHandler.PING_REQUEST
                    // 写入 timestamp 大端序到 buffer[HEADER_SIZE+1..HEADER_SIZE+8]
                    for (b in 0 until 8) {
                        buffer[TestPacketBuilder.HEADER_SIZE + 1 + b] =
                            (sendNs ushr (56 - b * 8)).toByte()
                    }
                    // 填充序号和 CRC
                    TestPacketBuilder.fillBuffer(buffer, seq, sendPayloadSize, withCrc = true)

                    // 发送
                    val sendOk = withContext(Dispatchers.IO) { mgr.send(buffer) }
                    if (!sendOk) {
                        appendChat(key, SppChatDirection.System, "Ping #$i 发送失败，测试终止")
                        break
                    }

                    // 等待 Ping Reply（使用 available() 轮询 + 超时检测，避免阻塞读取无法被取消）
                    try {
                        val replyDeadline = System.currentTimeMillis() + pingTimeout
                        var gotReply = false
                        withContext(Dispatchers.IO) {
                            while (isActive && System.currentTimeMillis() < replyDeadline) {
                                val available = inp.available()
                                if (available <= 0) {
                                    Thread.sleep(IDLE_POLL_INTERVAL_MS)
                                    continue
                                }
                                val reply = frameSyncReader.readPacket() ?: break // 流结束
                                // 检查是否为 Ping Reply
                                if (reply.payload.isNotEmpty() && reply.payload[0] == PingEchoHandler.PING_REPLY) {
                                    val recvNs = System.nanoTime()
                                    // 从 reply.payload 提取 timestamp
                                    val ts = extractTimestamp(reply.payload)
                                    val rtt = (recvNs - ts) / 1_000_000.0
                                    successCount++
                                    totalRtt += rtt
                                    if (rtt > maxRtt) maxRtt = rtt
                                    if (rtt < minRtt) minRtt = rtt
                                    val avgRtt = totalRtt / successCount
                                    updateSession(key) {
                                        it.copy(
                                            pingTestCount = i,
                                            pingTestSuccessCount = successCount,
                                            pingTestTimeoutCount = timeoutCount,
                                            pingTestAvgRtt = avgRtt,
                                            pingTestMaxRtt = maxRtt,
                                            pingTestMinRtt = minRtt,
                                            pingTestLastRtt = rtt
                                        )
                                    }
                                    gotReply = true
                                    break // 收到匹配的 Reply，进入下一次 Ping
                                }
                                // 非 Ping Reply 数据包丢弃，继续等待
                            }
                        }
                        if (!gotReply) {
                            // 超时
                            timeoutCount++
                            updateSession(key) {
                                it.copy(
                                    pingTestCount = i,
                                    pingTestTimeoutCount = timeoutCount
                                )
                            }
                        }
                    } catch (_: CancellationException) {
                        throw CancellationException() // 重新抛出以支持外层取消
                    }

                    seq++
                    if (i < iterations && isActive) {
                        delay(pingInterval)
                    }
                }

                // 发送 PING_DONE
                withContext(NonCancellable + Dispatchers.IO) {
                    mgr.send(REMOTE_PING_DONE.encodeToByteArray())
                }

                // 显示统计结果
                val finalSession = _uiState.value.sessions[key]
                val sc = finalSession?.pingTestSuccessCount ?: successCount
                val tc = finalSession?.pingTestTimeoutCount ?: timeoutCount
                val total = sc + tc
                val avgStr = if (sc > 0) String.format(Locale.US, "%.1f", finalSession?.pingTestAvgRtt ?: 0.0) else "-"
                val maxStr = if (sc > 0) String.format(Locale.US, "%.1f", finalSession?.pingTestMaxRtt ?: 0.0) else "-"
                val minStr = if (sc > 0 && (finalSession?.pingTestMinRtt ?: Double.MAX_VALUE) < Double.MAX_VALUE)
                    String.format(Locale.US, "%.1f", finalSession?.pingTestMinRtt ?: 0.0) else "-"
                appendChat(
                    key, SppChatDirection.System,
                    "Ping 完成：$total 次, 成功 $sc, 超时 $tc, RTT avg=$avgStr ms, max=$maxStr ms, min=$minStr ms"
                )
            } catch (_: CancellationException) {
                // 发送 PING_DONE（取消时也尝试通知对端）
                runCatching {
                    withContext(NonCancellable + Dispatchers.IO) {
                        mgr.send(REMOTE_PING_DONE.encodeToByteArray())
                    }
                }
                appendChat(key, SppChatDirection.System, "Ping 测试已停止")
            } catch (e: Exception) {
                updateSession(key) { it.copy(lastError = e.message ?: "Ping 异常") }
                appendChat(key, SppChatDirection.System, "Ping 异常：${e.message}")
            } finally {
                updateSession(key) { it.copy(pingTestRunning = false) }
                runtime.pingTestJob = null
                clearActiveTest(key)
                // 切回普通接收模式
                val latest = _uiState.value.sessions[key]
                if (latest?.connectionState == SppConnectionState.Connected && runtime.manager != null) {
                    startReceiveLoop(key)
                }
            }
        }
    }

    fun stopPingTest(key: String) {
        val runtime = runtimes[key] ?: return
        runtime.pingTestJob?.cancel()
        runtime.pingTestJob = null
        clearActiveTest(key)
        updateSession(key) { it.copy(pingTestRunning = false) }
    }

    /**
     * 从 Ping payload 中提取 8 字节大端序 nanoTime 时间戳。
     * payload 布局: [type 1B][timestamp 8B][padding...]
     */
    private fun extractTimestamp(payload: ByteArray): Long {
        if (payload.size < PingEchoHandler.MIN_PAYLOAD_SIZE) return 0L
        var ts = 0L
        for (i in 0 until 8) {
            ts = (ts shl 8) or (payload[PingEchoHandler.TIMESTAMP_OFFSET + i].toLong() and 0xFF)
        }
        return ts
    }

    /**
     * 等待对端回复指定的 ACK 字符串。
     * 阻塞读取直到收到 ACK 或超时。
     * 必须在 Dispatchers.IO 上调用。
     */
    private suspend fun waitForAck(mgr: SendRecvBluetoothProfileManager, expectedAck: String, timeoutMs: Long): Boolean {
        val ackBytes = expectedAck.encodeToByteArray()
        val buf = StringBuilder()
        return try {
            withTimeout(timeoutMs) {
                while (isActive) {
                    val data = mgr.receive(256) ?: return@withTimeout false
                    val text = runCatching { data.toString(Charsets.UTF_8) }.getOrNull() ?: continue
                    buf.append(text)
                    if (buf.contains(expectedAck)) return@withTimeout true
                    if (buf.length > 1024) buf.delete(0, buf.length - 256)
                }
                false
            }
        } catch (_: TimeoutCancellationException) {
            false
        }
    }

    private fun stopReceiveLoop(key: String) {
        val runtime = runtimes[key] ?: return
        runtime.receiveJob?.cancel()
        runtime.receiveJob = null
    }

    /** 检查指定会话是否可以启动新测试 */
    private fun canStartTest(key: String): Boolean {
        val session = _uiState.value.sessions[key] ?: return false
        return session.activeTestType == null &&
                session.connectionState == SppConnectionState.Connected
    }

    /** 设置当前运行的测试类型 */
    private fun setActiveTest(key: String, type: ActiveTestType) {
        updateSession(key) { it.copy(activeTestType = type) }
    }

    /** 清除当前运行的测试类型 */
    private fun clearActiveTest(key: String) {
        updateSession(key) { it.copy(activeTestType = null) }
    }

    private fun updateSession(key: String, block: (SppSession) -> SppSession) {
        _uiState.update { state ->
            val session = state.sessions[key] ?: return@update state
            state.copy(sessions = state.sessions + (key to block(session)))
        }
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

    // ═══ 打流中断连与自动重连 ═══

    /**
     * 打流过程中手动触发断连。
     * 1. 获取当前 activeTestType
     * 2. 立即停止当前发送/测速任务
     * 3. 记录断连时刻数据到 chat 系统消息
     * 4. 断开蓝牙连接
     * 5. 如果 autoReconnectEnabled，调用 startAutoReconnect(key)
     */
    fun disconnectDuringTest(key: String) {
        val session = _uiState.value.sessions[key] ?: return
        val runtime = runtimes[key] ?: return
        val testType = session.activeTestType

        // 立即停止当前测试 Job
        when (testType) {
            ActiveTestType.SpeedTest -> {
                runtime.speedTestJob?.cancel()
                runtime.speedTestJob = null
                updateSession(key) { it.copy(speedTestRunning = false) }
            }
            ActiveTestType.PeriodicSend -> {
                runtime.periodicTestJob?.cancel()
                runtime.periodicTestJob = null
                updateSession(key) { it.copy(periodicTestRunning = false) }
            }
            ActiveTestType.ConnectionCycle -> {
                runtime.connectionCycleJob?.cancel()
                runtime.connectionCycleJob = null
                updateSession(key) { it.copy(connectionCycleRunning = false) }
            }
            ActiveTestType.PingRtt -> {
                runtime.pingTestJob?.cancel()
                runtime.pingTestJob = null
                updateSession(key) { it.copy(pingTestRunning = false) }
            }
            ActiveTestType.PassiveReceive -> {
                // 被动模式可能运行在 receiveJob 或 speedTestJob 中
                runtime.receiveJob?.cancel()
                runtime.receiveJob = null
                runtime.speedTestJob?.cancel()
                runtime.speedTestJob = null
                updateSession(key) { it.copy(speedTestRunning = false) }
            }
            null -> { /* 无测试运行 */ }
        }

        // 记录断连时刻数据到 chat 系统消息
        val updatedSession = _uiState.value.sessions[key]
        val statsMsg = buildString {
            append("打流中断连")
            if (testType != null) {
                append("（${testType.name}）")
            }
            append("：")
            when (testType) {
                ActiveTestType.SpeedTest -> {
                    val txBytes = updatedSession?.speedTestTxTotalBytes ?: 0
                    val rxBytes = updatedSession?.speedTestRxTotalBytes ?: 0
                    val elapsedMs = updatedSession?.speedTestElapsedMs ?: 0
                    append("TX ${txBytes}B, RX ${rxBytes}B, 耗时 ${elapsedMs}ms")
                }
                ActiveTestType.PeriodicSend -> {
                    val sentCount = updatedSession?.periodicTestSentCount ?: 0
                    val sentBytes = updatedSession?.periodicTestSentBytes ?: 0
                    val elapsedMs = updatedSession?.periodicTestElapsedMs ?: 0
                    append("已发送 ${sentCount}包/${sentBytes}B, 耗时 ${elapsedMs}ms")
                }
                else -> append("已断开")
            }
        }
        // 直接写入 chat，绕过 muteConsoleDuringTest 检查
        val item = SppChatItem(
            id = chatId.incrementAndGet(),
            direction = SppChatDirection.System,
            text = statsMsg
        )
        updateSession(key) { state ->
            state.copy(chat = (listOf(item) + state.chat).take(500))
        }

        // 断开蓝牙连接
        disconnect(key)

        // 如果 autoReconnectEnabled，启动自动重连
        val latestSession = _uiState.value.sessions[key]
        if (latestSession?.autoReconnectEnabled == true &&
            latestSession.device.role == SppRole.Client
        ) {
            startAutoReconnect(key)
        }
    }

    /**
     * 自动重连逻辑。
     * 按 autoReconnectInterval 间隔尝试重连，最多 autoReconnectMaxRetries 次。
     * 重连成功：恢复到 Connected 空闲状态，启动接收循环，不自动继续打流。
     * 全部失败：显示"重连失败"，停止重连。
     * 仅客户端模式支持自动重连。
     */
    @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
    fun startAutoReconnect(key: String) {
        val session = _uiState.value.sessions[key] ?: return
        if (!session.autoReconnectEnabled) return
        if (session.device.role != SppRole.Client) return
        val runtime = runtimes[key] ?: return
        // Cancel any existing reconnect attempt to prevent concurrent races
        runtime.reconnectJob?.cancel()
        clearActiveTest(key)

        runtime.reconnectJob = viewModelScope.launch(Dispatchers.IO) {
            var lastFailureReason: String? = null
            for (attempt in 1..session.autoReconnectMaxRetries) {
                delay(session.autoReconnectInterval)

                // 直接写入 chat，绕过 muteConsoleDuringTest
                val attemptItem = SppChatItem(
                    id = chatId.incrementAndGet(),
                    direction = SppChatDirection.System,
                    text = "自动重连尝试 $attempt/${session.autoReconnectMaxRetries}"
                )
                updateSession(key) { state ->
                    state.copy(chat = (listOf(attemptItem) + state.chat).take(500))
                }

                try {
                    val currentSession = _uiState.value.sessions[key] ?: return@launch
                    val uuid = if (!localSocketMode) {
                        runCatching { UUID.fromString(currentSession.device.uuid) }
                            .getOrNull() ?: run {
                            lastFailureReason = "UUID 格式错误"
                            continue
                        }
                    } else null
                    val dev = if (!localSocketMode) {
                        adapter?.getRemoteDevice(currentSession.device.address) ?: run {
                            lastFailureReason = "无效的地址"
                            continue
                        }
                    } else null

                    val secure = currentSession.securityMode == SecurityMode.Secure
                    val mgr = if (localSocketMode) {
                        LocalSocketClientManager(
                            getApplication<Application>().applicationContext,
                            currentSession.device.uuid
                        )
                    } else {
                        SppClientManager(
                            getApplication<Application>().applicationContext, dev!!, uuid!!, secure
                        )
                    }
                    val runtime = runtimes.computeIfAbsent(key) { SessionRuntime() }
                    runtime.client = mgr
                    runtime.server = null

                    updateSession(key) { it.copy(connectionState = SppConnectionState.Connecting) }
                    mgr.connect()

                    // 验证连接是否真正成功（connect() 可能不抛异常但连接失败）
                    val connState = (mgr as? SocketLikeBluetoothClientManager)?.connectionState?.value
                    if (connState == SocketLikeBluetoothClientManager.ConnectionState.Error ||
                        connState == SocketLikeBluetoothClientManager.ConnectionState.Idle) {
                        lastFailureReason = "连接未建立"
                        val failItem = SppChatItem(
                            id = chatId.incrementAndGet(),
                            direction = SppChatDirection.System,
                            text = "重连失败：连接未建立"
                        )
                        updateSession(key) { state ->
                            state.copy(chat = (listOf(failItem) + state.chat).take(500))
                        }
                        continue
                    }

                    // 重连成功
                    updateSession(key) { it.copy(connectionState = SppConnectionState.Connected) }
                    when (mgr) {
                        is LocalSocketClientManager -> observeLocalSocketClientState(key, mgr)
                        is SppClientManager -> observeConnectionState(key, mgr)
                        else -> {}
                    }
                    startReceiveLoop(key)

                    val successItem = SppChatItem(
                        id = chatId.incrementAndGet(),
                        direction = SppChatDirection.System,
                        text = "重连成功"
                    )
                    updateSession(key) { state ->
                        state.copy(chat = (listOf(successItem) + state.chat).take(500))
                    }
                    return@launch
                } catch (e: Exception) {
                    lastFailureReason = e.message ?: e::class.java.simpleName
                    val failItem = SppChatItem(
                        id = chatId.incrementAndGet(),
                        direction = SppChatDirection.System,
                        text = "重连失败：${e.message}"
                    )
                    updateSession(key) { state ->
                        state.copy(chat = (listOf(failItem) + state.chat).take(500))
                    }
                }
            }

            // 所有重试均失败
            val finalItem = SppChatItem(
                id = chatId.incrementAndGet(),
                direction = SppChatDirection.System,
                text = "重连失败：已达最大重试次数"
            )
            updateSession(key) { state ->
                state.copy(
                    chat = (listOf(finalItem) + state.chat).take(500),
                    lastError = "重连失败：$lastFailureReason"
                )
            }
        }
    }

    fun updateAutoReconnectEnabled(enabled: Boolean) {
        val key = _uiState.value.selectedKey ?: return
        updateSession(key) { it.copy(autoReconnectEnabled = enabled) }
    }

    fun updateAutoReconnectInterval(interval: Long) {
        val key = _uiState.value.selectedKey ?: return
        updateSession(key) { it.copy(autoReconnectInterval = interval.coerceAtLeast(500)) }
    }

    // ── 测速配置更新 ──

    fun updateSpeedTestWithCrc(enabled: Boolean) {
        val key = _uiState.value.selectedKey ?: return
        updateSession(key) { it.copy(speedTestWithCrc = enabled) }
    }

    fun updateSpeedTestTargetBytes(bytes: Long) {
        val key = _uiState.value.selectedKey ?: return
        updateSession(key) { it.copy(speedTestTargetBytes = bytes.coerceAtLeast(1024)) }
    }

    fun updateSendPayloadSize(size: Int) {
        val key = _uiState.value.selectedKey ?: return
        updateSession(key) { it.copy(sendPayloadSize = size.coerceIn(1, 32768)) }
    }

    // ── 定时发送配置更新 ──

    fun updatePeriodicTestInterval(interval: Long) {
        val key = _uiState.value.selectedKey ?: return
        updateSession(key) { it.copy(periodicTestInterval = interval.coerceAtLeast(0)) }
    }

    fun updatePeriodicTestStopCondition(condition: PeriodicStopCondition) {
        val key = _uiState.value.selectedKey ?: return
        updateSession(key) { it.copy(periodicTestStopCondition = condition) }
    }

    fun updatePeriodicTestSendPayloadSize(size: Int) {
        val key = _uiState.value.selectedKey ?: return
        updateSession(key) { it.copy(periodicTestSendPayloadSize = size.coerceIn(1, 32768)) }
    }

    // ── 连接循环压测配置更新 ──

    fun updateConnectionCycleTargetCount(count: Int) {
        val key = _uiState.value.selectedKey ?: return
        updateSession(key) { it.copy(connectionCycleTargetCount = count.coerceAtLeast(0)) }
    }

    fun updateConnectionCycleInterval(interval: Long) {
        val key = _uiState.value.selectedKey ?: return
        updateSession(key) { it.copy(connectionCycleInterval = interval.coerceAtLeast(100)) }
    }

    fun updateConnectionCycleTimeout(timeout: Long) {
        val key = _uiState.value.selectedKey ?: return
        updateSession(key) { it.copy(connectionCycleTimeout = timeout.coerceAtLeast(1000)) }
    }

    // ── Ping RTT 配置更新 ──

    fun updatePingTestTargetCount(count: Int) {
        val key = _uiState.value.selectedKey ?: return
        updateSession(key) { it.copy(pingTestTargetCount = count.coerceAtLeast(0)) }
    }

    fun updatePingTestInterval(interval: Long) {
        val key = _uiState.value.selectedKey ?: return
        updateSession(key) { it.copy(pingTestInterval = interval.coerceAtLeast(0)) }
    }

    fun updatePingTestTimeout(timeout: Long) {
        val key = _uiState.value.selectedKey ?: return
        updateSession(key) { it.copy(pingTestTimeout = timeout.coerceAtLeast(500)) }
    }

    fun updatePingTestPaddingSize(size: Int) {
        val key = _uiState.value.selectedKey ?: return
        updateSession(key) { it.copy(pingTestPaddingSize = size.coerceAtLeast(0)) }
    }

    // ── 便捷方法（无参版本，自动取 selectedKey）──

    fun startPeriodicTestSelected() {
        val key = _uiState.value.selectedKey ?: return
        startPeriodicTest(key)
    }

    fun stopPeriodicTestSelected() {
        val key = _uiState.value.selectedKey ?: return
        stopPeriodicTest(key)
    }

    fun startConnectionCycleTestSelected() {
        val key = _uiState.value.selectedKey ?: return
        startConnectionCycleTest(key)
    }

    fun stopConnectionCycleTestSelected() {
        val key = _uiState.value.selectedKey ?: return
        stopConnectionCycleTest(key)
    }

    fun startPingTestSelected() {
        val key = _uiState.value.selectedKey ?: return
        startPingTest(key)
    }

    fun stopPingTestSelected() {
        val key = _uiState.value.selectedKey ?: return
        stopPingTest(key)
    }

    fun disconnectDuringTestSelected() {
        val key = _uiState.value.selectedKey ?: return
        disconnectDuringTest(key)
    }

    fun startPacketSpeedTestSelected() {
        val key = _uiState.value.selectedKey ?: return
        startPacketSpeedTest(key)
    }

    // ═══════════════════════════════════════════════════════════════════
    // ═══ Server Tab 管理方法 (Task 3.1) ═══
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 创建新的 Server Tab：生成 tabId、创建 ServerTabConfig、追加到 uiState.serverTabs、
     * 持久化到 ServerTabStore、创建对应 SppSession（key = "server:{tabId}"）。
     * 不自动开始监听，用户需手动点击 Listen。
     */
    fun addServerTab(uuid: String, name: String, securityMode: SecurityMode = SecurityMode.Secure): String {
        val tabId = java.util.UUID.randomUUID().toString()
        val config = ServerTabConfig(
            tabId = tabId,
            uuid = uuid,
            name = name,
            createdAt = System.currentTimeMillis(),
            securityMode = securityMode
        )
        val key = "server:$tabId"

        _uiState.update { state ->
            val newTabs = state.serverTabs + config
            val session = SppSession(
                device = SppDevice(name = name, uuid = uuid, role = SppRole.Server),
                speedTestMode = defaultSpeedTestMode(SppRole.Server)
            )
            state.copy(
                serverTabs = newTabs,
                sessions = state.sessions + (key to session),
                selectedKey = key
            )
        }

        // 持久化
        viewModelScope.launch {
            ServerTabStore.save(getApplication(), _uiState.value.serverTabs)
        }

        return tabId
    }

    /**
     * 删除 Server Tab：停止监听、断开客户端、清理 runtimes、从 uiState 中移除、
     * 持久化更新、清理历史会话磁盘数据。
     */
    fun removeServerTab(tabId: String) {
        val key = "server:$tabId"

        // 完全断开（含已连接的客户端）并清理运行时
        disconnect(key)
        runtimes.remove(key)

        _uiState.update { state ->
            state.copy(
                serverTabs = state.serverTabs.filter { it.tabId != tabId },
                sessions = state.sessions - key,
                selectedKey = if (state.selectedKey == key) null else state.selectedKey
            )
        }

        // 持久化
        viewModelScope.launch {
            ServerTabStore.save(getApplication(), _uiState.value.serverTabs)
            ServerSessionStore.delete(getApplication(), tabId)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ═══ Server Tab 监听控制方法 (Task 3.2) ═══
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 为指定 Server Tab 开始监听。
     * 通过 "server:{tabId}" 定位 session，根据 localSocketMode 创建对应 Manager，
     * 调用 register()，观察连接状态。
     */
    fun startListeningForTab(tabId: String) {
        val key = "server:$tabId"
        val session = _uiState.value.sessions[key] ?: return
        if (session.serverListenerState == ServerListenerState.Listening) return

        // 从 Tab 配置中读取 securityMode
        val tabConfig = _uiState.value.serverTabs.firstOrNull { it.tabId == tabId }
        val secure = tabConfig?.securityMode != SecurityMode.Insecure

        try {
            val sppUuid = runCatching { UUID.fromString(session.device.uuid) }.getOrElse {
                updateSession(key) { it.copy(lastError = "UUID 格式错误") }
                return
            }

            updateSession(key) { it.copy(lastError = null) }
            val runtime = runtimes.computeIfAbsent(key) { SessionRuntime() }

            // 关闭旧的 server manager（如果存在），避免 LocalSocket 地址冲突或蓝牙资源泄漏
            val oldServer = runtime.server
            if (oldServer != null) {
                when (oldServer) {
                    is SocketLikeBluetoothServerManager -> oldServer.stopServer()
                    is LocalSocketServerManager -> oldServer.stopServer()
                    else -> oldServer.disconnect()
                }
                runtime.server = null
                runtime.connectionJob?.cancel()
                runtime.connectionJob = null
            }

            if (localSocketMode) {
                val mgr = LocalSocketServerManager(
                    getApplication<Application>().applicationContext,
                    session.device.uuid
                )
                runtime.server = mgr
                runtime.client = null
                updateSession(key) { it.copy(serverListenerState = ServerListenerState.Listening) }
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val ok = mgr.register()
                        if (ok) {
                            // register() 成功后再开始观察状态，避免初始 Idle 覆盖 Listening
                            withContext(Dispatchers.Main) {
                                handleServerListenerStateChange(key, ServerListenerState.Listening)
                                observeLocalSocketServerState(key, mgr)
                            }
                        } else {
                            updateSession(key) {
                                it.copy(
                                    serverListenerState = ServerListenerState.Error,
                                    lastError = "LocalSocket 注册监听失败"
                                )
                            }
                        }
                    } catch (t: Throwable) {
                        updateSession(key) {
                            it.copy(
                                serverListenerState = ServerListenerState.Error,
                                lastError = t.message
                            )
                        }
                    }
                }
            } else {
                val mgr = SppServerManager(getApplication<Application>().applicationContext, sppUuid, secure = secure)
                runtime.server = mgr
                runtime.client = null
                updateSession(key) { it.copy(serverListenerState = ServerListenerState.Listening) }
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val ok = mgr.register()
                        if (ok) {
                            // register() 成功后再开始观察状态，避免初始 Idle 覆盖 Listening
                            withContext(Dispatchers.Main) {
                                handleServerListenerStateChange(key, ServerListenerState.Listening)
                                observeConnectionState(key, mgr)
                            }
                        } else {
                            updateSession(key) {
                                it.copy(
                                    serverListenerState = ServerListenerState.Error,
                                    lastError = "注册监听失败"
                                )
                            }
                        }
                    } catch (t: Throwable) {
                        updateSession(key) {
                            it.copy(
                                serverListenerState = ServerListenerState.Error,
                                lastError = t.message
                            )
                        }
                    }
                }
            }
        } catch (_: SecurityException) {
            updateSession(key) {
                it.copy(
                    serverListenerState = ServerListenerState.Error,
                    lastError = "缺少蓝牙权限（请授权蓝牙连接/扫描）"
                )
            }
        }
    }

    /**
     * 为指定 Server Tab 停止监听。
     * 停止所有运行中的测试、关闭 Server、清理资源。
     */
    fun stopListeningForTab(tabId: String) {
        val key = "server:$tabId"
        val runtime = runtimes[key] ?: return

        // 停止所有运行中的测试
        stopSpeedTest(key)
        runtime.connectionCycleJob?.cancel(); runtime.connectionCycleJob = null
        runtime.pingTestJob?.cancel(); runtime.pingTestJob = null
        runtime.periodicTestJob?.cancel(); runtime.periodicTestJob = null

        // 仅停止监听（关闭 serverSocket），不断开已建立的客户端连接
        val server = runtime.server
        when (server) {
            is SocketLikeBluetoothServerManager -> server.stopListening()
            is LocalSocketServerManager -> server.stopListening()
            else -> server?.disconnect()
        }

        updateSession(key) {
            it.copy(
                serverListenerState = ServerListenerState.Idle,
                activeTestType = null
            )
        }
    }

    /**
     * 为指定 Server Tab 仅断开当前客户端，保持监听。
     * 底层会自动重新 accept。
     */
    fun disconnectServerClientForTab(tabId: String) {
        val key = "server:$tabId"
        val runtime = runtimes[key] ?: return

        // 停止运行中的测试
        stopSpeedTest(key)
        runtime.pingTestJob?.cancel(); runtime.pingTestJob = null
        runtime.periodicTestJob?.cancel(); runtime.periodicTestJob = null

        // 兼容 SocketLikeBluetoothServerManager 和 LocalSocketServerManager
        val server = runtime.server
        when (server) {
            is SocketLikeBluetoothServerManager -> server.disconnectClient()
            is LocalSocketServerManager -> server.disconnectClient()
        }
        stopReceiveLoop(key)
    }

    // ═══════════════════════════════════════════════════════════════════
    // ═══ Server Tab 操作 ForTab 版本 (Task 3.2) ═══
    // ═══ 每个方法通过 "server:$tabId" 直接构造 key，不依赖 selectedKey ═══
    // ═══════════════════════════════════════════════════════════════════

    fun updateSendingTextForTab(tabId: String, text: String) {
        val key = "server:$tabId"
        updateSession(key) { it.copy(sendingText = text) }
    }

    fun clearChatForTab(tabId: String) {
        val key = "server:$tabId"
        updateSession(key) { it.copy(chat = emptyList(), lastError = null) }
    }

    fun sendOnceForTab(tabId: String) {
        val key = "server:$tabId"
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

    fun updateParseIncomingAsTextForTab(tabId: String, enabled: Boolean) {
        val key = "server:$tabId"
        updateSession(key) { it.copy(parseIncomingAsText = enabled) }
    }

    fun updatePayloadSizeForTab(tabId: String, size: Int) {
        val key = "server:$tabId"
        updateSession(key) { it.copy(receiveBufferSize = size.coerceAtLeast(1)) }
    }

    fun setMuteConsoleDuringTestForTab(tabId: String, enabled: Boolean) {
        val key = "server:$tabId"
        updateSession(key) { it.copy(muteConsoleDuringTest = enabled) }
    }

    fun setSpeedTestWindowOpenForTab(tabId: String, open: Boolean) {
        val key = "server:$tabId"
        updateSession(key) { it.copy(speedTestWindowOpen = open) }
    }

    fun updateSpeedTestPayloadForTab(tabId: String, payload: String) {
        val key = "server:$tabId"
        updateSession(key) { it.copy(speedTestPayload = payload) }
    }

    fun toggleSpeedTestModeForTab(tabId: String) {
        val key = "server:$tabId"
        val session = _uiState.value.sessions[key] ?: return
        if (session.speedTestRunning) return
        val next = when (session.speedTestMode) {
            SppSpeedTestMode.TxOnly -> SppSpeedTestMode.RxOnly
            SppSpeedTestMode.RxOnly -> SppSpeedTestMode.TxOnly
            SppSpeedTestMode.Duplex -> SppSpeedTestMode.TxOnly
        }
        updateSession(key) { it.copy(speedTestMode = next) }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun toggleSpeedTestForTab(tabId: String) {
        val key = "server:$tabId"
        val session = _uiState.value.sessions[key] ?: run {
            return
        }
        if (session.speedTestRunning) {
            stopSpeedTest(key)
        } else {
            // 统一使用 Test_Packet 格式测速（含 START/EOF 信令协议）
            startPacketSpeedTest(key)
        }
    }

    fun startPeriodicTestForTab(tabId: String) {
        val key = "server:$tabId"
        startPeriodicTest(key)
    }

    fun stopPeriodicTestForTab(tabId: String) {
        val key = "server:$tabId"
        stopPeriodicTest(key)
    }

    fun startPingTestForTab(tabId: String) {
        val key = "server:$tabId"
        startPingTest(key)
    }

    fun stopPingTestForTab(tabId: String) {
        val key = "server:$tabId"
        stopPingTest(key)
    }

    fun disconnectDuringTestForTab(tabId: String) {
        val key = "server:$tabId"
        disconnectDuringTest(key)
    }

    // ── 测速配置更新 ForTab 版本 ──

    fun updateSpeedTestWithCrcForTab(tabId: String, enabled: Boolean) {
        val key = "server:$tabId"
        updateSession(key) { it.copy(speedTestWithCrc = enabled) }
    }

    fun updateSpeedTestTargetBytesForTab(tabId: String, bytes: Long) {
        val key = "server:$tabId"
        updateSession(key) { it.copy(speedTestTargetBytes = bytes.coerceAtLeast(1024)) }
    }

    fun updateSendPayloadSizeForTab(tabId: String, size: Int) {
        val key = "server:$tabId"
        updateSession(key) { it.copy(sendPayloadSize = size.coerceIn(1, 32768)) }
    }

    // ── 定时发送配置更新 ForTab 版本 ──

    fun updatePeriodicTestIntervalForTab(tabId: String, interval: Long) {
        val key = "server:$tabId"
        updateSession(key) { it.copy(periodicTestInterval = interval.coerceAtLeast(0)) }
    }

    fun updatePeriodicTestStopConditionForTab(tabId: String, condition: PeriodicStopCondition) {
        val key = "server:$tabId"
        updateSession(key) { it.copy(periodicTestStopCondition = condition) }
    }

    fun updatePeriodicTestSendPayloadSizeForTab(tabId: String, size: Int) {
        val key = "server:$tabId"
        updateSession(key) { it.copy(periodicTestSendPayloadSize = size.coerceIn(1, 32768)) }
    }

    // ── Ping RTT 配置更新 ForTab 版本 ──

    fun updatePingTestTargetCountForTab(tabId: String, count: Int) {
        val key = "server:$tabId"
        updateSession(key) { it.copy(pingTestTargetCount = count.coerceAtLeast(0)) }
    }

    fun updatePingTestIntervalForTab(tabId: String, interval: Long) {
        val key = "server:$tabId"
        updateSession(key) { it.copy(pingTestInterval = interval.coerceAtLeast(0)) }
    }

    fun updatePingTestTimeoutForTab(tabId: String, timeout: Long) {
        val key = "server:$tabId"
        updateSession(key) { it.copy(pingTestTimeout = timeout.coerceAtLeast(500)) }
    }

    fun updatePingTestPaddingSizeForTab(tabId: String, size: Int) {
        val key = "server:$tabId"
        updateSession(key) { it.copy(pingTestPaddingSize = size.coerceAtLeast(0)) }
    }

    // ── 自动重连配置 ForTab 版本 ──

    fun updateAutoReconnectEnabledForTab(tabId: String, enabled: Boolean) {
        val key = "server:$tabId"
        updateSession(key) { it.copy(autoReconnectEnabled = enabled) }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ═══ selectServerTab (Task 3.3) ═══
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 切换到指定 Server Tab。
     * 设置 selectedKey，不自动开始监听（用户需手动点击 Listen）。
     */
    fun selectServerTab(tabId: String) {
        val key = "server:$tabId"
        _uiState.update { it.copy(selectedKey = key) }
    }

    /**
     * 更新指定 Server Tab 的 SecurityMode 并持久化。
     */
    fun updateServerTabSecurityMode(tabId: String, mode: SecurityMode) {
        _uiState.update { state ->
            val updatedTabs = state.serverTabs.map { tab ->
                if (tab.tabId == tabId) tab.copy(securityMode = mode) else tab
            }
            state.copy(serverTabs = updatedTabs)
        }
        viewModelScope.launch {
            ServerTabStore.save(getApplication(), _uiState.value.serverTabs)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ═══ Client 会话快照逻辑 ═══
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Client 会话断开时保存快照到历史。
     * 同时更新 _uiState.clientSessionHistory（内存态）和 ClientSessionStore.addSnapshot()（持久态）。
     */
    private fun saveClientSessionSnapshot(key: String, session: SppSession) {
        val sessionId = key.removePrefix("client:")

        // Prevent duplicate snapshots for the same session
        if (_uiState.value.clientSessionHistory.any { it.sessionId == sessionId }) return

        val snapshot = ClientSessionSnapshot(
            sessionId = sessionId,
            remoteDeviceAddress = session.device.address,
            remoteDeviceName = session.device.name.takeIf { it.isNotBlank() && it != session.device.address },
            uuid = session.device.uuid,
            securityMode = session.securityMode,
            disconnectedAt = System.currentTimeMillis(),
            chat = session.chat,
            speedTestTxAvgBps = session.speedTestTxAvgBps,
            speedTestRxAvgBps = session.speedTestRxAvgBps
        )

        // Update memory state
        _uiState.update { state ->
            val updated = (listOf(snapshot) + state.clientSessionHistory)
                .sortedByDescending { it.disconnectedAt }
                .take(20)
            state.copy(clientSessionHistory = updated)
        }

        // Persist
        viewModelScope.launch {
            ClientSessionStore.addSnapshot(getApplication(), snapshot)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ═══ Server 会话轮转逻辑 (Task 3.5) ═══
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 处理 Server Tab 的连接状态变化时的会话轮转。
     * 当 connectionState 变为 Connected 且当前 chat 非空时，快照当前会话并创建新空会话。
     */
    private fun handleServerSessionRotation(key: String) {
        val session = _uiState.value.sessions[key] ?: return
        // 只有当 chat 中包含用户数据消息（In/Out）时才触发轮转，纯系统消息不产生历史快照
        val hasUserMessages = session.chat.any { it.direction != SppChatDirection.System }
        if (!hasUserMessages) {
            // 无用户数据，仅清空系统消息重新开始
            updateSession(key) { it.copy(chat = emptyList()) }
            return
        }

        // 快照当前会话
        val snapshot = ServerSessionSnapshot(
            remoteDeviceAddress = session.remoteDeviceAddress ?: "未知设备",
            remoteDeviceName = session.device.name.takeIf { it.isNotBlank() },
            disconnectedAt = System.currentTimeMillis(),
            chat = session.chat,
            speedTestTxAvgBps = session.speedTestTxAvgBps,
            speedTestRxAvgBps = session.speedTestRxAvgBps,
            speedTestSamples = session.speedTestSamples
        )

        // 追加到 sessionHistory 头部，裁剪至上限（最多 6 条历史 + 1 活跃 = 7）
        updateSession(key) { s ->
            val newHistory = (listOf(snapshot) + s.sessionHistory).take(6)
            s.copy(
                sessionHistory = newHistory,
                chat = emptyList(),
                speedTestRunning = false,
                speedTestElapsedMs = 0,
                speedTestTxTotalBytes = 0,
                speedTestRxTotalBytes = 0,
                speedTestTxInstantBps = null,
                speedTestRxInstantBps = null,
                speedTestTxAvgBps = null,
                speedTestRxAvgBps = null,
                speedTestTxWriteAvgMs = null,
                speedTestTxWriteMaxMs = null,
                speedTestRxReadAvgMs = null,
                speedTestRxReadMaxMs = null,
                speedTestRxReadAvgBytes = null,
                speedTestTxFirstWriteDelayMs = null,
                speedTestRxFirstByteDelayMs = null,
                speedTestSamples = emptyList(),
                lastError = null
            )
        }

        // 持久化：从 key 中提取 tabId
        val tabId = key.removePrefix("server:")
        viewModelScope.launch {
            val updatedSession = _uiState.value.sessions[key]
            if (updatedSession != null) {
                ServerSessionStore.save(getApplication(), tabId, updatedSession.sessionHistory)
            }
        }
    }

    /**
     * 清空指定 Server Tab 的所有历史会话。
     */
    fun clearServerSessionHistory(tabId: String) {
        val key = "server:$tabId"
        updateSession(key) { it.copy(sessionHistory = emptyList()) }
        viewModelScope.launch {
            ServerSessionStore.save(getApplication(), tabId, emptyList())
        }
    }

    /**
     * 删除指定 Server Tab 的单条历史会话。
     */
    fun deleteServerSession(tabId: String, sessionIndex: Int) {
        val key = "server:$tabId"
        val session = _uiState.value.sessions[key] ?: return
        if (sessionIndex < 0 || sessionIndex >= session.sessionHistory.size) return

        val newHistory = session.sessionHistory.toMutableList().apply { removeAt(sessionIndex) }
        updateSession(key) { it.copy(sessionHistory = newHistory) }
        viewModelScope.launch {
            ServerSessionStore.save(getApplication(), tabId, newHistory)
        }
    }

    /** 蓝牙适配器关闭时的清理逻辑 */
    private fun onBluetoothOff() {
        runtimes.forEach { (key, runtime) ->
            // Cancel all running test Jobs
            runtime.speedTestJob?.cancel()
            runtime.speedTestJob = null
            runtime.connectionCycleJob?.cancel()
            runtime.connectionCycleJob = null
            runtime.pingTestJob?.cancel()
            runtime.pingTestJob = null
            runtime.periodicTestJob?.cancel()
            runtime.periodicTestJob = null
            runtime.reconnectJob?.cancel()
            runtime.reconnectJob = null

            // Disconnect all active runtimes
            val server = runtime.server
            when (server) {
                is SocketLikeBluetoothServerManager -> server.stopServer()
                is LocalSocketServerManager -> server.stopServer()
                else -> server?.disconnect()
            }
            runtime.client?.disconnect()
            stopReceiveLoop(key)
            runtime.connectionJob?.cancel()
            runtime.connectionJob = null
            runtime.client = null
            runtime.server = null
            runtime.lastConnState = null
            runtime.controlBuffer.clear()

            // Update UI state for this session
            updateSession(key) {
                it.copy(
                    activeTestType = null,
                    connectionState = SppConnectionState.Closed,
                    serverListenerState = ServerListenerState.Idle,
                    speedTestRunning = false,
                    connectionCycleRunning = false,
                    pingTestRunning = false,
                    periodicTestRunning = false
                )
            }

            // Append system message
            // Use chatId directly to bypass muteConsoleDuringTest check
            val item = SppChatItem(
                id = chatId.incrementAndGet(),
                direction = SppChatDirection.System,
                text = "蓝牙已关闭"
            )
            updateSession(key) { state ->
                state.copy(chat = (listOf(item) + state.chat).take(500))
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().unregisterReceiver(bluetoothStateReceiver)
        val keys = runtimes.keys.toList()
        keys.forEach { disconnect(it) }
        runtimes.clear()
    }
}

private fun SppDevice.key(): String = address.ifBlank { uuid }
