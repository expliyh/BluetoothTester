package top.expli.bluetoothtester.ui.spp

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.expli.bluetoothtester.model.SecurityMode
import top.expli.bluetoothtester.model.ServerSessionSnapshot
import top.expli.bluetoothtester.model.SppSession
import top.expli.bluetoothtester.util.formatBps
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── ActiveSessionCard ──

@Composable
fun ActiveSessionCard(
    session: SppSession,
    securityMode: SecurityMode = SecurityMode.Secure,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 每秒刷新连接时长
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }

    val durationText = session.connectedAt?.let { formatDuration(now - it) } ?: ""

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 绿色圆点
            Icon(
                Icons.Filled.Circle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(10.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = session.remoteDeviceAddress ?: "未知设备",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = "Connected",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4CAF50)
                    )
                    Text(
                        text = securityMode.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (securityMode == SecurityMode.Insecure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "${session.chat.size} 条消息",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    if (durationText.isNotEmpty()) {
                        Text(
                            text = "连接 $durationText",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

// ── HistorySessionCard ──

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistorySessionCard(
    snapshot: ServerSessionSnapshot,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onDelete
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = snapshot.remoteDeviceAddress,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = sessionDateFormat.format(Date(snapshot.disconnectedAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${snapshot.chat.size} 条消息",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = snapshot.securityMode.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (snapshot.securityMode == SecurityMode.Insecure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
                // 测速摘要
                val speedSummary = buildString {
                    snapshot.speedTestTxAvgBps?.let { append("TX: ${formatBps(it)}") }
                    if (snapshot.speedTestTxAvgBps != null && snapshot.speedTestRxAvgBps != null) append(" / ")
                    snapshot.speedTestRxAvgBps?.let { append("RX: ${formatBps(it)}") }
                }
                if (speedSummary.isNotEmpty()) {
                    Text(
                        text = speedSummary,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ── Utility functions ──

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return when {
        hours > 0 -> "${hours}时${minutes % 60}分${seconds % 60}秒"
        minutes > 0 -> "${minutes}分${seconds % 60}秒"
        else -> "${seconds}秒"
    }
}

private val sessionDateFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
