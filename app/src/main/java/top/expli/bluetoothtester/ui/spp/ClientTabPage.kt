package top.expli.bluetoothtester.ui.spp

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.coroutines.launch
import top.expli.bluetoothtester.data.SettingsStore
import top.expli.bluetoothtester.model.DeviceType
import top.expli.bluetoothtester.model.ScanMode
import top.expli.bluetoothtester.model.SecurityMode
import top.expli.bluetoothtester.model.SppConnectionState
import top.expli.bluetoothtester.model.SppUiState
import top.expli.bluetoothtester.model.SppViewModel
import top.expli.bluetoothtester.ui.common.DevicePickerSheet
import top.expli.bluetoothtester.ui.navigation.AppNavTransitions

@SuppressLint("MissingPermission")
@Composable
fun ClientTabPage(
    vm: SppViewModel,
    state: SppUiState,
    snackbarHostState: SnackbarHostState,
    ensureBluetoothPermissions: (() -> Unit) -> Unit,
    onScrollToLatest: (() -> Unit) -> Unit,
    onIsInDetailChanged: (Boolean) -> Unit,
    onBackClick: ((() -> Unit)?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val latestState by rememberUpdatedState(state)

    // Collect control card state from ViewModel
    val address by vm.clientControlAddress.collectAsState()
    val uuid by vm.clientControlUuid.collectAsState()
    val securityMode by vm.clientControlSecurityMode.collectAsState()

    // Device picker state
    var showDevicePicker by remember { mutableStateOf(false) }
    var pickerForReconnect by remember { mutableStateOf(false) }

    // Persist SecurityMode changes
    LaunchedEffect(securityMode) {
        vm.viewModelScope.launch {
            SettingsStore.updateClientDefaultSecurityMode(context, securityMode)
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val inDetail = currentRoute != null && currentRoute.contains("SessionDetail")

    // Derive active session key from NavHost route (survives recomposition/rotation)
    val activeSessionKey: String? = if (inDetail) {
        try { backStackEntry?.toRoute<ClientRoute.SessionDetail>()?.sessionKey } catch (_: Exception) { null }
    } else null

    // Report detail state changes to parent
    LaunchedEffect(inDetail) {
        onIsInDetailChanged(inDetail)
        onBackClick(if (inDetail) ({ navController.navigateUp() }) else null)
    }

    // Defensive cleanup
    DisposableEffect(Unit) {
        onDispose {
            onIsInDetailChanged(false)
            onBackClick(null)
        }
    }

    NavHost(
        navController = navController,
        startDestination = ClientRoute.SessionList,
        enterTransition = AppNavTransitions.enter,
        exitTransition = AppNavTransitions.exit,
        popEnterTransition = AppNavTransitions.popEnter,
        popExitTransition = AppNavTransitions.popExit,
        modifier = modifier
    ) {
        composable<ClientRoute.SessionList> {
            // Main view: Control Card + Session List
            Column(modifier = Modifier.fillMaxSize()) {
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
                            val key = "client:$sessionId"
                            vm.selectClientSession(sessionId)
                            navController.navigate(ClientRoute.SessionDetail(key))
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
                                    vm.selectClientSession(sessionId)
                                    navController.navigate(ClientRoute.SessionDetail(key))
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
                                    val key = "client:${snapshot.sessionId}"
                                    vm.selectClientHistorySession(snapshot.sessionId)
                                    navController.navigate(ClientRoute.SessionDetail(key))
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

        composable<ClientRoute.SessionDetail> { entry ->
            val route = entry.toRoute<ClientRoute.SessionDetail>()
            val sessionKey = route.sessionKey

            // Sync ViewModel's selectedKey to this session.
            // Try active sessions first; fall back to history rehydration
            // (needed when the route is restored after process recreation).
            // If neither source has the key, pop the route.
            var rehydrated by remember(sessionKey) { mutableStateOf(false) }
            LaunchedEffect(sessionKey) {
                val sessionId = sessionKey.removePrefix("client:")
                if (latestState.sessions.containsKey(sessionKey)) {
                    vm.selectClientSession(sessionId)
                } else {
                    vm.selectClientHistorySession(sessionId)
                }
                rehydrated = true
            }

            val selectedSession = state.sessions[sessionKey]

            // Navigate back if session was present but is now gone.
            // Skip the initial null: navigate() fires before ViewModel state
            // propagates, so the first composition may see null transiently.
            var sessionSeen by remember(sessionKey) { mutableStateOf(false) }
            if (selectedSession != null) sessionSeen = true
            LaunchedEffect(selectedSession, sessionSeen, rehydrated) {
                if (rehydrated && !sessionSeen && selectedSession == null) {
                    // Rehydration completed but session still not found — pop
                    navController.navigateUp()
                } else if (sessionSeen && selectedSession == null) {
                    // Session was present but disappeared
                    navController.navigateUp()
                }
            }

            if (selectedSession != null) {
                val isReadOnly = selectedSession.connectionState == SppConnectionState.Closed ||
                        selectedSession.connectionState == SppConnectionState.Error

                ClientDetailScreen(
                    modifier = Modifier.fillMaxSize(),
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
                            latestState.sessions[sessionKey]?.connectionState == SppConnectionState.Connected
                        if (active) {
                            sessionKey.removePrefix("client:").let { vm.disconnectClientSession(it) }
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
                        activeSessionKey?.let { latestState.sessions[it] }?.device
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
}
