package top.expli.bluetoothtester.ui.spp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import top.expli.bluetoothtester.model.PeriodicStopCondition
import top.expli.bluetoothtester.model.SppSession
import top.expli.bluetoothtester.util.formatBytes
import top.expli.bluetoothtester.util.formatElapsedMs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodicTestSheet(
    session: SppSession,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onUpdateInterval: (Long) -> Unit,
    onUpdateStopCondition: (PeriodicStopCondition) -> Unit,
    onUpdateSendPayloadSize: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val running = session.periodicTestRunning

    // 速率模式：true = 最大速率（interval=0），false = 固定间隔
    val isMaxRate = session.periodicTestInterval == 0L
    var intervalText by remember(session.periodicTestInterval) {
        mutableStateOf(
            if (session.periodicTestInterval > 0) session.periodicTestInterval.toString() else ""
        )
    }

    // 停止条件
    val isByCount = session.periodicTestStopCondition is PeriodicStopCondition.ByCount
    var countText by remember(session.periodicTestStopCondition) {
        mutableStateOf(
            when (val sc = session.periodicTestStopCondition) {
                is PeriodicStopCondition.ByCount -> sc.count.toString()
                else -> "100"
            }
        )
    }
    var timeText by remember(session.periodicTestStopCondition) {
        mutableStateOf(
            when (val sc = session.periodicTestStopCondition) {
                is PeriodicStopCondition.ByTime -> sc.seconds.toString()
                else -> "60"
            }
        )
    }

    // sendPayloadSize
    var payloadSizeText by remember(session.periodicTestSendPayloadSize) {
        mutableStateOf(session.periodicTestSendPayloadSize.toString())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 标题 + 运行状态 ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("定时发送", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (running) "运行中" else "已停止",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (running) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── 发送速率模式 ──
            item {
                Text("发送速率", style = MaterialTheme.typography.titleSmall)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    FilterChip(
                        selected = isMaxRate,
                        onClick = {
                            if (!running) onUpdateInterval(0L)
                        },
                        enabled = !running,
                        label = { Text("最大速率") }
                    )
                    FilterChip(
                        selected = !isMaxRate,
                        onClick = {
                            if (!running) {
                                val interval = intervalText.toLongOrNull()?.coerceAtLeast(10L) ?: 100L
                                onUpdateInterval(interval)
                            }
                        },
                        enabled = !running,
                        label = { Text("固定间隔") }
                    )
                }
            }

            // ── 固定间隔输入框 ──
            if (!isMaxRate) {
                item {
                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = { newVal ->
                            intervalText = newVal
                            val parsed = newVal.toLongOrNull()
                            if (parsed != null && parsed >= 10) {
                                onUpdateInterval(parsed)
                            }
                        },
                        label = { Text("间隔（毫秒）") },
                        supportingText = { Text("最小 10ms") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !running,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item { HorizontalDivider() }

            // ── 停止条件 ──
            item {
                Text("停止条件", style = MaterialTheme.typography.titleSmall)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    FilterChip(
                        selected = isByCount,
                        onClick = {
                            if (!running) {
                                val count = countText.toLongOrNull()?.coerceAtLeast(1) ?: 100
                                onUpdateStopCondition(PeriodicStopCondition.ByCount(count))
                            }
                        },
                        enabled = !running,
                        label = { Text("按次数") }
                    )
                    FilterChip(
                        selected = !isByCount,
                        onClick = {
                            if (!running) {
                                val seconds = timeText.toLongOrNull()?.coerceAtLeast(1) ?: 60
                                onUpdateStopCondition(PeriodicStopCondition.ByTime(seconds))
                            }
                        },
                        enabled = !running,
                        label = { Text("按时间") }
                    )
                }
            }

            // ── 按次数输入框 ──
            if (isByCount) {
                item {
                    OutlinedTextField(
                        value = countText,
                        onValueChange = { newVal ->
                            countText = newVal
                            val parsed = newVal.toLongOrNull()
                            if (parsed != null && parsed >= 1) {
                                onUpdateStopCondition(PeriodicStopCondition.ByCount(parsed))
                            }
                        },
                        label = { Text("发送次数") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !running,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ── 按时间输入框 ──
            if (!isByCount) {
                item {
                    OutlinedTextField(
                        value = timeText,
                        onValueChange = { newVal ->
                            timeText = newVal
                            val parsed = newVal.toLongOrNull()
                            if (parsed != null && parsed >= 1) {
                                onUpdateStopCondition(PeriodicStopCondition.ByTime(parsed))
                            }
                        },
                        label = { Text("持续时间（秒）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = !running,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item { HorizontalDivider() }

            // ── sendPayloadSize 输入框 ──
            item {
                OutlinedTextField(
                    value = payloadSizeText,
                    onValueChange = { newVal ->
                        payloadSizeText = newVal
                        val parsed = newVal.toIntOrNull()
                        if (parsed != null && parsed in 1..32768) {
                            onUpdateSendPayloadSize(parsed)
                        }
                    },
                    label = { Text("Payload 大小（字节）") },
                    supportingText = { Text("范围 1~32768，默认 512") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !running,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── 开始/停止按钮 ──
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { if (running) onStop() else onStart() }
                    ) {
                        Text(if (running) "停止" else "开始")
                    }
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
            }

            // ── 实时统计（运行中显示） ──
            if (running || session.periodicTestSentCount > 0) {
                item { HorizontalDivider() }

                item {
                    Text("实时统计", style = MaterialTheme.typography.titleSmall)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StatRow("已发送包数", "${session.periodicTestSentCount}")
                            StatRow("已发送字节", formatBytes(session.periodicTestSentBytes))
                            StatRow("成功次数", "${session.periodicTestSuccessCount}")
                            StatRow("失败次数", "${session.periodicTestFailCount}")
                            StatRow("已用时间", formatElapsedMs(session.periodicTestElapsedMs))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace
        )
    }
}
