package top.expli.bluetoothtester.ui.scan

import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import top.expli.bluetoothtester.model.BleScanState
import top.expli.bluetoothtester.model.ClassicScanState
import top.expli.bluetoothtester.model.DeviceType
import top.expli.bluetoothtester.model.ScanViewModel
import top.expli.bluetoothtester.model.UnifiedDeviceResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    onBackClick: () -> Unit,
    viewModel: ScanViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var filterExpanded by remember { mutableStateOf(false) }
    var nameKeyword by remember { mutableStateOf("") }
    var serviceUuid by remember { mutableStateOf("") }
    var rssiThreshold by remember { mutableFloatStateOf(-100f) }

    val bleScanning = uiState.bleScanState is BleScanState.Scanning
    val classicScanning = uiState.classicScanState is ClassicScanState.Scanning

    val bleError = (uiState.bleScanState as? BleScanState.Error)?.message
    val classicError = (uiState.classicScanState as? ClassicScanState.Error)?.message
    val errorMessage = bleError ?: classicError

    // Runtime permission handling
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            pendingAction?.invoke()
        }
        pendingAction = null
    }

    fun withBluetoothPermissions(action: () -> Unit) {
        val context = viewModel.getApplication<android.app.Application>()
        val scanGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.BLUETOOTH_SCAN
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val connectGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.BLUETOOTH_CONNECT
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val locationGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (scanGranted && connectGranted && locationGranted) {
            action()
        } else {
            pendingAction = action
            permissionLauncher.launch(
                arrayOf(
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        }
    }

    val surfaceColor = MaterialTheme.colorScheme.surface
    Surface(color = surfaceColor) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "扫描设备",
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Scan toggle buttons
                ScanControlRow(
                    bleScanning = bleScanning,
                    classicScanning = classicScanning,
                    onToggleBle = {
                        if (bleScanning) {
                            @SuppressWarnings("MissingPermission")
                            viewModel.stopBleScan()
                        } else {
                            withBluetoothPermissions {
                                @SuppressWarnings("MissingPermission")
                                viewModel.startBleScan()
                            }
                        }
                    },
                    onToggleClassic = {
                        if (classicScanning) {
                            @SuppressWarnings("MissingPermission")
                            viewModel.stopClassicScan()
                        } else {
                            withBluetoothPermissions {
                                @SuppressWarnings("MissingPermission")
                                viewModel.startClassicScan()
                            }
                        }
                    }
                )

                // Filter section toggle
                TextButton(
                    onClick = { filterExpanded = !filterExpanded },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (filterExpanded) "收起过滤" else "展开过滤")
                }

                // Collapsible filter section
                AnimatedVisibility(visible = filterExpanded) {
                    FilterSection(
                        nameKeyword = nameKeyword,
                        onNameKeywordChange = { nameKeyword = it },
                        serviceUuid = serviceUuid,
                        onServiceUuidChange = { serviceUuid = it },
                        rssiThreshold = rssiThreshold,
                        onRssiThresholdChange = { rssiThreshold = it }
                    )
                }

                // Performance warning banner
                if (uiState.showClassicAffectsBleWarning) {
                    PerformanceWarningBanner()
                }

                // Error state
                if (errorMessage != null) {
                    ErrorBanner(
                        message = errorMessage,
                        onRetry = {
                            @SuppressWarnings("MissingPermission")
                            if (bleError != null) viewModel.startBleScan()
                            @SuppressWarnings("MissingPermission")
                            if (classicError != null) viewModel.startClassicScan()
                        }
                    )
                }

                // Device list or empty state
                if (uiState.combinedDevices.isEmpty()) {
                    EmptyState()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = uiState.combinedDevices,
                            key = { it.address }
                        ) { device ->
                            DeviceItem(device = device)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanControlRow(
    bleScanning: Boolean,
    classicScanning: Boolean,
    onToggleBle: () -> Unit,
    onToggleClassic: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (bleScanning) {
            Button(
                onClick = onToggleBle,
                modifier = Modifier.weight(1f)
            ) { Text("停止 BLE") }
        } else {
            FilledTonalButton(
                onClick = onToggleBle,
                modifier = Modifier.weight(1f)
            ) { Text("BLE 扫描") }
        }

        if (classicScanning) {
            Button(
                onClick = onToggleClassic,
                modifier = Modifier.weight(1f)
            ) { Text("停止经典") }
        } else {
            FilledTonalButton(
                onClick = onToggleClassic,
                modifier = Modifier.weight(1f)
            ) { Text("经典扫描") }
        }
    }
}

@Composable
private fun FilterSection(
    nameKeyword: String,
    onNameKeywordChange: (String) -> Unit,
    serviceUuid: String,
    onServiceUuidChange: (String) -> Unit,
    rssiThreshold: Float,
    onRssiThresholdChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = nameKeyword,
            onValueChange = onNameKeywordChange,
            label = { Text("设备名称关键字") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = serviceUuid,
            onValueChange = onServiceUuidChange,
            label = { Text("服务 UUID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Text(
            text = "RSSI 阈值: ${rssiThreshold.toInt()} dBm",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = rssiThreshold,
            onValueChange = onRssiThresholdChange,
            valueRange = -120f..-30f,
            steps = 17
        )
    }
}

@Composable
private fun PerformanceWarningBanner() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "经典蓝牙扫描可能影响 BLE 扫描性能",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            OutlinedButton(
                onClick = onRetry,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("重试", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "未发现设备",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "点击上方按钮开始扫描",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DeviceItem(device: UnifiedDeviceResult) {
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
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = device.name ?: "未知设备",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                DeviceTypeChip(deviceType = device.deviceType)
            }

            Text(
                text = device.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // BLE-specific info
            if (device.deviceType == DeviceType.BLE || device.deviceType == DeviceType.Dual) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    device.rssi?.let { rssi ->
                        Text(
                            text = "RSSI: $rssi dBm",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    device.bleData?.let { ble ->
                        if (ble.serviceUuids.isNotEmpty()) {
                            Text(
                                text = "UUID: ${ble.serviceUuids.size} 个",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            // Classic-specific info
            if (device.deviceType == DeviceType.Classic || device.deviceType == DeviceType.Dual) {
                device.classicData?.let { classic ->
                    Text(
                        text = "设备类: ${majorClassName(classic.majorDeviceClass)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceTypeChip(deviceType: DeviceType) {
    val (label, color) = when (deviceType) {
        DeviceType.BLE -> "BLE" to MaterialTheme.colorScheme.primary
        DeviceType.Classic -> "Classic" to MaterialTheme.colorScheme.tertiary
        DeviceType.Dual -> "Dual" to MaterialTheme.colorScheme.secondary
    }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * Simple major device class name mapper for display purposes.
 */
private fun majorClassName(majorClass: Int): String {
    return when (majorClass) {
        0x0100 -> "计算机"
        0x0200 -> "手机"
        0x0300 -> "网络接入点"
        0x0400 -> "音频/视频"
        0x0500 -> "外设"
        0x0600 -> "成像设备"
        0x0700 -> "可穿戴设备"
        0x0800 -> "玩具"
        0x0900 -> "健康设备"
        else -> "未知 (0x${majorClass.toString(16)})"
    }
}
