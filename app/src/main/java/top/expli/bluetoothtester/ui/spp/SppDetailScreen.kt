package top.expli.bluetoothtester.ui.spp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.expli.bluetoothtester.model.SppChatDirection
import top.expli.bluetoothtester.model.SppChatItem
import top.expli.bluetoothtester.model.SppConnectionState
import top.expli.bluetoothtester.model.SppRole
import top.expli.bluetoothtester.model.SppSession

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SppDetailScreen(
    modifier: Modifier = Modifier,
    sessionState: SppSession?,
    onTextChange: (String) -> Unit,
    onPayloadChange: (Int) -> Unit,
    onSend: () -> Unit,
    onToggleSpeedTest: () -> Unit,
    onToggleSpeedTestMode: () -> Unit,
    onMuteConsoleDuringTestChange: (Boolean) -> Unit,
    onSpeedTestWindowOpenChange: (Boolean) -> Unit,
    onSpeedTestPayloadChange: (String) -> Unit = {},
    onParseIncomingAsTextChange: (Boolean) -> Unit,
    onToggleConnection: () -> Unit,
    onClearChat: () -> Unit,
    onConnectFromBondedDevice: () -> Unit = {},
    onScrollToLatest: (() -> Unit) -> Unit = {}  // 向外暴露滚动函数
) {
    val session = sessionState
    if (session == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("未选择 Socket", style = MaterialTheme.typography.titleMedium)
        }
        return
    }
    val selected = session.device

    var showActions by remember { mutableStateOf(false) }
    var showPayloadDialog by remember { mutableStateOf(false) }
    val showSpeedTestSheet = session.speedTestWindowOpen

    fun openSpeedTestSheet() {
        onSpeedTestWindowOpenChange(true)
    }

    fun closeSpeedTestSheet() {
        onSpeedTestWindowOpenChange(false)
    }

    DisposableEffect(Unit) {
        onDispose { onSpeedTestWindowOpenChange(false) }
    }

    // LazyListState 用于控制滚动
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // 注册滚动到最新的函数
    DisposableEffect(Unit) {
        onScrollToLatest {
            coroutineScope.launch {
                if (session.chat.isNotEmpty()) {
                    listState.animateScrollToItem(0)
                }
            }
        }
        onDispose { }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        val density = LocalDensity.current
        val imeVisible = WindowInsets.ime.getBottom(density) > 5
        val imeGap = if (imeVisible) 8.dp else 0.dp
        var composerHeightPx by remember { mutableStateOf(0) }
        val composerHeightDp = with(density) { composerHeightPx.toDp() }

        Box(modifier = Modifier.fillMaxSize()) {
            if (session.chat.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无消息", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = true,
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        top = 12.dp,
                        end = 12.dp,
                        bottom = composerHeightDp + imeGap + 12.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(session.chat, key = { it.id }) { item ->
                        ChatLine(item)
                    }
                }
            }

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
                    if (
                        session.speedTestRunning ||
                        session.speedTestTxAvgBps != null ||
                        session.speedTestRxAvgBps != null
                    ) {
                        val title = if (session.speedTestRunning) "测速中" else "上次测速"
                        val txInstant = session.speedTestTxInstantBps?.let(::formatBps) ?: "--"
                        val txAvg = session.speedTestTxAvgBps?.let(::formatBps) ?: "--"
                        val rxInstant = session.speedTestRxInstantBps?.let(::formatBps) ?: "--"
                        val rxAvg = session.speedTestRxAvgBps?.let(::formatBps) ?: "--"
                        Text(
                            "$title · TX $txInstant / $txAvg · RX $rxInstant / $rxAvg",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { openSpeedTestSheet() }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = { showActions = true }) {
                            Icon(Icons.Default.Add, contentDescription = "功能")
                        }

                        OutlinedTextField(
                            value = session.sendingText,
                            onValueChange = onTextChange,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("输入…") },
                            maxLines = 4
                        )

                        val speedTesting = session.speedTestRunning
                        LongPressIconButton(
                            icon = if (speedTesting) Icons.Default.Stop else Icons.AutoMirrored.Filled.Send,
                            contentDescription = if (speedTesting) "停止测速" else "发送",
                            enabled = speedTesting || session.connectionState == SppConnectionState.Connected,
                            onClick = if (speedTesting) onToggleSpeedTest else onSend,
                            onLongClick = {
                                openSpeedTestSheet()
                                if (!speedTesting) onToggleSpeedTest()
                            }
                        )
                    }
                }
            }
        }
    }

    if (showActions) {
        ModalBottomSheet(
            onDismissRequest = { showActions = false }
        ) {
            val active =
                session.connectionState == SppConnectionState.Connected || session.connectionState == SppConnectionState.Listening
            val connecting = session.connectionState == SppConnectionState.Connecting
            val connectTitle = when (selected.role) {
                SppRole.Client -> if (active) "断开连接" else "连接"
                SppRole.Server -> if (active) "停止监听" else "开始监听"
            }
            val connectIcon = when {
                connecting -> Icons.AutoMirrored.Filled.BluetoothSearching
                active -> Icons.Default.Stop
                else -> Icons.Default.PlayArrow
            }

            ListItem(
                headlineContent = { Text(connectTitle) },
                supportingContent = { Text("状态: ${session.connectionState.label()}") },
                leadingContent = { Icon(connectIcon, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 6.dp),
                trailingContent = {
                    TextButton(onClick = {
                        showActions = false
                        onToggleConnection()
                    }, enabled = !connecting) { Text(if (active) "停止" else "开始") }
                }
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text("测速窗口") },
                supportingContent = { Text("记录发送/接收速度") },
                leadingContent = { Icon(Icons.Default.Tune, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 6.dp),
                trailingContent = {
                    TextButton(onClick = {
                        showActions = false
                        openSpeedTestSheet()
                    }) { Text("打开") }
                }
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text("解析接收数据") },
                supportingContent = {
                    Text(if (session.parseIncomingAsText) "UTF-8 文本（非文本则显示 HEX）" else "HEX 原始数据")
                },
                leadingContent = { Icon(Icons.Default.TextFields, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 6.dp),
                trailingContent = {
                    Switch(
                        checked = session.parseIncomingAsText,
                        onCheckedChange = onParseIncomingAsTextChange
                    )
                }
            )
            HorizontalDivider()

            if (selected.role == SppRole.Client) {
                ListItem(
                    headlineContent = { Text("从已绑定设备连接") },
                    supportingContent = { Text("从系统已配对设备选择") },
                    leadingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.BluetoothSearching,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.padding(horizontal = 6.dp),
                    trailingContent = {
                        TextButton(
                            onClick = {
                                showActions = false
                                onConnectFromBondedDevice()
                            },
                            enabled = !connecting && !active
                        ) { Text("选择") }
                    }
                )
                HorizontalDivider()
            }

            ListItem(
                headlineContent = { Text("接收缓冲大小") },
                supportingContent = { Text("${session.payloadSize} 字节") },
                leadingContent = { Icon(Icons.Default.Tune, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 6.dp),
                trailingContent = {
                    TextButton(onClick = {
                        showActions = false
                        showPayloadDialog = true
                    }) { Text("修改") }
                }
            )
            HorizontalDivider()

            ListItem(
                headlineContent = { Text("清空聊天记录") },
                supportingContent = { Text("仅清空本次会话展示") },
                leadingContent = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 6.dp),
                trailingContent = {
                    TextButton(onClick = {
                        showActions = false
                        onClearChat()
                    }) { Text("清空") }
                }
            )
            HorizontalDivider()

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

    if (showPayloadDialog) {
        var input by remember { mutableStateOf(session.payloadSize.toString()) }
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

    if (showSpeedTestSheet) {
        SppSpeedTestSheet(
            session = session,
            onDismissRequest = { closeSpeedTestSheet() },
            onToggleSpeedTest = onToggleSpeedTest,
            onToggleSpeedTestMode = onToggleSpeedTestMode,
            onMuteConsoleDuringTestChange = onMuteConsoleDuringTestChange,
            onSpeedTestPayloadChange = onSpeedTestPayloadChange
        )
    }
}

@Composable
private fun ChatLine(item: SppChatItem) {
    val arrangement = when (item.direction) {
        SppChatDirection.In -> Arrangement.Start
        SppChatDirection.Out -> Arrangement.End
        SppChatDirection.System -> Arrangement.Center
    }
    val color = when (item.direction) {
        SppChatDirection.In -> MaterialTheme.colorScheme.onSurface
        SppChatDirection.Out -> MaterialTheme.colorScheme.primary
        SppChatDirection.System -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = arrangement
    ) {
        Text(
            text = item.text,
            color = color,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            textAlign = when (item.direction) {
                SppChatDirection.In -> TextAlign.Start
                SppChatDirection.Out -> TextAlign.End
                SppChatDirection.System -> TextAlign.Center
            },
            modifier = Modifier.fillMaxWidth(0.92f)
        )
    }
}

@Composable
private fun LongPressIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .combinedClickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onLongClick = onLongClick,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        val alpha = if (enabled) 1f else 0.38f
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = LocalContentColor.current.copy(alpha = alpha)
        )
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
    return "%.2f %s".format(value, units[unitIndex])
}
