package top.expli.bluetoothtester.ui.spp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import top.expli.bluetoothtester.model.SppConnectionState
import top.expli.bluetoothtester.model.SppSession
import top.expli.bluetoothtester.model.SppSpeedTestMode
import top.expli.bluetoothtester.util.formatBps
import top.expli.bluetoothtester.util.formatBytes
import top.expli.bluetoothtester.util.formatElapsedMs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SppSpeedTestSheet(
    session: SppSession,
    onDismissRequest: () -> Unit,
    onToggleSpeedTest: () -> Unit,
    onToggleSpeedTestMode: () -> Unit,
    onMuteConsoleDuringTestChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onSpeedTestPayloadChange: (String) -> Unit = {},
    onSpeedTestWithCrcChange: (Boolean) -> Unit = {},
    onSpeedTestTargetBytesChange: (Long) -> Unit = {},
    onSendPayloadSizeChange: (Int) -> Unit = {}
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
                    // 发起方固定为发送方，不需要模式切换
                    Text(
                        if (session.speedTestRunning) "测速中" else "已停止",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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

            item { HorizontalDivider() }

            item {
                val selected = session.device
                ListItem(
                    headlineContent = { Text(selected.name) },
                    supportingContent = {
                        Text(
                            selected.address.ifBlank { selected.uuid },
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

            // ── 校验模式开关 ──
            item {
                ListItem(
                    headlineContent = { Text("校验模式") },
                    supportingContent = {
                        Text(
                            if (session.speedTestWithCrc) "CRC-16 校验（含序号检查）" else "无校验（最大吞吐）"
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = session.speedTestWithCrc,
                            onCheckedChange = onSpeedTestWithCrcChange,
                            enabled = !session.speedTestRunning
                        )
                    }
                )
            }

            // ── 数据量输入框（始终显示） ──
            item {
                var selectedUnit by remember { mutableStateOf(if (session.speedTestTargetBytes >= 1_048_576) "MB" else "KB") }
                var textValue by remember {
                    val displayValue = if (selectedUnit == "MB") {
                        session.speedTestTargetBytes / 1_048_576
                    } else {
                        session.speedTestTargetBytes / 1024
                    }
                    mutableStateOf(displayValue.toString())
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { newText ->
                            textValue = newText
                            val num = newText.toLongOrNull()
                            if (num != null && num > 0) {
                                val bytes = if (selectedUnit == "MB") num * 1_048_576 else num * 1024
                                onSpeedTestTargetBytesChange(bytes)
                            }
                        },
                        label = { Text("数据总量") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !session.speedTestRunning,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedUnit == "KB",
                            onClick = {
                                selectedUnit = "KB"
                                val num = textValue.toLongOrNull()
                                if (num != null && num > 0) {
                                    onSpeedTestTargetBytesChange(num * 1024)
                                }
                            },
                            label = { Text("KB") },
                            enabled = !session.speedTestRunning
                        )
                        FilterChip(
                            selected = selectedUnit == "MB",
                            onClick = {
                                selectedUnit = "MB"
                                val num = textValue.toLongOrNull()
                                if (num != null && num > 0) {
                                    onSpeedTestTargetBytesChange(num * 1_048_576)
                                }
                            },
                            label = { Text("MB") },
                            enabled = !session.speedTestRunning
                        )
                    }
                }
            }

            // ── sendPayloadSize 输入框 ──
            item {
                var payloadSizeText by remember { mutableStateOf(session.sendPayloadSize.toString()) }

                OutlinedTextField(
                    value = payloadSizeText,
                    onValueChange = { newText ->
                        payloadSizeText = newText
                        val num = newText.toIntOrNull()
                        if (num != null && num in 1..32768) {
                            onSendPayloadSizeChange(num)
                        }
                    },
                    label = { Text("发送Payload大小（字节）") },
                    supportingText = { Text("范围 1~32768，默认 512") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !session.speedTestRunning,
                    isError = payloadSizeText.toIntOrNull()?.let { it !in 1..32768 } ?: payloadSizeText.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
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

            // 只显示有数据的方向：发起方显示 TX，被动接收方显示 RX
            item {
                val hasTx = session.speedTestTxTotalBytes > 0 || session.speedTestTxAvgBps != null
                val hasRx = session.speedTestRxTotalBytes > 0 || session.speedTestRxAvgBps != null
                if (hasTx) {
                    SpeedSummary(
                        title = "吞吐",
                        avgBps = session.speedTestTxAvgBps,
                        totalBytes = session.speedTestTxTotalBytes
                    )
                } else if (hasRx) {
                    SpeedSummary(
                        title = "吞吐",
                        avgBps = session.speedTestRxAvgBps,
                        totalBytes = session.speedTestRxTotalBytes
                    )
                } else {
                    SpeedSummary(
                        title = "吞吐",
                        avgBps = null,
                        totalBytes = 0
                    )
                }
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

            item { HorizontalDivider() }

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
                    // 只显示有数据的方向
                    val avgBps = if (s.txTotalBytes > 0) s.txAvgBps else s.rxAvgBps
                    ListItem(
                        headlineContent = {
                            Text(
                                "t=${formatElapsedMs(s.elapsedMs)} · avg ${formatBps(avgBps)}",
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showPayloadDialog) {
        @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
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
    avgBps: Double?,
    totalBytes: Long,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "速率: ${avgBps?.let(::formatBps) ?: "--"}",
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
