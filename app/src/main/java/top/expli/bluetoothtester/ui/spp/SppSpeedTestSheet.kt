package top.expli.bluetoothtester.ui.spp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import top.expli.bluetoothtester.model.SppConnectionState
import top.expli.bluetoothtester.model.SppSession
import top.expli.bluetoothtester.model.SppSpeedSample

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SppSpeedTestSheet(
    session: SppSession,
    onDismissRequest: () -> Unit,
    onToggleSpeedTest: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SPP 测速", style = MaterialTheme.typography.titleMedium)
                AssistChip(
                    onClick = {},
                    label = { Text(if (session.speedTestRunning) "测速中" else "已停止") }
                )
            }

            val canOperate =
                session.connectionState == SppConnectionState.Connected || session.speedTestRunning
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onToggleSpeedTest, enabled = canOperate) {
                    Text(if (session.speedTestRunning) "停止" else "开始")
                }
                TextButton(onClick = onDismissRequest) { Text("关闭") }
            }

            Divider()

            val selected = session.device
            ListItem(
                headlineContent = { Text(selected.name) },
                supportingContent = {
                    Text(
                        if (selected.address.isBlank()) selected.uuid else selected.address,
                        fontFamily = FontFamily.Monospace
                    )
                }
            )

            SpeedSummary(
                title = "发送",
                instantBps = session.speedTestTxInstantBps,
                avgBps = session.speedTestTxAvgBps,
                totalBytes = session.speedTestTxTotalBytes
            )
            SpeedSummary(
                title = "接收",
                instantBps = session.speedTestRxInstantBps,
                avgBps = session.speedTestRxAvgBps,
                totalBytes = session.speedTestRxTotalBytes
            )
            ListItem(
                headlineContent = { Text("时长") },
                supportingContent = {
                    Text(
                        formatElapsedMs(session.speedTestElapsedMs),
                        fontFamily = FontFamily.Monospace
                    )
                }
            )

            Divider()

            Text("速度记录（最新在前）", style = MaterialTheme.typography.titleSmall)
            SpeedSampleList(session.speedTestSamples)
        }
    }
}

@Composable
private fun SpeedSummary(
    title: String,
    instantBps: Double?,
    avgBps: Double?,
    totalBytes: Long,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "即时: ${instantBps?.let(::formatBps) ?: "--"}",
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "平均: ${avgBps?.let(::formatBps) ?: "--"}",
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "累计: ${formatBytes(totalBytes)}",
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        modifier = modifier
    )
}

@Composable
private fun SpeedSampleList(samples: List<SppSpeedSample>) {
    if (samples.isEmpty()) {
        Text(
            "暂无记录（开始测速后将自动记录）",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        return
    }

    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
        items(samples, key = { it.id }) { s ->
            ListItem(
                headlineContent = {
                    Text(
                        "t=${formatElapsedMs(s.elapsedMs)} · TX ${formatBps(s.txInstantBps)} · RX ${
                            formatBps(
                                s.rxInstantBps
                            )
                        }",
                        fontFamily = FontFamily.Monospace
                    )
                },
                supportingContent = {
                    Text(
                        "avg TX ${formatBps(s.txAvgBps)} · avg RX ${formatBps(s.rxAvgBps)}",
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            )
            Divider()
        }
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

private fun formatBytes(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return "%.2f %s".format(value, units[unitIndex])
}

private fun formatElapsedMs(ms: Long): String {
    if (ms <= 0) return "0.0s"
    val seconds = ms / 1000.0
    return "%.1fs".format(seconds)
}
