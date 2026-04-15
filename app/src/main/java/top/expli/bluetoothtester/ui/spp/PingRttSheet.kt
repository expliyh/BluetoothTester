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
fun PingRttSheet(
    session: SppSession,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onUpdateTargetCount: (Int) -> Unit,
    onUpdateInterval: (Long) -> Unit,
    onUpdateTimeout: (Long) -> Unit,
    onUpdatePaddingSize: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val running = session.pingTestRunning

    var targetCountText by remember(session.pingTestTargetCount) {
        mutableStateOf(session.pingTestTargetCount.toString())
    }
    var intervalText by remember(session.pingTestInterval) {
        mutableStateOf(session.pingTestInterval.toString())
    }
    var timeoutText by remember(session.pingTestTimeout) {
        mutableStateOf(session.pingTestTimeout.toString())
    }
    var paddingSizeText by remember(session.pingTestPaddingSize) {
        mutableStateOf(session.pingTestPaddingSize.toString())
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
                    Text("Ping RTT 测量", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (running) "运行中" else "已停止",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (running) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── 开始/停止 + 关闭 ──
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

            item { HorizontalDivider() }

            // ── 配置区域 ──
            item {
                Text("配置", style = MaterialTheme.typography.titleSmall)
            }

            // Ping 次数
            item {
                OutlinedTextField(
                    value = targetCountText,
                    onValueChange = { value ->
                        targetCountText = value
                        value.toIntOrNull()?.let { onUpdateTargetCount(it) }
                    },
                    label = { Text("Ping 次数") },
                    supportingText = { Text("0 = 持续 Ping") },
                    enabled = !running,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Ping 间隔
            item {
                OutlinedTextField(
                    value = intervalText,
                    onValueChange = { value ->
                        intervalText = value
                        value.toLongOrNull()?.let { onUpdateInterval(it) }
                    },
                    label = { Text("Ping 间隔") },
                    suffix = { Text("ms") },
                    enabled = !running,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 超时时间
            item {
                OutlinedTextField(
                    value = timeoutText,
                    onValueChange = { value ->
                        timeoutText = value
                        value.toLongOrNull()?.let { onUpdateTimeout(it) }
                    },
                    label = { Text("超时时间") },
                    suffix = { Text("ms") },
                    enabled = !running,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Payload 大小（额外 padding）
            item {
                OutlinedTextField(
                    value = paddingSizeText,
                    onValueChange = { value ->
                        paddingSizeText = value
                        value.toIntOrNull()?.let { onUpdatePaddingSize(it) }
                    },
                    label = { Text("额外 Padding 大小") },
                    suffix = { Text("字节") },
                    supportingText = { Text("最小 payload = 9 字节（type 1B + timestamp 8B）") },
                    enabled = !running,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ── 实时统计（运行中或有历史数据时显示） ──
            if (running || session.pingTestCount > 0) {
                item { HorizontalDivider() }

                item {
                    Text("统计", style = MaterialTheme.typography.titleSmall)
                }

                item {
                    PingStatistics(session)
                }
            }
        }
    }
}

@Composable
private fun PingStatistics(session: SppSession) {
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
            // 本次 RTT
            Text(
                "本次 RTT: ${session.pingTestLastRtt?.let { "%.1f ms".format(it) } ?: "--"}",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // 计数统计
            Text(
                "总次数: ${session.pingTestCount}",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "成功: ${session.pingTestSuccessCount}  ·  超时: ${session.pingTestTimeoutCount}",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // RTT 统计
            Text(
                "平均 RTT: ${if (session.pingTestSuccessCount > 0) "%.1f ms".format(session.pingTestAvgRtt) else "--"}",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "最大 RTT: ${if (session.pingTestSuccessCount > 0) "%.1f ms".format(session.pingTestMaxRtt) else "--"}",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "最小 RTT: ${
                    if (session.pingTestSuccessCount > 0 && session.pingTestMinRtt != Double.MAX_VALUE)
                        "%.1f ms".format(session.pingTestMinRtt)
                    else "--"
                }",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
