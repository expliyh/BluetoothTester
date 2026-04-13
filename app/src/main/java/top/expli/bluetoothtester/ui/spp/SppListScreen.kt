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
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.expli.bluetoothtester.bluetooth.UuidHelper
import top.expli.bluetoothtester.model.SppDevice
import top.expli.bluetoothtester.model.SppRole
import top.expli.bluetoothtester.model.SppUiState

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
                                    Box(modifier = Modifier.weight(1f))

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
fun AddSppDeviceDialog(
    onDismiss: () -> Unit,
    onConfirm: (SppDevice) -> Unit,
    onPickBondedDevice: ((onPicked: (String, String?) -> Unit) -> Unit) = {}
) {
    var name by remember { mutableStateOf("") }
    var addr by remember { mutableStateOf("") }
    var uuid by remember { mutableStateOf(UuidHelper.toShortForm(SppDevice.DEFAULT_SPP_UUID)) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var uuidError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("注册 SPP Socket") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称（可选）") })
                OutlinedTextField(
                    value = uuid,
                    onValueChange = { newValue ->
                        uuid = newValue
                        val result = UuidHelper.parse(newValue)
                        uuidError = when (result) {
                            is UuidHelper.UuidParseResult.Invalid -> result.reason
                            is UuidHelper.UuidParseResult.Valid -> null
                        }
                    },
                    label = { Text("UUID") },
                    placeholder = { Text("1101") },
                    singleLine = true,
                    isError = uuidError != null,
                    supportingText = if (uuidError != null) {
                        {
                            Text(
                                text = uuidError!!,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else null
                )
                OutlinedTextField(
                    value = addr,
                    onValueChange = { addr = it },
                    label = { Text("地址（必填）") },
                    trailingIcon = {
                        IconButton(onClick = {
                            onPickBondedDevice { pickedAddr, pickedName ->
                                addr = pickedAddr
                                if (name.isBlank() && pickedName != null && pickedName.isNotBlank()) {
                                    name = pickedName
                                }
                            }
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.BluetoothSearching,
                                contentDescription = "从已绑定设备选择"
                            )
                        }
                    }
                )
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
            @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
            TextButton(
                enabled = uuidError == null && uuid.isNotBlank(),
                onClick = {
                    val parseResult = UuidHelper.parse(uuid)
                    if (parseResult is UuidHelper.UuidParseResult.Invalid) {
                        uuidError = parseResult.reason; return@TextButton
                    }
                    val fullUuid = (parseResult as UuidHelper.UuidParseResult.Valid).uuid.toString()
                    if (addr.isBlank()) {
                        error = "客户端必须填写地址"; return@TextButton
                    }
                    error = null
                    val finalName = name.ifBlank { addr }
                    val device = SppDevice(
                        name = finalName,
                        address = addr,
                        uuid = fullUuid,
                        role = SppRole.Client,
                        note = note.ifBlank { null }
                    )
                    onConfirm(device)
                }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

fun SppDevice.uniqueKey(): String = address.ifBlank { uuid }
