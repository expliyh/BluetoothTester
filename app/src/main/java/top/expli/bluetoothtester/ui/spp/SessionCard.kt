package top.expli.bluetoothtester.ui.spp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.expli.bluetoothtester.model.SecurityMode
import top.expli.bluetoothtester.model.SppConnectionState
import top.expli.bluetoothtester.util.formatBpsCompact
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val sessionCardDateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

@Composable
fun SessionCard(
    sessionId: String,
    deviceName: String,
    deviceAddress: String,
    connectionState: SppConnectionState,
    securityMode: SecurityMode,
    isClient: Boolean,
    disconnectedAt: Long? = null,
    speedTestTxAvgBps: Double? = null,
    speedTestRxAvgBps: Double? = null,
    chatCount: Int = 0,
    onClick: () -> Unit,
    onRetry: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isActive = connectionState == SppConnectionState.Connected || connectionState == SppConnectionState.Connecting
    val containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant

    Card(
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = deviceName.ifBlank { deviceAddress },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (deviceAddress.isNotBlank() && deviceAddress != deviceName) {
                        Text(
                            text = deviceAddress,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row {
                    Text(
                        text = securityMode.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (securityMode == SecurityMode.Insecure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val stateText = when (connectionState) {
                    SppConnectionState.Idle -> "空闲"
                    SppConnectionState.Connecting -> "连接中…"
                    SppConnectionState.Connected -> "已连接"
                    SppConnectionState.Closed -> "已断开"
                    SppConnectionState.Error -> "错误"
                }
                Column {
                    Text(text = stateText, style = MaterialTheme.typography.bodySmall)
                    // 历史会话摘要：测速结果 + 聊天记录数
                    if (!isActive) {
                        val parts = mutableListOf<String>()
                        speedTestTxAvgBps?.let { parts.add("TX ${formatBpsCompact(it)}") }
                        speedTestRxAvgBps?.let { parts.add("RX ${formatBpsCompact(it)}") }
                        if (chatCount > 0) parts.add("${chatCount}条消息")
                        if (parts.isNotEmpty()) {
                            Text(
                                text = parts.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (disconnectedAt != null && !isActive) {
                    Text(text = sessionCardDateFormat.format(Date(disconnectedAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row {
                    if (onRetry != null && connectionState == SppConnectionState.Error) {
                        IconButton(onClick = onRetry) { Icon(Icons.Default.Refresh, "重试") }
                    }
                    if (onDelete != null && !isActive) {
                        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "删除") }
                    }
                }
            }
        }
    }
}
