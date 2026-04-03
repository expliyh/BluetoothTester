package top.expli.bluetoothtester.ui.gatt

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothGattCharacteristic
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import top.expli.bluetoothtester.model.DeviceType
import top.expli.bluetoothtester.model.GattCharacteristicInfo
import top.expli.bluetoothtester.model.GattConnectionState
import top.expli.bluetoothtester.model.GattClientViewModel
import top.expli.bluetoothtester.model.GattLogEntry
import top.expli.bluetoothtester.model.GattServiceInfo
import top.expli.bluetoothtester.model.ScanViewModel
import top.expli.bluetoothtester.ui.common.BondedDeviceItem
import top.expli.bluetoothtester.ui.common.BondedDeviceLoadResult
import top.expli.bluetoothtester.ui.common.DevicePickerSheet
import top.expli.bluetoothtester.ui.common.loadBondedDeviceItems
import top.expli.bluetoothtester.ui.permissions.BluetoothPermissions
import top.expli.bluetoothtester.ui.permissions.openAppSettings
import java.util.concurrent.atomic.AtomicReference

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GattClientScreen(
    onBackClick: () -> Unit,
    initialAddress: String = "",
    initialName: String = "",
    viewModel: GattClientViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scanViewModel: ScanViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val scanUiState by scanViewModel.uiState.collectAsState()
    var targetAddress by rememberSaveable { mutableStateOf(initialAddress) }
    var mtuInput by rememberSaveable { mutableStateOf("512") }

    val context = LocalContext.current
    val activity = context as? Activity
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // ─── Device picker state ───
    var showDevicePicker by remember { mutableStateOf(false) }
    var bondedDevices by remember { mutableStateOf<List<BondedDeviceItem>>(emptyList()) }

    // ─── Permission state ───
    var showPermissionRationaleDialog by remember { mutableStateOf(false) }
    var showPermissionSettingsDialog by remember { mutableStateOf(false) }
    val pendingBluetoothAction = remember { AtomicReference<(() -> Unit)?>(null) }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.all { it }
        val pendingAction = pendingBluetoothAction.getAndSet(null)
        if (granted) {
            pendingAction?.invoke()
        } else {
            if (activity != null && !BluetoothPermissions.shouldShowRationale(activity)) {
                showPermissionSettingsDialog = true
            } else {
                scope.launch { snackbarHostState.showSnackbar("需要蓝牙权限才能继续") }
            }
        }
    }

    fun ensureBluetoothPermissions(action: () -> Unit) {
        if (BluetoothPermissions.hasAll(context)) {
            action()
            return
        }
        pendingBluetoothAction.set(action)
        if (activity != null && BluetoothPermissions.shouldShowRationale(activity)) {
            showPermissionRationaleDialog = true
        } else {
            bluetoothPermissionLauncher.launch(BluetoothPermissions.required)
        }
    }

    LaunchedEffect(initialAddress) {
        if (initialAddress.isNotBlank()) {
            viewModel.setTarget(initialAddress, initialName)
            targetAddress = initialAddress
        }
    }

    val isConnected = uiState.connectionState is GattConnectionState.Connected
    val isConnecting = uiState.connectionState is GattConnectionState.Connecting

    val surfaceColor = MaterialTheme.colorScheme.surface
    Surface(color = surfaceColor) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "GATT 客户端",
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
                // ─── Connection Section ───
                item {
                    ConnectionSection(
                        targetAddress = targetAddress,
                        onAddressChange = { targetAddress = it },
                        connectionState = uiState.connectionState,
                        isConnected = isConnected,
                        isConnecting = isConnecting,
                        onConnect = {
                            @SuppressWarnings("MissingPermission")
                            viewModel.connect(targetAddress, initialName)
                        },
                        onDisconnect = {
                            @SuppressWarnings("MissingPermission")
                            viewModel.disconnect()
                        },
                        onPickDevice = {
                            ensureBluetoothPermissions {
                                val result = runCatching { loadBondedDeviceItems(context) }.getOrElse {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            "读取已绑定设备失败: ${it.message ?: it::class.java.simpleName}"
                                        )
                                    }
                                    return@ensureBluetoothPermissions
                                }
                                when (result) {
                                    is BondedDeviceLoadResult.Success -> {
                                        bondedDevices = result.devices
                                        showDevicePicker = true
                                    }
                                    BondedDeviceLoadResult.BluetoothDisabled -> scope.launch {
                                        snackbarHostState.showSnackbar("蓝牙未开启，请先开启蓝牙")
                                    }
                                    BondedDeviceLoadResult.BluetoothUnavailable -> scope.launch {
                                        snackbarHostState.showSnackbar("本机蓝牙不可用")
                                    }
                                }
                            }
                        }
                    )
                }

                // ─── Error Display ───
                if (uiState.lastError != null) {
                    item {
                        ErrorCard(error = uiState.lastError!!)
                    }
                }

                // ─── MTU Section ───
                if (isConnected) {
                    item {
                        MtuSection(
                            mtuInput = mtuInput,
                            onMtuInputChange = { mtuInput = it },
                            currentMtu = uiState.currentMtu,
                            onRequestMtu = {
                                val mtu = mtuInput.toIntOrNull() ?: return@MtuSection
                                @SuppressWarnings("MissingPermission")
                                viewModel.requestMtu(mtu)
                            }
                        )
                    }
                }

                // ─── Service Tree ───
                if (uiState.services.isNotEmpty()) {
                    item {
                        Text(
                            text = "服务列表 (${uiState.services.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    items(
                        items = uiState.services,
                        key = { it.uuid }
                    ) { service ->
                        ServiceCard(
                            service = service,
                            viewModel = viewModel
                        )
                    }
                }

                // ─── Operation Log ───
                if (uiState.log.isNotEmpty()) {
                    item {
                        Text(
                            text = "操作日志 (${uiState.log.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    items(
                        items = uiState.log.reversed().take(50),
                        key = { it.id }
                    ) { entry ->
                        LogEntryItem(entry = entry)
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    // ─── Permission Rationale Dialog ───
    if (showPermissionRationaleDialog) {
        AlertDialog(
            onDismissRequest = {
                showPermissionRationaleDialog = false
                pendingBluetoothAction.set(null)
            },
            title = { Text("需要蓝牙权限") },
            text = { Text("GATT 连接需要授予「附近设备」相关的蓝牙连接与扫描权限。") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionRationaleDialog = false
                    bluetoothPermissionLauncher.launch(BluetoothPermissions.required)
                }) { Text("去授权") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPermissionRationaleDialog = false
                    pendingBluetoothAction.set(null)
                }) { Text("取消") }
            }
        )
    }

    // ─── Permission Settings Dialog ───
    if (showPermissionSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionSettingsDialog = false },
            title = { Text("权限被拒绝") },
            text = { Text("蓝牙权限可能已被永久拒绝，请前往系统设置手动开启。") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionSettingsDialog = false
                    openAppSettings(context)
                }) { Text("打开设置") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionSettingsDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // ─── Device Picker Sheet ───
    if (showDevicePicker) {
        DevicePickerSheet(
            bondedDevices = bondedDevices,
            scannedDevices = scanUiState.combinedDevices,
            showBonded = true,
            showScanned = true,
            deviceTypeFilter = setOf(DeviceType.BLE, DeviceType.Dual),
            onStartScan = {
                @SuppressWarnings("MissingPermission")
                scanViewModel.startBleScan()
            },
            onDismissRequest = {
                scanViewModel.stopBleScan()
                showDevicePicker = false
            },
            onSelect = { address, name ->
                scanViewModel.stopBleScan()
                targetAddress = address
                viewModel.setTarget(address, name ?: "")
                showDevicePicker = false
            }
        )
    }
}

