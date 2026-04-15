package top.expli.bluetoothtester.ui.spp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.expli.bluetoothtester.model.ActiveTestType
import top.expli.bluetoothtester.model.PeriodicStopCondition
import top.expli.bluetoothtester.model.SppConnectionState
import top.expli.bluetoothtester.model.SppSession

import top.expli.bluetoothtester.util.formatBps

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDetailScreen(
    modifier: Modifier = Modifier,
    sessionState: SppSession?,
    readOnly: Boolean = false,
    onTextChange: (String) -> Unit = {},
    onPayloadChange: (Int) -> Unit = {},
    onSend: () -> Unit = {},
    onToggleSpeedTest: () -> Unit = {},
    onToggleSpeedTestMode: () -> Unit = {},
    onMuteConsoleDuringTestChange: (Boolean) -> Unit = {},
    onSpeedTestWindowOpenChange: (Boolean) -> Unit = {},
    onSpeedTestPayloadChange: (String) -> Unit = {},
    onParseIncomingAsTextChange: (Boolean) -> Unit = {},
    onToggleConnection: () -> Unit = {},
    onClearChat: () -> Unit = {},
    onConnectFromBondedDevice: () -> Unit = {},
    onScrollToLatest: (() -> Unit) -> Unit = {},
    onStartPeriodicTest: () -> Unit = {},
    onStopPeriodicTest: () -> Unit = {},
    onStartConnectionCycleTest: () -> Unit = {},
    onStopConnectionCycleTest: () -> Unit = {},
    onStartPingTest: () -> Unit = {},
    onStopPingTest: () -> Unit = {},
    onDisconnectDuringTest: () -> Unit = {},
    onAutoReconnectEnabledChange: (Boolean) -> Unit = {},
    onUpdatePeriodicInterval: (Long) -> Unit = {},
    onUpdatePeriodicStopCondition: (PeriodicStopCondition) -> Unit = {},
    onUpdatePeriodicSendPayloadSize: (Int) -> Unit = {},
    onUpdateConnectionCycleTargetCount: (Int) -> Unit = {},
    onUpdateConnectionCycleInterval: (Long) -> Unit = {},
    onUpdateConnectionCycleTimeout: (Long) -> Unit = {},
    onUpdatePingTargetCount: (Int) -> Unit = {},
    onUpdatePingInterval: (Long) -> Unit = {},
    onUpdatePingTimeout: (Long) -> Unit = {},
    onUpdatePingPaddingSize: (Int) -> Unit = {},
    onSpeedTestWithCrcChange: (Boolean) -> Unit = {},
    onSpeedTestTargetBytesChange: (Long) -> Unit = {},
    onSendPayloadSizeChange: (Int) -> Unit = {}
) {
    if (sessionState == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("未选择 Socket", style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    var showBottomSheet by remember { mutableStateOf(false) }
    var showPayloadDialog by remember { mutableStateOf(false) }
    var showPeriodicTestSheet by remember { mutableStateOf(false) }
    var showConnectionCycleSheet by remember { mutableStateOf(false) }
    var showPingRttSheet by remember { mutableStateOf(false) }
    val showSpeedTestSheet = sessionState.speedTestWindowOpen && !readOnly

    fun openSpeedTestSheet() { onSpeedTestWindowOpenChange(true) }
    fun closeSpeedTestSheet() { onSpeedTestWindowOpenChange(false) }

    DisposableEffect(Unit) {
        onDispose { onSpeedTestWindowOpenChange(false) }
    }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onScrollToLatest {
            coroutineScope.launch {
                if (sessionState.chat.isNotEmpty()) {
                    listState.animateScrollToItem(0)
                }
            }
        }
        onDispose { }
    }

    // ── Build ActionBar items ──
    val connectionState = sessionState.connectionState
    val activeTestType = sessionState.activeTestType

    val actionBarItems = buildList {
        // Item 1: 连接/断开 or 断连
        if (activeTestType != null) {
            add(
                ActionBarItem(
                    label = "断连",
                    icon = Icons.Default.Stop,
                    onClick = onDisconnectDuringTest
                )
            )
        } else {
            val isConnected = connectionState == SppConnectionState.Connected
            val isConnecting = connectionState == SppConnectionState.Connecting
            add(
                ActionBarItem(
                    label = if (isConnected) "断开" else "连接",
                    icon = if (isConnected) Icons.Default.Stop else Icons.Default.PlayArrow,
                    onClick = onToggleConnection,
                    isLoading = isConnecting
                )
            )
        }

        // Item 2: 测速
        add(
            ActionBarItem(
                label = "测速",
                icon = if (activeTestType == ActiveTestType.SpeedTest) Icons.Default.Tune else Icons.Default.Speed,
                onClick = { openSpeedTestSheet() },
                isRunning = activeTestType == ActiveTestType.SpeedTest
            )
        )

        // Item 3: 定时发送
        add(
            ActionBarItem(
                label = "定时发送",
                icon = Icons.Default.Timer,
                onClick = { showPeriodicTestSheet = true },
                isRunning = activeTestType == ActiveTestType.PeriodicSend
            )
        )

        // Item 4: Ping RTT
        add(
            ActionBarItem(
                label = "Ping",
                icon = Icons.Default.NetworkPing,
                onClick = { showPingRttSheet = true },
                isRunning = activeTestType == ActiveTestType.PingRtt
            )
        )
    }

    // ── Main layout ──
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        val density = LocalDensity.current
        val imeVisible = WindowInsets.ime.getBottom(density) > 5
        val imeGap = if (imeVisible) 8.dp else 0.dp
        var composerHeightPx by remember { mutableIntStateOf(0) }
        val composerHeightDp = with(density) { composerHeightPx.toDp() }

        Box(modifier = Modifier.fillMaxSize()) {
            // ── Top: ConnectionStatusBanner + ChatMessageList ──
            Column(modifier = Modifier.fillMaxSize()) {
                ConnectionStatusBanner(
                    state = connectionState,
                    remoteAddress = sessionState.remoteDeviceAddress,
                    lastError = sessionState.lastError
                )

                ChatMessageList(
                    chat = sessionState.chat,
                    modifier = Modifier.weight(1f),
                    listState = listState,
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        top = 12.dp,
                        end = 12.dp,
                        bottom = if (readOnly) 12.dp else (composerHeightDp + imeGap + 12.dp)
                    )
                )

                // Read-only speed test summary at bottom
                if (readOnly && (sessionState.speedTestTxAvgBps != null || sessionState.speedTestRxAvgBps != null)) {
                    HorizontalDivider()
                    val txAvg = sessionState.speedTestTxAvgBps?.let(::formatBps) ?: "--"
                    val rxAvg = sessionState.speedTestRxAvgBps?.let(::formatBps) ?: "--"
                    Text(
                        "测速摘要 · TX $txAvg · RX $rxAvg",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            // ── Bottom: Speed summary + ActionBar + MessageInputBar (hidden in readOnly mode) ──
            if (!readOnly) {
            @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
            Surface(
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = imeGap)
                    .onSizeChanged { composerHeightPx = it.height }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Speed test summary bar
                    if (
                        sessionState.speedTestRunning ||
                        sessionState.speedTestTxAvgBps != null ||
                        sessionState.speedTestRxAvgBps != null
                    ) {
                        val title = if (sessionState.speedTestRunning) "测速中" else "上次测速"
                        val txAvg = sessionState.speedTestTxAvgBps?.let(::formatBps) ?: "--"
                        val rxAvg = sessionState.speedTestRxAvgBps?.let(::formatBps) ?: "--"
                        Text(
                            "$title · TX $txAvg · RX $rxAvg",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openSpeedTestSheet() }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    HorizontalDivider()

                    ActionBar(items = actionBarItems)

                    HorizontalDivider()

                    MessageInputBar(
                        sendingText = sessionState.sendingText,
                        onTextChange = onTextChange,
                        onSend = onSend,
                        sendEnabled = connectionState == SppConnectionState.Connected,
                        onMoreClick = { showBottomSheet = true }
                    )
                }
            }
            } // end if (!readOnly)
        }
    }

    // ── Client BottomSheet (low-frequency operations) ──
    if (showBottomSheet) {
        ClientBottomSheet(
            sessionState = sessionState,
            onDismiss = { showBottomSheet = false },
            onShowConnectionCycleSheet = {
                showBottomSheet = false
                showConnectionCycleSheet = true
            },
            onConnectFromBondedDevice = {
                showBottomSheet = false
                onConnectFromBondedDevice()
            },
            onAutoReconnectEnabledChange = onAutoReconnectEnabledChange,
            onParseIncomingAsTextChange = onParseIncomingAsTextChange,
            onShowPayloadDialog = {
                showBottomSheet = false
                showPayloadDialog = true
            },
            onClearChat = {
                showBottomSheet = false
                onClearChat()
            }
        )
    }

    // ── Payload size dialog ──
    if (showPayloadDialog) {
        var input by remember { mutableStateOf(sessionState.receiveBufferSize.toString()) }
        @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
        AlertDialog(
            onDismissRequest = { showPayloadDialog = false },
            title = { Text("接收缓冲大小") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("字节数") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    input.toIntOrNull()?.let(onPayloadChange)
                    showPayloadDialog = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showPayloadDialog = false }) { Text("取消") } }
        )
    }

    // ── Sheet composables ──
    if (showSpeedTestSheet) {
        SppSpeedTestSheet(
            session = sessionState,
            onDismissRequest = { closeSpeedTestSheet() },
            onToggleSpeedTest = onToggleSpeedTest,
            onToggleSpeedTestMode = onToggleSpeedTestMode,
            onMuteConsoleDuringTestChange = onMuteConsoleDuringTestChange,
            onSpeedTestPayloadChange = onSpeedTestPayloadChange,
            onSpeedTestWithCrcChange = onSpeedTestWithCrcChange,
            onSpeedTestTargetBytesChange = onSpeedTestTargetBytesChange,
            onSendPayloadSizeChange = onSendPayloadSizeChange
        )
    }

    if (showPeriodicTestSheet) {
        PeriodicTestSheet(
            session = sessionState,
            onDismiss = { showPeriodicTestSheet = false },
            onStart = onStartPeriodicTest,
            onStop = onStopPeriodicTest,
            onUpdateInterval = onUpdatePeriodicInterval,
            onUpdateStopCondition = onUpdatePeriodicStopCondition,
            onUpdateSendPayloadSize = onUpdatePeriodicSendPayloadSize
        )
    }

    if (showConnectionCycleSheet) {
        ConnectionCycleSheet(
            session = sessionState,
            onDismiss = { showConnectionCycleSheet = false },
            onStart = onStartConnectionCycleTest,
            onStop = onStopConnectionCycleTest,
            onUpdateTargetCount = onUpdateConnectionCycleTargetCount,
            onUpdateInterval = onUpdateConnectionCycleInterval,
            onUpdateTimeout = onUpdateConnectionCycleTimeout
        )
    }

    if (showPingRttSheet) {
        PingRttSheet(
            session = sessionState,
            onDismiss = { showPingRttSheet = false },
            onStart = onStartPingTest,
            onStop = onStopPingTest,
            onUpdateTargetCount = onUpdatePingTargetCount,
            onUpdateInterval = onUpdatePingInterval,
            onUpdateTimeout = onUpdatePingTimeout,
            onUpdatePaddingSize = onUpdatePingPaddingSize
        )
    }
}

