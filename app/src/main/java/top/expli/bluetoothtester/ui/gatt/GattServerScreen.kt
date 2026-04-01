package top.expli.bluetoothtester.ui.gatt

import android.bluetooth.BluetoothGattCharacteristic
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import top.expli.bluetoothtester.model.GattCharacteristicConfig
import top.expli.bluetoothtester.model.GattServerLogEntry
import top.expli.bluetoothtester.model.GattServerState
import top.expli.bluetoothtester.model.GattServerViewModel
import top.expli.bluetoothtester.model.GattServiceConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GattServerScreen(
    onBackClick: () -> Unit,
    viewModel: GattServerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val isRunning = uiState.serverState is GattServerState.Running

    // ─── Service config form state ───
    var serviceUuid by rememberSaveable { mutableStateOf("0000180d-0000-1000-8000-00805f9b34fb") }
    var charUuid by rememberSaveable { mutableStateOf("00002a37-0000-1000-8000-00805f9b34fb") }
    var propRead by rememberSaveable { mutableStateOf(true) }
    var propWrite by rememberSaveable { mutableStateOf(true) }
    var propNotify by rememberSaveable { mutableStateOf(false) }
    var propIndicate by rememberSaveable { mutableStateOf(false) }

    // ─── Notification form state ───
    var notifyServiceUuid by rememberSaveable { mutableStateOf("") }
    var notifyCharUuid by rememberSaveable { mutableStateOf("") }
    var notifyDeviceAddress by rememberSaveable { mutableStateOf("") }
    var notifyValueHex by rememberSaveable { mutableStateOf("") }

    val surfaceColor = MaterialTheme.colorScheme.surface
    Surface(color = surfaceColor) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "GATT 服务端",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ─── Server State + Start/Stop ───
                item {
                    ServerStateSection(
                        serverState = uiState.serverState,
                        isRunning = isRunning,
                        onOpenServer = {
                            @SuppressWarnings("MissingPermission")
                            viewModel.openServer()
                        },
                        onCloseServer = {
                            @SuppressWarnings("MissingPermission")
                            viewModel.closeServer()
                        }
                    )
                }

                // ─── Error Display ───
                if (uiState.lastError != null) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Text(
                                text = uiState.lastError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                // ─── Service Configuration Form ───
                if (isRunning) {
                    item {
                        ServiceConfigForm(
                            serviceUuid = serviceUuid,
                            onServiceUuidChange = { serviceUuid = it },
                            charUuid = charUuid,
                            onCharUuidChange = { charUuid = it },
                            propRead = propRead,
                            onPropReadChange = { propRead = it },
                            propWrite = propWrite,
                            onPropWriteChange = { propWrite = it },
                            propNotify = propNotify,
                            onPropNotifyChange = { propNotify = it },
                            propIndicate = propIndicate,
                            onPropIndicateChange = { propIndicate = it },
                            onAddService = {
                                var properties = 0
                                var permissions = 0
                                if (propRead) {
                                    properties = properties or BluetoothGattCharacteristic.PROPERTY_READ
                                    permissions = permissions or BluetoothGattCharacteristic.PERMISSION_READ
                                }
                                if (propWrite) {
                                    properties = properties or BluetoothGattCharacteristic.PROPERTY_WRITE
                                    permissions = permissions or BluetoothGattCharacteristic.PERMISSION_WRITE
                                }
                                if (propNotify) {
                                    properties = properties or BluetoothGattCharacteristic.PROPERTY_NOTIFY
                                }
                                if (propIndicate) {
                                    properties = properties or BluetoothGattCharacteristic.PROPERTY_INDICATE
                                }
                                val config = GattServiceConfig(
                                    serviceUuid = serviceUuid,
                                    characteristics = listOf(
                                        GattCharacteristicConfig(
                                            uuid = charUuid,
                                            properties = properties,
                                            permissions = permissions
                                        )
                                    )
                                )
                                @SuppressWarnings("MissingPermission")
                                viewModel.addService(config)
                            }
                        )
                    }
                }

                // ─── Active Services List ───
                if (uiState.activeServices.isNotEmpty()) {
                    item {
                        Text(
                            text = "已注册服务 (${uiState.activeServices.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    items(
                        items = uiState.activeServices,
                        key = { it.serviceUuid }
                    ) { service ->
                        ActiveServiceCard(
                            service = service,
                            onRemove = {
                                @SuppressWarnings("MissingPermission")
                                viewModel.removeService(service.serviceUuid)
                            }
                        )
                    }
                }

                // ─── Connected Devices ───
                if (uiState.connectedDevices.isNotEmpty()) {
                    item {
                        Text(
                            text = "已连接设备 (${uiState.connectedDevices.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    items(
                        items = uiState.connectedDevices,
                        key = { it }
                    ) { address ->
                        ConnectedDeviceItem(address = address)
                    }
                }

                // ─── Notification Sender ───
                if (isRunning && uiState.connectedDevices.isNotEmpty()) {
                    item {
                        NotificationSenderSection(
                            serviceUuid = notifyServiceUuid,
                            onServiceUuidChange = { notifyServiceUuid = it },
                            charUuid = notifyCharUuid,
                            onCharUuidChange = { notifyCharUuid = it },
                            deviceAddress = notifyDeviceAddress,
                            onDeviceAddressChange = { notifyDeviceAddress = it },
                            valueHex = notifyValueHex,
                            onValueHexChange = { notifyValueHex = it },
                            connectedDevices = uiState.connectedDevices,
                            onUpdateValue = {
                                val bytes = hexToBytes(notifyValueHex) ?: return@NotificationSenderSection
                                viewModel.updateCharacteristicValue(
                                    notifyServiceUuid, notifyCharUuid, bytes
                                )
                            },
                            onSendNotification = {
                                val bytes = hexToBytes(notifyValueHex) ?: return@NotificationSenderSection
                                @SuppressWarnings("MissingPermission")
                                viewModel.sendNotification(
                                    notifyServiceUuid, notifyCharUuid, notifyDeviceAddress, bytes
                                )
                            }
                        )
                    }
                }

                // ─── Request Log ───
                if (uiState.log.isNotEmpty()) {
                    item {
                        Text(
                            text = "请求日志 (${uiState.log.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    items(
                        items = uiState.log.reversed().take(50),
                        key = { it.id }
                    ) { entry ->
                        ServerLogEntryItem(entry = entry)
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

// ─── Server State Section ───

@Composable
private fun ServerStateSection(
    serverState: GattServerState,
    isRunning: Boolean,
    onOpenServer: () -> Unit,
    onCloseServer: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ServerStateChip(state = serverState)

                if (isRunning) {
                    OutlinedButton(onClick = onCloseServer) {
                        Text("停止服务")
                    }
                } else {
                    Button(onClick = onOpenServer) {
                        Text("启动服务")
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerStateChip(state: GattServerState) {
    val (label, color) = when (state) {
        is GattServerState.Idle -> "未启动" to MaterialTheme.colorScheme.outline
        is GattServerState.Running -> "运行中" to MaterialTheme.colorScheme.primary
        is GattServerState.Error -> "错误" to MaterialTheme.colorScheme.error
    }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

// ─── Service Configuration Form ───

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServiceConfigForm(
    serviceUuid: String,
    onServiceUuidChange: (String) -> Unit,
    charUuid: String,
    onCharUuidChange: (String) -> Unit,
    propRead: Boolean,
    onPropReadChange: (Boolean) -> Unit,
    propWrite: Boolean,
    onPropWriteChange: (Boolean) -> Unit,
    propNotify: Boolean,
    onPropNotifyChange: (Boolean) -> Unit,
    propIndicate: Boolean,
    onPropIndicateChange: (Boolean) -> Unit,
    onAddService: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "添加服务",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )

            OutlinedTextField(
                value = serviceUuid,
                onValueChange = onServiceUuidChange,
                label = { Text("服务 UUID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = charUuid,
                onValueChange = onCharUuidChange,
                label = { Text("特征值 UUID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text(
                text = "属性",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                CheckboxItem(label = "Read", checked = propRead, onCheckedChange = onPropReadChange)
                CheckboxItem(label = "Write", checked = propWrite, onCheckedChange = onPropWriteChange)
                CheckboxItem(label = "Notify", checked = propNotify, onCheckedChange = onPropNotifyChange)
                CheckboxItem(label = "Indicate", checked = propIndicate, onCheckedChange = onPropIndicateChange)
            }

            Button(
                onClick = onAddService,
                modifier = Modifier.fillMaxWidth(),
                enabled = serviceUuid.isNotBlank() && charUuid.isNotBlank()
            ) {
                Text("添加服务")
            }
        }
    }
}

@Composable
private fun CheckboxItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(36.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.clickable { onCheckedChange(!checked) }
        )
    }
}

// ─── Active Service Card ───

@Composable
private fun ActiveServiceCard(
    service: GattServiceConfig,
    onRemove: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Service",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = service.serviceUuid,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${service.characteristics.size} 个特征值",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "移除服务",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    service.characteristics.forEach { char ->
                        ServerCharacteristicItem(characteristic = char)
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerCharacteristicItem(characteristic: GattCharacteristicConfig) {
    val props = characteristic.properties
    val canRead = (props and BluetoothGattCharacteristic.PROPERTY_READ) != 0
    val canWrite = (props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
    val canNotify = (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
    val canIndicate = (props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0

    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Characteristic",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = characteristic.uuid,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (canRead) ServerPropertyFlag("R")
            if (canWrite) ServerPropertyFlag("W")
            if (canNotify) ServerPropertyFlag("N")
            if (canIndicate) ServerPropertyFlag("I")
        }
    }
}

@Composable
private fun ServerPropertyFlag(label: String) {
    Card(
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

// ─── Connected Device Item ───

@Composable
private fun ConnectedDeviceItem(address: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = address,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// ─── Notification Sender Section ───

@Composable
private fun NotificationSenderSection(
    serviceUuid: String,
    onServiceUuidChange: (String) -> Unit,
    charUuid: String,
    onCharUuidChange: (String) -> Unit,
    deviceAddress: String,
    onDeviceAddressChange: (String) -> Unit,
    valueHex: String,
    onValueHexChange: (String) -> Unit,
    connectedDevices: List<String>,
    onUpdateValue: () -> Unit,
    onSendNotification: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "特征值编辑 / 发送通知",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )

            OutlinedTextField(
                value = serviceUuid,
                onValueChange = onServiceUuidChange,
                label = { Text("服务 UUID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = charUuid,
                onValueChange = onCharUuidChange,
                label = { Text("特征值 UUID") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = deviceAddress,
                onValueChange = onDeviceAddressChange,
                label = { Text("目标设备地址") },
                placeholder = { Text(connectedDevices.firstOrNull() ?: "AA:BB:CC:DD:EE:FF") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            if (connectedDevices.size > 1) {
                Text(
                    text = "已连接: ${connectedDevices.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = valueHex,
                onValueChange = onValueHexChange,
                label = { Text("数据 (Hex)") },
                placeholder = { Text("0102AABB") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onUpdateValue,
                    modifier = Modifier.weight(1f),
                    enabled = serviceUuid.isNotBlank() && charUuid.isNotBlank() && valueHex.isNotBlank()
                ) {
                    Text("更新值")
                }
                Button(
                    onClick = onSendNotification,
                    modifier = Modifier.weight(1f),
                    enabled = serviceUuid.isNotBlank() && charUuid.isNotBlank()
                            && deviceAddress.isNotBlank() && valueHex.isNotBlank()
                ) {
                    Text("发送通知")
                }
            }
        }
    }
}

// ─── Server Log Entry Item ───

@Composable
private fun ServerLogEntryItem(entry: GattServerLogEntry) {
    val timeStr = remember(entry.timestamp) {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
        sdf.format(java.util.Date(entry.timestamp))
    }
    val eventColor = when (entry.eventType) {
        top.expli.bluetoothtester.model.GattServerEvent.DeviceConnected -> MaterialTheme.colorScheme.primary
        top.expli.bluetoothtester.model.GattServerEvent.DeviceDisconnected -> MaterialTheme.colorScheme.error
        top.expli.bluetoothtester.model.GattServerEvent.ReadRequest -> MaterialTheme.colorScheme.tertiary
        top.expli.bluetoothtester.model.GattServerEvent.WriteRequest -> MaterialTheme.colorScheme.secondary
        top.expli.bluetoothtester.model.GattServerEvent.NotificationSent -> MaterialTheme.colorScheme.primary
        top.expli.bluetoothtester.model.GattServerEvent.MtuChanged -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = entry.eventType.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = eventColor
                )
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "设备: ${entry.deviceAddress}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            entry.serviceUuid?.let { uuid ->
                Text(
                    text = "服务: $uuid",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            entry.characteristicUuid?.let { uuid ->
                Text(
                    text = "特征: $uuid",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            entry.data?.let { data ->
                Text(
                    text = "数据: $data",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─── Utility ───

private fun hexToBytes(hex: String): ByteArray? {
    val cleaned = hex.replace(" ", "").replace(":", "")
    if (cleaned.isEmpty() || cleaned.length % 2 != 0) return null
    return try {
        ByteArray(cleaned.length / 2) { i ->
            cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    } catch (_: NumberFormatException) {
        null
    }
}
