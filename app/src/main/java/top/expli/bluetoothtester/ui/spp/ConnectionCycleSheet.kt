package top.expli.bluetoothtester.ui.spp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import top.expli.bluetoothtester.model.SppSession

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionCycleSheet(
    session: SppSession,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onUpdateTargetCount: (Int) -> Unit,
    onUpdateInterval: (Long) -> Unit,
    onUpdateTimeout: (Long) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val running = session.connectionCycleRunning

    var targetCountText by remember { mutableStateOf(session.connectionCycleTargetCount.toString()) }
    var intervalText by remember { mutableStateOf(session.connectionCycleInterval.toString()) }
    var timeoutText by remember { mutableStateOf(session.connectionCycleTimeout.toString()) }

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
                    Text("连接循环压测", style = MaterialTheme.typography.titleMedium)
                    AssistChip(
                        onClick = {},
                        label = {
                            Text(if (running) "运行中" else "已停止")
                        }
                    )
                }
            }

            // ── 循环次数 ──
            item {
                OutlinedTextField(
                    value = targetCountText,
                    onValueChange = { value ->
                        targetCountText = value
                        value.toIntOrNull()?.let { onUpdateTargetCount(it) }
                    },
                    label = { Text("循环次数") },
                    supportingText = { Text("0 = 无限循环") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── 断开后等待间隔 ──
            item {
                OutlinedTextField(
                    value = intervalText,
                    onValueChange = { value ->
                        intervalText = value
                        value.toLongOrNull()?.let { onUpdateInterval(it) }
                    },
                    label = { Text("断开后等待间隔") },
                    suffix = { Text("ms") },
                    supportingText = { Text("默认 1000ms") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !running,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── 单次连接超时 ──
            item {
                OutlinedTextField(
                    value = timeoutText,
                    onValueChange = { value ->
                        timeoutText = value
                        value.toLongOrNull()?.let { onUpdateTimeout(it) }
                    },
                    label = { Text("单次连接超时") },
                    suffix = { Text("ms") },
                    supportingText = { Text("默认 10000ms") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !running,
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

            // ── 实时统计（运行中或有数据时显示） ──
            if (running || session.connectionCycleTotalCount > 0) {
                item { HorizontalDivider() }

                item {
                    Text("实时统计", style = MaterialTheme.typography.titleSmall)
                }

                item {
                    CycleStatistics(session)
                }
            }
        }
    }
}

@Composable
private fun CycleStatistics(session: SppSession) {
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
            StatRow("总次数", "${session.connectionCycleTotalCount}")
            StatRow("成功", "${session.connectionCycleSuccessCount}")
            StatRow("失败", "${session.connectionCycleFailCount}")
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            StatRow("平均耗时", formatMs(session.connectionCycleAvgMs))
            StatRow("最大耗时", if (session.connectionCycleMaxMs == 0L) "--" else "${session.connectionCycleMaxMs}ms")
            StatRow(
                "最小耗时",
                if (session.connectionCycleMinMs == Long.MAX_VALUE) "--" else "${session.connectionCycleMinMs}ms"
            )
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

private fun formatMs(ms: Double): String {
    if (ms <= 0.0) return "--"
    return "%.1fms".format(ms)
}
