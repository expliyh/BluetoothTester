package top.expli.bluetoothtester.ui.spp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import top.expli.bluetoothtester.model.SppConnectionState
import top.expli.bluetoothtester.model.SppSession
import top.expli.bluetoothtester.model.SppSpeedTestMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SppSpeedTestSheet(
    session: SppSession,
    onDismissRequest: () -> Unit,
    onToggleSpeedTest: () -> Unit,
    onToggleSpeedTestMode: () -> Unit,
    onMuteConsoleDuringTestChange: (Boolean) -> Unit,
    onSpeedTestPayloadChange: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showPayloadDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("SPP 测速", style = MaterialTheme.typography.titleMedium)
                    val modeLabel =
                        when (session.speedTestMode) {
                            SppSpeedTestMode.TxOnly -> "单向TX"
                            SppSpeedTestMode.RxOnly -> "单向RX"
                            SppSpeedTestMode.Duplex -> "双向"
                        }
                    AssistChip(
                        onClick = onToggleSpeedTestMode,
                        enabled = !session.speedTestRunning,
                        label = {
                            Text(
                                "${if (session.speedTestRunning) "测速中" else "已停止"} · $modeLabel"
                            )
                        }
                    )
                }
            }

            item {
                val canOperate =
                    session.connectionState == SppConnectionState.Connected || session.speedTestRunning
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onToggleSpeedTest, enabled = canOperate) {
                        Text(if (session.speedTestRunning) "停止" else "开始")
                    }
                    TextButton(onClick = onDismissRequest) { Text("关闭") }
                }
            }

            item { Divider() }

            item {
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
            }

            item {
                ListItem(
                    headlineContent = { Text("测速Payload") },
                    supportingContent = {
                        Text(
                            if (session.speedTestPayload.isEmpty()) "默认（0x00~0xFF序列）"
                            else "自定义：${session.speedTestPayload}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    trailingContent = {
                        TextButton(
                            onClick = { showPayloadDialog = true },
                            enabled = !session.speedTestRunning
                        ) {
                            Text("设置")
                        }
                    }
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("测速时抑制控制台输出") },
                    supportingContent = {
                        Text(
                            "测速窗口打开/测速进行中时不写入聊天记录",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = session.muteConsoleDuringTest,
                            onCheckedChange = onMuteConsoleDuringTestChange
                        )
                    }
                )
            }

            item {
                SpeedSummary(
                    title = "发送",
                    instantBps = session.speedTestTxInstantBps,
                    avgBps = session.speedTestTxAvgBps,
                    totalBytes = session.speedTestTxTotalBytes
                )
            }
            item {
                SpeedSummary(
                    title = "接收",
                    instantBps = session.speedTestRxInstantBps,
                    avgBps = session.speedTestRxAvgBps,
                    totalBytes = session.speedTestRxTotalBytes
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("时长") },
                    supportingContent = {
                        Text(
                            formatElapsedMs(session.speedTestElapsedMs),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                )
            }

            item { SpeedDebug(session) }

            item { Divider() }

            item { Text("速度记录（最新在前）", style = MaterialTheme.typography.titleSmall) }

            if (session.speedTestSamples.isEmpty()) {
                item {
                    Text(
                        "暂无记录（开始测速后将自动记录）",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                items(session.speedTestSamples, key = { it.id }) { s ->
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
    }

    if (showPayloadDialog) {
        PayloadDialog(
            currentPayload = session.speedTestPayload,
            onConfirm = { newPayload ->
                onSpeedTestPayloadChange(newPayload)
                showPayloadDialog = false
            },
            onDismiss = { showPayloadDialog = false }
        )
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

@Composable
private fun SpeedDebug(session: SppSession) {
    val txWriteAvgMs = session.speedTestTxWriteAvgMs
    val txWriteMaxMs = session.speedTestTxWriteMaxMs
    val rxReadAvgMs = session.speedTestRxReadAvgMs
    val rxReadMaxMs = session.speedTestRxReadMaxMs
    val rxReadAvgBytes = session.speedTestRxReadAvgBytes
    val txDelay = session.speedTestTxFirstWriteDelayMs
    val rxDelay = session.speedTestRxFirstByteDelayMs

    if (
        !session.speedTestRunning &&
        txWriteAvgMs == null &&
        txWriteMaxMs == null &&
        rxReadAvgMs == null &&
        rxReadMaxMs == null &&
        rxReadAvgBytes == null &&
        txDelay == null &&
        rxDelay == null
    ) return

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("诊断", style = MaterialTheme.typography.titleSmall)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (txWriteAvgMs == null && rxReadAvgMs == null && txDelay == null && rxDelay == null) {
                    Text(
                        "测速进行中…（诊断数据采集中）",
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Text(
                    "TX write: avg ${txWriteAvgMs?.let { "%.2fms".format(it) } ?: "--"} · max ${
                        txWriteMaxMs?.let { "%.2fms".format(it) } ?: "--"
                    }",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "RX read: avg ${rxReadAvgMs?.let { "%.2fms".format(it) } ?: "--"} · max ${
                        rxReadMaxMs?.let { "%.2fms".format(it) } ?: "--"
                    } · avgBytes ${rxReadAvgBytes?.let { "%.0f".format(it) } ?: "--"}",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "startDelay: tx ${txDelay?.let { "${it}ms" } ?: "--"} · rx ${
                        rxDelay?.let { "${it}ms" } ?: "--"
                    }",
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayloadDialog(
    currentPayload: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(currentPayload) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置测速Payload") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "留空使用默认序列(0x00~0xFF循环)，或输入自定义文本",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Payload内容") },
                    placeholder = { Text("留空=默认序列") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
