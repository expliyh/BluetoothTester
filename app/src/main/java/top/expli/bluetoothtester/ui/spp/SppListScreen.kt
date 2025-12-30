package top.expli.bluetoothtester.ui.spp

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.expli.bluetoothtester.model.SppDevice
import top.expli.bluetoothtester.model.SppRole
import top.expli.bluetoothtester.model.SppUiState
import java.util.UUID

@Composable
fun SppListScreen(
    modifier: Modifier = Modifier,
    state: SppUiState,
    onOpenDetail: (SppDevice) -> Unit,
    onDelete: (SppDevice) -> Unit,
    onAdd: () -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "已注册 Socket",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            TextButton(onClick = onAdd) { Text("注册") }
        }

        if (state.registered.isEmpty()) {
            Text("暂无设备，点击右上角注册", style = MaterialTheme.typography.bodySmall)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.registered, key = { it.uniqueKey() }) { dev ->
                    val selected = state.selectedKey == dev.uniqueKey()

                    val containerColor by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        label = "sppCardContainerColor"
                    )
                    val secondaryTextColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenDetail(dev) },
                        colors = CardDefaults.cardColors(
                            containerColor = containerColor,
                            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        ),
                        shape = CardDefaults.shape,
                        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 1.dp else 0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(46.dp),
                                shape = CircleShape,
                                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                                tonalElevation = 2.dp
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = dev.role.icon(),
                                        contentDescription = null,
                                        tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = dev.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RoleChip(role = dev.role, selected = selected)
//                                    val serverText = when (dev.role) {
//                                        SppRole.Server -> "本机监听（等待连接）"
//                                        else -> null
//                                    }
//                                    if (serverText != null) {
//                                        Text(
//                                            text = serverText,
//                                            style = MaterialTheme.typography.labelSmall,
//                                            color = secondaryTextColor,
//                                            maxLines = 1,
//                                            overflow = TextOverflow.Ellipsis,
//                                            modifier = Modifier.weight(1f)
//                                        )
//                                    } else {
                                    Box(modifier = Modifier.weight(1f))
//                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        FilledTonalIconButton(
                                            onClick = { onOpenDetail(dev) }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "进入"
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDelete(dev) },
                                            colors = IconButtonDefaults.iconButtonColors(
                                                contentColor = MaterialTheme.colorScheme.error
                                            )
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "移除")
                                        }
                                    }
                                }

                                if (dev.role == SppRole.Client && dev.address.isNotBlank()) {
                                    Text(
                                        text = dev.address,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = secondaryTextColor,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Text(
                                    text = "UUID: ${dev.uuid}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = secondaryTextColor,
                                    fontFamily = FontFamily.Monospace
                                )

                                dev.note?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = secondaryTextColor,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleChip(role: SppRole, selected: Boolean) {
    val (container, labelColor) = when (role) {
        SppRole.Client -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        SppRole.Server -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
    AssistChip(
        onClick = {},
        label = { Text(role.label()) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = container,
            labelColor = labelColor
        )
    )
}

@Composable
fun AddSppDeviceDialog(
    onDismiss: () -> Unit,
    onConfirm: (SppDevice) -> Unit,
    onPickBondedDevice: ((onPicked: (BondedDeviceItem) -> Unit) -> Unit) = {}
) {
    var name by remember { mutableStateOf("") }
    var addr by remember { mutableStateOf("") }
    var uuid by remember { mutableStateOf(SppDevice.DEFAULT_SPP_UUID) }
    var role by remember { mutableStateOf(SppRole.Client) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("注册 SPP Socket") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("角色", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = role == SppRole.Client,
                        onClick = { role = SppRole.Client },
                        label = { Text("客户端") }
                    )
                    FilterChip(
                        selected = role == SppRole.Server,
                        onClick = { role = SppRole.Server },
                        label = { Text("服务端") }
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称（可选）") })
                OutlinedTextField(
                    value = uuid,
                    onValueChange = { uuid = it },
                    label = { Text("UUID") },
                    placeholder = { Text(SppDevice.DEFAULT_SPP_UUID) },
                    singleLine = true
                )
                if (role == SppRole.Client) {
                    OutlinedTextField(
                        value = addr,
                        onValueChange = { addr = it },
                        label = { Text("地址（必填）") },
                        trailingIcon = {
                            IconButton(onClick = {
                                onPickBondedDevice { picked ->
                                    addr = picked.address
                                    if (name.isBlank() && picked.name.isNotBlank()) {
                                        name = picked.name
                                    }
                                }
                            }) {
                                Icon(
                                    Icons.Default.BluetoothSearching,
                                    contentDescription = "从已绑定设备选择"
                                )
                            }
                        }
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注 可空") })
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (uuid.isBlank()) {
                    error = "UUID 不能为空"; return@TextButton
                }
                if (runCatching { UUID.fromString(uuid) }.isFailure) {
                    error = "UUID 格式不正确"; return@TextButton
                }
                if (role == SppRole.Client && addr.isBlank()) {
                    error = "客户端必须填写地址"; return@TextButton
                }
                error = null
                val finalName = name.ifBlank { if (role == SppRole.Client) addr else "SPP Server" }
                val device = SppDevice(
                    name = finalName,
                    address = if (role == SppRole.Client) addr else "",
                    uuid = uuid,
                    role = role,
                    note = note.ifBlank { null }
                )
                onConfirm(device)
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

fun SppDevice.uniqueKey(): String = if (address.isNotBlank()) address else uuid