// ── Task 8.2: Client BottomSheet (low-frequency operations only) ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClientBottomSheet(
    sessionState: SppSession,
    onDismiss: () -> Unit,
    onShowConnectionCycleSheet: () -> Unit,
    onConnectFromBondedDevice: () -> Unit,
    onAutoReconnectEnabledChange: (Boolean) -> Unit,
    onParseIncomingAsTextChange: (Boolean) -> Unit,
    onShowPayloadDialog: () -> Unit,
    onClearChat: () -> Unit
) {
    val selected = sessionState.device
    val active = sessionState.connectionState == SppConnectionState.Connected
    val connecting = sessionState.connectionState == SppConnectionState.Connecting

    ModalBottomSheet(onDismissRequest = onDismiss) {
        // 1. 连接循环压测 (Client only)
        ListItem(
            headlineContent = { Text("连接循环压测") },
            supportingContent = { Text("自动连接→断开循环测试") },
            trailingContent = {
                TextButton(onClick = onShowConnectionCycleSheet) { Text("打开") }
            }
        )
        HorizontalDivider()

        // 2. 从已绑定设备连接 (Client only)
        ListItem(
            headlineContent = { Text("从已绑定设备连接") },
            supportingContent = { Text("从系统已配对设备选择") },
            leadingContent = {
                Icon(Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null)
            },
            modifier = Modifier.padding(horizontal = 6.dp),
            trailingContent = {
                TextButton(
                    onClick = onConnectFromBondedDevice,
                    enabled = !connecting && !active
                ) { Text("选择") }
            }
        )
        HorizontalDivider()

        // 3. 自动重连开关
        ListItem(
            headlineContent = { Text("自动重连") },
            supportingContent = { Text("打流中断连后自动尝试重连") },
            trailingContent = {
                Switch(
                    checked = sessionState.autoReconnectEnabled,
                    onCheckedChange = onAutoReconnectEnabledChange
                )
            }
        )
        HorizontalDivider()

        // 4. 解析接收数据
        ListItem(
            headlineContent = { Text("解析接收数据") },
            supportingContent = {
                Text(if (sessionState.parseIncomingAsText) "UTF-8 文本（非文本则显示 HEX）" else "HEX 原始数据")
            },
            leadingContent = { Icon(Icons.Default.TextFields, contentDescription = null) },
            modifier = Modifier.padding(horizontal = 6.dp),
            trailingContent = {
                Switch(
                    checked = sessionState.parseIncomingAsText,
                    onCheckedChange = onParseIncomingAsTextChange
                )
            }
        )
        HorizontalDivider()

        // 5. 接收缓冲大小
        ListItem(
            headlineContent = { Text("接收缓冲大小") },
            supportingContent = { Text("${sessionState.receiveBufferSize} 字节") },
            leadingContent = { Icon(Icons.Default.Tune, contentDescription = null) },
            modifier = Modifier.padding(horizontal = 6.dp),
            trailingContent = {
                TextButton(onClick = onShowPayloadDialog) { Text("修改") }
            }
        )
        HorizontalDivider()

        // 6. 清空聊天记录
        ListItem(
            headlineContent = { Text("清空聊天记录") },
            supportingContent = { Text("仅清空本次会话展示") },
            leadingContent = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
            modifier = Modifier.padding(horizontal = 6.dp),
            trailingContent = {
                TextButton(onClick = onClearChat) { Text("清空") }
            }
        )
        HorizontalDivider()

        // 7. Socket 信息 (read-only)
        ListItem(
            headlineContent = { Text("Socket 信息") },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "${selected.role.label()} · ${selected.name}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (selected.address.isNotBlank()) {
                        Text("地址: ${selected.address}", fontFamily = FontFamily.Monospace)
                    }
                    Text("UUID: ${selected.uuid}", fontFamily = FontFamily.Monospace)
                }
            },
            leadingContent = { Icon(selected.role.icon(), contentDescription = null) },
            modifier = Modifier.padding(horizontal = 6.dp)
        )

        Box(modifier = Modifier.height(12.dp))
    }
}
