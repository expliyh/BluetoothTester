package top.expli.bluetoothtester.ui.spp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import top.expli.bluetoothtester.model.SppChatDirection
import top.expli.bluetoothtester.model.SppChatItem
import top.expli.bluetoothtester.model.SppConnectionState
import top.expli.bluetoothtester.model.SppRole
import top.expli.bluetoothtester.model.SppUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SppDetailScreen(
    modifier: Modifier = Modifier,
    state: SppUiState,
    onTextChange: (String) -> Unit,
    onPayloadChange: (Int) -> Unit,
    onSend: () -> Unit,
    onToggleConnection: () -> Unit,
    onClearChat: () -> Unit
) {
    val selected = state.selected
    if (selected == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("未选择 Socket", style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    var showActions by remember { mutableStateOf(false) }
    var showPayloadDialog by remember { mutableStateOf(false) }

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
            if (state.chat.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无消息", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
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
                    items(state.chat, key = { it.id }) { item ->
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
                    Divider()
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
                            value = state.sendingText,
                            onValueChange = onTextChange,
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("输入…") },
                            maxLines = 4
                        )

                        IconButton(
                            onClick = onSend,
                            enabled = state.connectionState == SppConnectionState.Connected
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "发送")
                        }
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
                state.connectionState == SppConnectionState.Connected || state.connectionState == SppConnectionState.Listening
            val connecting = state.connectionState == SppConnectionState.Connecting
            val connectTitle = when (selected.role) {
                SppRole.Client -> if (active) "断开连接" else "连接"
                SppRole.Server -> if (active) "停止监听" else "开始监听"
            }
            val connectIcon = when {
                connecting -> Icons.Default.BluetoothSearching
                active -> Icons.Default.Stop
                else -> Icons.Default.PlayArrow
            }

            ListItem(
                headlineContent = { Text(connectTitle) },
                supportingContent = { Text("状态: ${state.connectionState.label()}") },
                leadingContent = { Icon(connectIcon, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 6.dp),
                trailingContent = {
                    TextButton(onClick = {
                        showActions = false
                        onToggleConnection()
                    }, enabled = !connecting) { Text(if (active) "停止" else "开始") }
                }
            )
            Divider()

            ListItem(
                headlineContent = { Text("接收缓冲大小") },
                supportingContent = { Text("${state.payloadSize} 字节") },
                leadingContent = { Icon(Icons.Default.Tune, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 6.dp),
                trailingContent = {
                    TextButton(onClick = {
                        showActions = false
                        showPayloadDialog = true
                    }) { Text("修改") }
                }
            )
            Divider()

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
            Divider()

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
        var input by remember { mutableStateOf(state.payloadSize.toString()) }
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
