package top.expli.bluetoothtester.ui.spp

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import top.expli.bluetoothtester.data.SettingsStore
import top.expli.bluetoothtester.model.DeviceType
import top.expli.bluetoothtester.model.ScanMode
import top.expli.bluetoothtester.model.SecurityMode
import top.expli.bluetoothtester.model.SppConnectionState
import top.expli.bluetoothtester.model.SppUiState
import top.expli.bluetoothtester.model.SppViewModel
import top.expli.bluetoothtester.ui.common.DevicePickerSheet

@SuppressLint("MissingPermission")
@Composable
fun ClientTabPage(
    vm: SppViewModel,
    state: SppUiState,
    snackbarHostState: SnackbarHostState,
    ensureBluetoothPermissions: (() -> Unit) -> Unit,
    onScrollToLatest: (() -> Unit) -> Unit,
    onIsInDetailChanged: (Boolean) -> Unit,
    onBackHandler: ((() -> Unit)?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val latestState by rememberUpdatedState(state)

    // Collect control card state from ViewModel
    val address by vm.clientControlAddress.collectAsState()
    val uuid by vm.clientControlUuid.collectAsState()
    val securityMode by vm.clientControlSecurityMode.collectAsState()

    // Client Tab 维护自己的 selectedClientKey，不依赖全局 selectedKey
    // 避免切换到 Server Tab 时全局 selectedKey 被改为 server key 导致 Client 详情页丢失
    var selectedClientKey by remember { mutableStateOf<String?>(null) }

    // 同步：当全局 selectedKey 是 client key 时，更新本地状态
    LaunchedEffect(state.selectedKey) {
        if (state.selectedKey?.startsWith("client:") == true) {
            selectedClientKey = state.selectedKey
        }
    }

    val selectedSession = selectedClientKey?.let { state.sessions[it] }
    val inDetail = selectedClientKey != null && selectedSession != null

    // Device picker state
    var showDevicePicker by remember { mutableStateOf(false) }
    var pickerForReconnect by remember { mutableStateOf(false) }

    // Persist SecurityMode changes
    LaunchedEffect(securityMode) {
        vm.viewModelScope.launch {
            SettingsStore.updateClientDefaultSecurityMode(context, securityMode)
        }
    }

    // Report detail state changes to parent
    LaunchedEffect(inDetail) {
        onIsInDetailChanged(inDetail)
        onBackHandler(
            if (inDetail) ({
                // 返回列表页，不断连 — 连接保持在后台
                selectedClientKey = null
                vm.clearSelectedKey()
            }) else null
        )
    }

    // Defensive cleanup
    DisposableEffect(Unit) {
        onDispose {
            onBackHandler(null)
            onIsInDetailChanged(false)
        }
    }

    if (inDetail) {
        // 确保 ViewModel 的 selectedKey 指向当前 client session，
        // 这样所有基于 selectedKey 的无参方法（toggleSpeedTest、sendOnce 等）都能正确工作
        LaunchedEffect(selectedClientKey) {
            if (selectedClientKey != null && state.selectedKey != selectedClientKey) {
                vm.selectClientSession(selectedClientKey!!.removePrefix("client:"))
            }
        }

        val isReadOnly = selectedSession.connectionState == SppConnectionState.Closed ||
                selectedSession.connectionState == SppConnectionState.Error

        ClientDetailScreen(
            modifier = modifier.fillMaxSize(),
            sessionState = selectedSession,
            readOnly = isReadOnly,
            onTextChange = { vm.updateSendingText(it) },
            onPayloadChange = { vm.updatePayloadSize(it) },
            onSend = { vm.sendOnce() },
            onToggleSpeedTest = { ensureBluetoothPermissions { vm.toggleSpeedTest() } },
            onToggleSpeedTestMode = { vm.toggleSpeedTestMode() },
            onMuteConsoleDuringTestChange = { vm.setMuteConsoleDuringTest(it) },
            onSpeedTestWindowOpenChange = { vm.setSpeedTestWindowOpen(it) },
            onSpeedTestPayloadChange = { vm.updateSpeedTestPayload(it) },
            onParseIncomingAsTextChange = { vm.updateParseIncomingAsText(it) },
            onScrollToLatest = onScrollToLatest,
            onToggleConnection = {
                val active =
                    selectedClientKey?.let { latestState.sessions[it] }?.connectionState == SppConnectionState.Connected
                if (active) {
                    selectedClientKey?.removePrefix("client:")?.let { vm.disconnectClientSession(it) }
                } else {
                    ensureBluetoothPermissions { vm.connect() }
                }
            },
            onClearChat = { vm.clearChat() },
            onConnectFromBondedDevice = {
                pickerForReconnect = true
                showDevicePicker = true
            },
            onStartPeriodicTest = { vm.startPeriodicTestSelected() },
            onStopPeriodicTest = { vm.stopPeriodicTestSelected() },
            onUpdatePeriodicInterval = { vm.updatePeriodicTestInterval(it) },
            onUpdatePeriodicStopCondition = { vm.updatePeriodicTestStopCondition(it) },
            onUpdatePeriodicSendPayloadSize = { vm.updatePeriodicTestSendPayloadSize(it) },
            onStartConnectionCycleTest = { ensureBluetoothPermissions { vm.startConnectionCycleTestSelected() } },
            onStopConnectionCycleTest = { vm.stopConnectionCycleTestSelected() },
            onUpdateConnectionCycleTargetCount = { vm.updateConnectionCycleTargetCount(it) },
            onUpdateConnectionCycleInterval = { vm.updateConnectionCycleInterval(it) },
            onUpdateConnectionCycleTimeout = { vm.updateConnectionCycleTimeout(it) },
            onStartPingTest = { vm.startPingTestSelected() },
            onStopPingTest = { vm.stopPingTestSelected() },
            onUpdatePingTargetCount = { vm.updatePingTestTargetCount(it) },
            onUpdatePingInterval = { vm.updatePingTestInterval(it) },
            onUpdatePingTimeout = { vm.updatePingTestTimeout(it) },
            onUpdatePingPaddingSize = { vm.updatePingTestPaddingSize(it) },
            onDisconnectDuringTest = { vm.disconnectDuringTestSelected() },
            onAutoReconnectEnabledChange = { vm.updateAutoReconnectEnabled(it) },
            onSpeedTestWithCrcChange = { vm.updateSpeedTestWithCrc(it) },
            onSpeedTestTargetBytesChange = { vm.updateSpeedTestTargetBytes(it) },
            onSendPayloadSizeChange = { vm.updateSendPayloadSize(it) }
        )
    } else {
        // Main view: Control Card + Session List
        Column(modifier = modifier.fillMaxSize()) {
            // Control Card
            ClientControlCard(
                address = address,
                onAddressChange = { vm.clientControlAddress.value = it },
                uuid = uuid,
                onUuidChange = { vm.clientControlUuid.value = it },
                securityMode = securityMode,
                onSecurityModeChange = { vm.clientControlSecurityMode.value = it },
                onConnect = {
                    ensureBluetoothPermissions {
                        val sessionId = vm.createAndConnectClientSession(address, uuid, address, securityMode)
                        selectedClientKey = "client:$sessionId"
                    }
                },
                onDiscoverDevices = {
                    pickerForReconnect = false
                    showDevicePicker = true
                },
                connectEnabled = true,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )

            // Session List
            val activeSessions = state.sessions.entries
                .filter { entry ->
                    entry.key.startsWith("client:") &&
                            (entry.value.connectionState == SppConnectionState.Connected ||
                                    entry.value.connectionState == SppConnectionState.Connecting)
                }
                .sortedByDescending { it.value.connectedAt ?: 0L }

            val historySessions = state.clientSessionHistory

            val hasContent = activeSessions.isNotEmpty() || historySessions.isNotEmpty()

            if (hasContent) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    // Active sessions
                    items(activeSessions, key = { it.key }) { (key, session) ->
                        val sessionId = key.removePrefix("client:")
                        SessionCard(
                            sessionId = sessionId,
                            deviceName = session.device.name,
                            deviceAddress = session.device.address,
                            connectionState = session.connectionState,
                            securityMode = session.securityMode,
                            isClient = true,
                            onClick = {
                                selectedClientKey = key
                                vm.selectClientSession(sessionId)
                            }
                        )
                    }

                    // History sessions
                    items(historySessions, key = { "history:${it.sessionId}" }) { snapshot ->
                        SessionCard(
                            sessionId = snapshot.sessionId,
                            deviceName = snapshot.remoteDeviceName
                                ?: snapshot.remoteDeviceAddress,
                            deviceAddress = snapshot.remoteDeviceAddress,
                            connectionState = SppConnectionState.Closed,
                            securityMode = snapshot.securityMode,
                            isClient = true,
                            disconnectedAt = snapshot.disconnectedAt,
                            speedTestTxAvgBps = snapshot.speedTestTxAvgBps,
                            speedTestRxAvgBps = snapshot.speedTestRxAvgBps,
                            chatCount = snapshot.chat.size,
                            onClick = {
                                selectedClientKey = "client:${snapshot.sessionId}"
                                vm.selectClientHistorySession(snapshot.sessionId)
                            },
                            onDelete = { vm.deleteClientHistorySession(snapshot.sessionId) }
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 64.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        text = "暂无会话，输入地址后点击连接",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Device Picker (bonded + classic scan, filtered to Classic/Dual)
    if (showDevicePicker) {
        DevicePickerSheet(
            deviceTypeFilter = setOf(DeviceType.Classic, DeviceType.Dual),
            defaultScanMode = ScanMode.BrOnly,
            ensureBluetoothPermissions = ensureBluetoothPermissions,
            onDismissRequest = { showDevicePicker = false },
            onSelect = { addr, pickedName, _ ->
                if (pickerForReconnect) {
                    val selected =
                        selectedClientKey?.let { latestState.sessions[it] }?.device
                    if (selected != null) {
                        val resolvedName =
                            if (selected.name.isBlank() || selected.name == selected.address) (pickedName ?: addr) else selected.name
                        val updated = selected.copy(name = resolvedName, address = addr)
                        ensureBluetoothPermissions {
                            vm.select(updated)
                            vm.connect()
                        }
                    }
                } else {
                    vm.clientControlAddress.value = addr
                }
                showDevicePicker = false
            }
        )
    }

    // Handle back navigation for Client detail — 只回到列表页，不断连
    BackHandler(enabled = inDetail) {
        selectedClientKey = null
        vm.clearSelectedKey()
    }
}