// ─── Connection Section ───

@Composable
private fun ConnectionSection(
    targetAddress: String,
    onAddressChange: (String) -> Unit,
    connectionState: GattConnectionState,
    isConnected: Boolean,
    isConnecting: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onPickDevice: () -> Unit
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
            OutlinedTextField(
                value = targetAddress,
                onValueChange = onAddressChange,
                label = { Text("目标设备地址") },
                placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isConnected && !isConnecting,
                trailingIcon = {
                    IconButton(
                        onClick = onPickDevice,
                        enabled = !isConnected && !isConnecting
                    ) {
                        Icon(Icons.Default.Contacts, contentDescription = "选择设备")
                    }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ConnectionStateChip(state = connectionState)

                if (isConnected) {
                    OutlinedButton(onClick = onDisconnect) {
                        Text("断开连接")
                    }
                } else {
                    Button(
                        onClick = onConnect,
                        enabled = targetAddress.isNotBlank() && !isConnecting
                    ) {
                        Text(if (isConnecting) "连接中..." else "连接")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStateChip(state: GattConnectionState) {
    val (label, color) = when (state) {
        is GattConnectionState.Idle -> "未连接" to MaterialTheme.colorScheme.outline
        is GattConnectionState.Connecting -> "连接中" to MaterialTheme.colorScheme.tertiary
        is GattConnectionState.Connected -> "已连接" to MaterialTheme.colorScheme.primary
        is GattConnectionState.Disconnected -> "已断开" to MaterialTheme.colorScheme.error
        is GattConnectionState.Error -> "错误 (${state.status})" to MaterialTheme.colorScheme.error
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

// ─── Error Card ───

@Composable
private fun ErrorCard(error: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Text(
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp)
        )
    }
}

// ─── MTU Section ───

@Composable
private fun MtuSection(
    mtuInput: String,
    onMtuInputChange: (String) -> Unit,
    currentMtu: Int,
    onRequestMtu: () -> Unit
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
                text = "MTU 协商",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "当前 MTU: $currentMtu",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = mtuInput,
                    onValueChange = onMtuInputChange,
                    label = { Text("目标 MTU") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                FilledTonalButton(onClick = onRequestMtu) {
                    Text("协商 MTU")
                }
            }
        }
    }
}

// ─── Service Card (Expandable) ───

@Composable
private fun ServiceCard(
    service: GattServiceInfo,
    viewModel: GattClientViewModel
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
            // Service header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Service",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium
                        )
                        ServiceTypeBadge(isPrimary = service.isPrimary)
                    }
                    Text(
                        text = service.uuid,
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
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    modifier = Modifier.size(24.dp)
                )
            }

            // Characteristics (expanded)
            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    service.characteristics.forEach { char ->
                        CharacteristicItem(
                            serviceUuid = service.uuid,
                            characteristic = char,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceTypeBadge(isPrimary: Boolean) {
    val (label, color) = if (isPrimary) {
        "Primary" to MaterialTheme.colorScheme.primary
    } else {
        "Secondary" to MaterialTheme.colorScheme.tertiary
    }
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

// ─── Characteristic Item ───

@Composable
private fun CharacteristicItem(
    serviceUuid: String,
    characteristic: GattCharacteristicInfo,
    viewModel: GattClientViewModel
) {
    var writeData by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(false) }

    val props = characteristic.properties
    val canRead = (props and BluetoothGattCharacteristic.PROPERTY_READ) != 0
    val canWrite = (props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
    val canWriteNoResp = (props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
    val canNotify = (props and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
    val canIndicate = (props and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0

    Column(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Characteristic header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
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
                // Property flags
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (canRead) PropertyFlag("R")
                    if (canWrite) PropertyFlag("W")
                    if (canWriteNoResp) PropertyFlag("WNR")
                    if (canNotify) PropertyFlag("N")
                    if (canIndicate) PropertyFlag("I")
                }
                // Last read value
                characteristic.lastReadValue?.let { value ->
                    Text(
                        text = "值: $value",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Expanded actions
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Read button
                if (canRead) {
                    FilledTonalButton(
                        onClick = {
                            @SuppressWarnings("MissingPermission")
                            viewModel.readCharacteristic(serviceUuid, characteristic.uuid)
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("读取", style = MaterialTheme.typography.labelSmall)
                    }
                }

                // Write section
                if (canWrite || canWriteNoResp) {
                    OutlinedTextField(
                        value = writeData,
                        onValueChange = { writeData = it },
                        label = { Text("写入数据 (Hex)") },
                        placeholder = { Text("0102AABB") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (canWrite) {
                            FilledTonalButton(
                                onClick = {
                                    val bytes = hexToBytes(writeData) ?: return@FilledTonalButton
                                    @SuppressWarnings("MissingPermission")
                                    viewModel.writeCharacteristic(
                                        serviceUuid, characteristic.uuid, bytes,
                                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                                    )
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("写入", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (canWriteNoResp) {
                            OutlinedButton(
                                onClick = {
                                    val bytes = hexToBytes(writeData) ?: return@OutlinedButton
                                    @SuppressWarnings("MissingPermission")
                                    viewModel.writeCharacteristic(
                                        serviceUuid, characteristic.uuid, bytes,
                                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                                    )
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("无响应写入", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // Notify/Indicate toggle
                if (canNotify || canIndicate) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (canNotify) "Notify" else "Indicate",
                            style = MaterialTheme.typography.bodySmall
                        )
                        var enabled by remember { mutableStateOf(false) }
                        Switch(
                            checked = enabled,
                            onCheckedChange = { newValue ->
                                enabled = newValue
                                @SuppressWarnings("MissingPermission")
                                viewModel.setNotification(
                                    serviceUuid, characteristic.uuid, newValue
                                )
                            }
                        )
                    }
                }

                // Descriptors
                if (characteristic.descriptors.isNotEmpty()) {
                    Text(
                        text = "描述符 (${characteristic.descriptors.size})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    characteristic.descriptors.forEach { desc ->
                        Text(
                            text = desc.uuid,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PropertyFlag(label: String) {
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

// ─── Log Entry Item ───

@Composable
private fun LogEntryItem(entry: GattLogEntry) {
    val timeStr = remember(entry.timestamp) {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
        sdf.format(java.util.Date(entry.timestamp))
    }
    val statusColor = if (entry.success) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
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
                    text = entry.operation.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = statusColor
                )
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            entry.status?.let { status ->
                Text(
                    text = if (entry.success) "成功" else "失败 (status=$status)",
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }

            entry.dataHex?.let { hex ->
                Text(
                    text = "HEX: $hex",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            entry.dataUtf8?.let { utf8 ->
                Text(
                    text = "UTF-8: $utf8",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ─── Utility ───

private fun hexToBytes(hex: String): ByteArray? {
    val cleaned = hex.replace(" ", "").replace(":", "")
    if (cleaned.length % 2 != 0) return null
    return try {
        ByteArray(cleaned.length / 2) { i ->
            cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    } catch (_: NumberFormatException) {
        null
    }
}
