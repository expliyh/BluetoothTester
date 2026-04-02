package top.expli.bluetoothtester.ui.l2cap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import top.expli.bluetoothtester.model.L2capConnectionState
import top.expli.bluetoothtester.model.L2capRole
import top.expli.bluetoothtester.model.L2capViewModel
import top.expli.bluetoothtester.model.SppChatDirection
import top.expli.bluetoothtester.model.SppChatItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun L2capScreen(
    onBackClick: () -> Unit,
    initialAddress: String = "",
    initialName: String = "",
    viewModel: L2capViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(initialAddress) {
        if (initialAddress.isNotBlank()) {
            viewModel.initFromRoute(initialAddress, initialName)
        }
    }

    val isConnected = uiState.connectionState is L2capConnectionState.Connected
    val isActive = isConnected ||
            uiState.connectionState is L2capConnectionState.Connecting ||
            uiState.connectionState is L2capConnectionState.Listening

    val surfaceColor = MaterialTheme.colorScheme.surface
    Surface(color = surfaceColor) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "L2CAP CoC",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.clearChat() }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "清空聊天")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Role tabs
                RoleTabs(
                    currentRole = uiState.role,
                    enabled = !isActive,
                    onRoleChange = { viewModel.switchRole(it) }
                )

                // Connection controls
                ConnectionControls(
                    role = uiState.role,
                    connectionState = uiState.connectionState,
                    targetAddress = uiState.targetAddress,
                    psm = uiState.psm,
                    assignedPsm = uiState.assignedPsm,
                    onAddressChange = { viewModel.updateTargetAddress(it) },
                    onPsmChange = { viewModel.updatePsm(it) },
                    onConnect = { addr, psm -> viewModel.connect(addr, psm) },
                    onListen = { viewModel.listen() },
                    onDisconnect = { viewModel.disconnect() }
                )

                // Error display
                uiState.lastError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                HorizontalDivider()

                // Chat area + send bar
                ChatArea(
                    chat = uiState.chat,
                    sendingText = uiState.sendingText,
                    isConnected = isConnected,
                    onTextChange = { viewModel.updateSendingText(it) },
                    onSend = { viewModel.sendOnce(uiState.sendingText) },
                    onToggleSpeedTest = { viewModel.toggleSpeedTest() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun RoleTabs(
    currentRole: L2capRole,
    enabled: Boolean,
    onRoleChange: (L2capRole) -> Unit
) {
    val selectedIndex = if (currentRole == L2capRole.Client) 0 else 1
    PrimaryTabRow(selectedTabIndex = selectedIndex) {
        Tab(
            selected = selectedIndex == 0,
            onClick = { if (enabled) onRoleChange(L2capRole.Client) },
            enabled = enabled,
            text = { Text("客户端") }
        )
        Tab(
            selected = selectedIndex == 1,
            onClick = { if (enabled) onRoleChange(L2capRole.Server) },
            enabled = enabled,
            text = { Text("服务端") }
        )
    }
}

@Composable
private fun ConnectionControls(
    role: L2capRole,
    connectionState: L2capConnectionState,
    targetAddress: String,
    psm: Int?,
    assignedPsm: Int?,
    onAddressChange: (String) -> Unit,
    onPsmChange: (Int?) -> Unit,
    onConnect: (String, Int) -> Unit,
    onListen: () -> Unit,
    onDisconnect: () -> Unit
) {
    val isActive = connectionState is L2capConnectionState.Connected ||
            connectionState is L2capConnectionState.Connecting ||
            connectionState is L2capConnectionState.Listening

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Connection state
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "状态:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = connectionState.label(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = connectionState.color()
                )
            }

            if (role == L2capRole.Client) {
                // Client mode: address + PSM input
                var psmText by rememberSaveable { mutableStateOf(psm?.toString() ?: "") }

                OutlinedTextField(
                    value = targetAddress,
                    onValueChange = onAddressChange,
                    label = { Text("目标地址") },
                    placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                    singleLine = true,
                    enabled = !isActive,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = psmText,
                    onValueChange = { input ->
                        psmText = input
                        onPsmChange(input.toIntOrNull())
                    },
                    label = { Text("PSM") },
                    placeholder = { Text("例如: 128") },
                    singleLine = true,
                    enabled = !isActive,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isActive) {
                        OutlinedButton(onClick = onDisconnect) {
                            Text("断开")
                        }
                    } else {
                        Button(
                            onClick = {
                                val p = psmText.toIntOrNull() ?: return@Button
                                onConnect(targetAddress, p)
                            },
                            enabled = targetAddress.isNotBlank() && psmText.toIntOrNull() != null
                        ) {
                            Text("连接")
                        }
                    }
                }
            } else {
                // Server mode: listen button + assigned PSM display
                assignedPsm?.let { p ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "系统分配 PSM:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = p.toString(),
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (isActive) {
                        OutlinedButton(onClick = onDisconnect) {
                            Text("停止监听")
                        }
                    } else {
                        Button(onClick = onListen) {
                            Text("开始监听")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatArea(
    chat: List<SppChatItem>,
    sendingText: String,
    isConnected: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onToggleSpeedTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 5

    Box(modifier = modifier.fillMaxSize()) {
        if (chat.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "暂无消息",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                reverseLayout = true,
                contentPadding = PaddingValues(
                    start = 12.dp, top = 12.dp, end = 12.dp,
                    bottom = 80.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(chat, key = { it.id }) { item ->
                    ChatLine(item)
                }
            }
        }

        // Send bar
        Surface(
            tonalElevation = 0.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Column {
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onToggleSpeedTest, enabled = isConnected) {
                        Icon(Icons.Default.Speed, contentDescription = "测速")
                    }

                    OutlinedTextField(
                        value = sendingText,
                        onValueChange = onTextChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("输入…") },
                        maxLines = 4
                    )

                    IconButton(
                        onClick = onSend,
                        enabled = isConnected && sendingText.isNotBlank()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送"
                        )
                    }
                }
            }
        }
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

// ─── Extension helpers ───

@Composable
private fun L2capConnectionState.label(): String = when (this) {
    L2capConnectionState.Idle -> "未连接"
    L2capConnectionState.Connecting -> "连接中…"
    L2capConnectionState.Listening -> "监听中…"
    L2capConnectionState.Connected -> "已连接"
    L2capConnectionState.Closed -> "已关闭"
    is L2capConnectionState.Error -> "错误: $message"
}

@Composable
private fun L2capConnectionState.color(): Color = when (this) {
    L2capConnectionState.Connected -> MaterialTheme.colorScheme.primary
    L2capConnectionState.Connecting, L2capConnectionState.Listening -> MaterialTheme.colorScheme.tertiary
    is L2capConnectionState.Error -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
