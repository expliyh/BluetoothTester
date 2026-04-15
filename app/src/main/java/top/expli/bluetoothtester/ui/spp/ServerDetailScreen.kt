package top.expli.bluetoothtester.ui.spp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.NetworkPing
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
fun ServerDetailScreen(
    modifier: Modifier = Modifier,
    sessionState: SppSession?,
    isHistory: Boolean = false,
    onTextChange: (String) -> Unit = {},
    onPayloadChange: (Int) -> Unit = {},
    onSend: () -> Unit = {},
    onToggleSpeedTest: () -> Unit = {},
    onToggleSpeedTestMode: () -> Unit = {},
    onMuteConsoleDuringTestChange: (Boolean) -> Unit = {},
    onSpeedTestWindowOpenChange: (Boolean) -> Unit = {},
    onSpeedTestPayloadChange: (String) -> Unit = {},
    onParseIncomingAsTextChange: (Boolean) -> Unit = {},
    onClearChat: () -> Unit = {},
    onDisconnectServerClient: () -> Unit = {},
    onDisconnectDuringTest: () -> Unit = {},
    onScrollToLatest: (() -> Unit) -> Unit = {},
    onStartPeriodicTest: () -> Unit = {},
    onStopPeriodicTest: () -> Unit = {},
    onStartPingTest: () -> Unit = {},
    onStopPingTest: () -> Unit = {},
    onUpdatePeriodicInterval: (Long) -> Unit = {},
    onUpdatePeriodicStopCondition: (PeriodicStopCondition) -> Unit = {},
    onUpdatePeriodicSendPayloadSize: (Int) -> Unit = {},
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
    var showPingRttSheet by remember { mutableStateOf(false) }
    val showSpeedTestSheet = sessionState.speedTestWindowOpen

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

    // ── Derived state ──
    val connectionState = if (isHistory) SppConnectionState.Closed else sessionState.connectionState
    val activeTestType = sessionState.activeTestType
    val clientConnected = connectionState == SppConnectionState.Connected

    // ── Build ActionBar items (only when client connected) ──
    val actionBarItems = buildList {
        // Item 1: 断开客户端 or 断连 (during test)
        if (activeTestType != null) {
            add(
                ActionBarItem(
                    label = "断连",
                    icon = Icons.Default.Stop,
                    onClick = onDisconnectDuringTest
                )
            )
        } else {
            add(
                ActionBarItem(
                    label = "断开客户端",
                    icon = Icons.Default.Stop,
                    onClick = onDisconnectServerClient
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

        Column(modifier = Modifier.fillMaxSize()) {
            // ── Server_Client_Session area ──
            // V2 extension point: This Box can be replaced with TabRow + HorizontalPager
            // to support multiple concurrent client sessions.
            Box(modifier = Modifier.weight(1f)) {
                if (isHistory || clientConnected) {
                    // Full session UI (active or history read-only)
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
                                    bottom = if (!isHistory) composerHeightDp + imeGap + 12.dp else 12.dp
                                )
                            )
                        }

                        // ── Bottom: Speed summary + ActionBar + MessageInputBar (active only) ──
                        if (!isHistory) {
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
                                    // Speed test summary bar (conditional)
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
                                        sendEnabled = clientConnected,
                                        onMoreClick = { showBottomSheet = true }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Not connected and not history — show ConnectionStatusBanner + empty chat
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
                                bottom = 12.dp
                            )
                        )
                    }
                }
            }
        }
    }

    // ── Server BottomSheet (low-frequency operations only) ──
    if (showBottomSheet) {
        ServerBottomSheet(
            sessionState = sessionState,
            isHistory = isHistory,
            onDismiss = { showBottomSheet = false },
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

    // ── Sheet composables (active mode only) ──
    if (!isHistory && showSpeedTestSheet) {
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

    if (!isHistory && showPeriodicTestSheet) {
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

    if (!isHistory && showPingRttSheet) {
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

// ── Task 9.2: Server BottomSheet (low-frequency operations only) ──
// NO "连接循环压测" and NO "从已绑定设备连接" (Client-only features).

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerBottomSheet(
    sessionState: SppSession,
    isHistory: Boolean = false,
    onDismiss: () -> Unit,
    onParseIncomingAsTextChange: (Boolean) -> Unit,
    onShowPayloadDialog: () -> Unit,
    onClearChat: () -> Unit
) {
    val selected = sessionState.device

    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (!isHistory) {
            // 1. 解析接收数据 — Switch
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

            // 2. 接收缓冲大小 — opens dialog
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

            // 3. 清空聊天记录
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
        }

        // Socket 信息 — read-only display (always shown)
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
                    sessionState.remoteDeviceAddress?.let { addr ->
                        Text("已连接客户端: $addr", fontFamily = FontFamily.Monospace)
                    }
                }
            },
            leadingContent = { Icon(selected.role.icon(), contentDescription = null) },
            modifier = Modifier.padding(horizontal = 6.dp)
        )

        Box(modifier = Modifier.height(12.dp))
    }
}
