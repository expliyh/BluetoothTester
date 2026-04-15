package top.expli.bluetoothtester.ui.spp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.expli.bluetoothtester.model.SecurityMode
import top.expli.bluetoothtester.model.ServerListenerState
import top.expli.bluetoothtester.model.SppConnectionState
import top.expli.bluetoothtester.model.SppSession

@Composable
fun ServerSessionListScreen(
    sessionState: SppSession?,
    listenerState: ServerListenerState,
    uuid: String,
    securityMode: SecurityMode,
    onSecurityModeChange: (SecurityMode) -> Unit,
    lastError: String?,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    onOpenActiveSession: () -> Unit,
    onOpenHistorySession: (snapshotId: String) -> Unit,
    onDeleteHistorySession: (snapshotId: String) -> Unit,
    onClearSessionHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clientConnected = sessionState?.connectionState == SppConnectionState.Connected
    val historyList = sessionState?.sessionHistory
        ?.sortedByDescending { it.disconnectedAt }
        ?: emptyList()
    val hasActive = clientConnected
    val hasHistory = historyList.isNotEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // 1. 顶部：ServerControlCard
        item(key = "server_control_card") {
            ServerControlCard(
                listenerState = listenerState,
                uuid = uuid,
                onUuidChange = {}, // UUID is managed by tab config, not editable here
                securityMode = securityMode,
                onSecurityModeChange = onSecurityModeChange,
                onStartListening = onStartListening,
                onStopListening = onStopListening,
                listenEnabled = uuid.isNotBlank() && listenerState != ServerListenerState.Listening,
                lastError = lastError,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        // 2. 活跃会话卡片
        if (hasActive && sessionState != null) {
            item(key = "active_session") {
                ActiveSessionCard(
                    session = sessionState,
                    securityMode = securityMode,
                    onClick = onOpenActiveSession,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        // 3. 历史会话列表
        if (hasHistory) {
            items(
                items = historyList,
                key = { it.id }
            ) { snapshot ->
                HistorySessionCard(
                    snapshot = snapshot,
                    onClick = { onOpenHistorySession(snapshot.id) },
                    onDelete = { onDeleteHistorySession(snapshot.id) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        // 4. 空状态占位
        if (!hasActive && !hasHistory) {
            item(key = "empty_placeholder") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无会话，等待客户端连接…",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
