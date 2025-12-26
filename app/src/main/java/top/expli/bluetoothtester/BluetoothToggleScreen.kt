package top.expli.bluetoothtester

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.expli.bluetoothtester.shizuku.BluetoothState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothToggleScreen(
    state: BluetoothState,
    inProgress: Boolean,
    lastError: String?,
    loopRunning: Boolean,
    loopIterations: Int,
    loopCompleted: Int,
    loopOnDurationMs: Long,
    loopOffDurationMs: Long,
    onToggle: () -> Unit,
    onStartLoop: () -> Unit,
    onStopLoop: () -> Unit,
    onLoopIterationsChange: (Int) -> Unit,
    onLoopOnDurationChange: (Long) -> Unit,
    onLoopOffDurationChange: (Long) -> Unit,
    onBackClick: () -> Unit
) {

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("蓝牙开关", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            val on = state == BluetoothState.On || state == BluetoothState.TurningOn
            val icon = if (on) Icons.Default.Bluetooth else Icons.Default.BluetoothDisabled
            val statusText = when (state) {
                BluetoothState.On -> "已开启"
                BluetoothState.Off -> "已关闭"
                BluetoothState.TurningOn -> "正在开启…"
                BluetoothState.TurningOff -> "正在关闭…"
                BluetoothState.Unavailable -> "设备不支持或无适配器"
            }
            val statusColor = when (state) {
                BluetoothState.On -> MaterialTheme.colorScheme.primary
                BluetoothState.Off -> MaterialTheme.colorScheme.onSurfaceVariant
                BluetoothState.TurningOn, BluetoothState.TurningOff -> MaterialTheme.colorScheme.secondary
                BluetoothState.Unavailable -> MaterialTheme.colorScheme.error
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        if (inProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleLarge,
                        color = statusColor,
                        textAlign = TextAlign.Center
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = {},
                            label = { Text(if (on) "当前: 开" else "当前: 关") })
                        AssistChip(
                            onClick = {},
                            label = { Text(if (loopRunning) "循环中" else "单次模式") })
                    }

                    if (lastError != null) {
                        Text(
                            text = lastError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onToggle,
                            enabled = !inProgress && !loopRunning && state != BluetoothState.Unavailable
                        ) {
                            Text(if (on) "立即关闭" else "立即开启")
                        }
                        OutlinedButton(onClick = { onBackClick() }) {
                            Text("返回")
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "循环开关设置",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    NumberField(
                        label = "循环次数",
                        value = loopIterations,
                        suffix = "次",
                        onValueChange = { onLoopIterationsChange(it) }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            NumberField(
                                label = "开启保持",
                                value = (loopOnDurationMs / 1000).toInt(),
                                suffix = "秒",
                                onValueChange = { onLoopOnDurationChange(it.toLong() * 1000) }
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            NumberField(
                                label = "关闭保持",
                                value = (loopOffDurationMs / 1000).toInt(),
                                suffix = "秒",
                                onValueChange = { onLoopOffDurationChange(it.toLong() * 1000) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onStartLoop,
                            enabled = !loopRunning && !inProgress && loopIterations > 0 && state != BluetoothState.Unavailable
                        ) { Text("开始循环") }

                        OutlinedButton(
                            onClick = onStopLoop,
                            enabled = loopRunning
                        ) { Text("停止") }

                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            "已完成: $loopCompleted/${loopIterations.coerceAtLeast(1)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: Int,
    suffix: String,
    onValueChange: (Int) -> Unit
) {
    @Suppress("UNUSED_VALUE")
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            val parsed = it.toIntOrNull()
            if (parsed != null && parsed >= 0) onValueChange(parsed)
        },
        label = { Text(label) },
        trailingIcon = {
            Text(
                suffix,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}
