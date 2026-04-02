package top.expli.bluetoothtester.ui.advertiser

import android.bluetooth.le.AdvertiseSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import top.expli.bluetoothtester.model.AdvertiserState
import top.expli.bluetoothtester.model.AdvertiserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvertiserScreen(
    onBackClick: () -> Unit,
    viewModel: AdvertiserViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isAdvertising = uiState.state is AdvertiserState.Advertising

    // Local input state
    var serviceUuidInput by rememberSaveable { mutableStateOf("") }
    var manufacturerIdInput by rememberSaveable { mutableStateOf("") }
    var manufacturerDataInput by rememberSaveable { mutableStateOf("") }
    var scanRespUuidInput by rememberSaveable { mutableStateOf("") }
    var scanRespMfIdInput by rememberSaveable { mutableStateOf("") }
    var scanRespMfDataInput by rememberSaveable { mutableStateOf("") }

    val surfaceColor = MaterialTheme.colorScheme.surface
    Surface(color = surfaceColor) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "BLE 广播",
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
                // ─── Status & Duration ───
                item {
                    StatusCard(
                        state = uiState.state,
                        durationMs = uiState.durationMs
                    )
                }

                // ─── Error Display ───
                if (uiState.lastError != null) {
                    item {
                        ErrorCard(error = uiState.lastError!!)
                    }
                }

                // ─── Advertise Mode ───
                item {
                    ModeSelector(
                        currentMode = uiState.config.mode,
                        onModeChange = { viewModel.updateMode(it) },
                        enabled = !isAdvertising
                    )
                }

                // ─── TX Power ───
                item {
                    TxPowerSelector(
                        currentLevel = uiState.config.txPowerLevel,
                        onLevelChange = { viewModel.updateTxPowerLevel(it) },
                        enabled = !isAdvertising
                    )
                }

                // ─── Connectable Toggle ───
                item {
                    ToggleRow(
                        label = "可连接",
                        checked = uiState.config.connectable,
                        onCheckedChange = { viewModel.updateConnectable(it) },
                        enabled = !isAdvertising
                    )
                }

                // ─── Advertise Data Section ───
                item {
                    AdvertiseDataSection(
                        includeName = uiState.config.includeName,
                        onIncludeNameChange = { viewModel.updateIncludeName(it) },
                        serviceUuidInput = serviceUuidInput,
                        onServiceUuidInputChange = { serviceUuidInput = it },
                        serviceUuids = uiState.config.serviceUuids,
                        onAddServiceUuid = {
                            if (serviceUuidInput.isNotBlank()) {
                                viewModel.updateServiceUuids(
                                    uiState.config.serviceUuids + serviceUuidInput.trim()
                                )
                                serviceUuidInput = ""
                            }
                        },
                        onRemoveServiceUuid = { uuid ->
                            viewModel.updateServiceUuids(
                                uiState.config.serviceUuids.filter { it != uuid }
                            )
                        },
                        manufacturerIdInput = manufacturerIdInput,
                        onManufacturerIdChange = { manufacturerIdInput = it },
                        manufacturerDataInput = manufacturerDataInput,
                        onManufacturerDataChange = { manufacturerDataInput = it },
                        manufacturerData = uiState.config.manufacturerData,
                        onAddManufacturerData = {
                            val id = manufacturerIdInput.toIntOrNull()
                            if (id != null && manufacturerDataInput.isNotBlank()) {
                                viewModel.updateManufacturerData(
                                    uiState.config.manufacturerData + (id to manufacturerDataInput.trim())
                                )
                                manufacturerIdInput = ""
                                manufacturerDataInput = ""
                            }
                        },
                        onRemoveManufacturerData = { companyId ->
                            viewModel.updateManufacturerData(
                                uiState.config.manufacturerData.filter { it.key != companyId }
                            )
                        },
                        enabled = !isAdvertising
                    )
                }

                // ─── Scan Response Section ───
                item {
                    ScanResponseSection(
                        scanRespUuidInput = scanRespUuidInput,
                        onScanRespUuidChange = { scanRespUuidInput = it },
                        scanResponseUuids = uiState.config.scanResponseServiceUuids,
                        onAddScanRespUuid = {
                            if (scanRespUuidInput.isNotBlank()) {
                                viewModel.updateScanResponseServiceUuids(
                                    uiState.config.scanResponseServiceUuids + scanRespUuidInput.trim()
                                )
                                scanRespUuidInput = ""
                            }
                        },
                        onRemoveScanRespUuid = { uuid ->
                            viewModel.updateScanResponseServiceUuids(
                                uiState.config.scanResponseServiceUuids.filter { it != uuid }
                            )
                        },
                        scanRespMfIdInput = scanRespMfIdInput,
                        onScanRespMfIdChange = { scanRespMfIdInput = it },
                        scanRespMfDataInput = scanRespMfDataInput,
                        onScanRespMfDataChange = { scanRespMfDataInput = it },
                        scanResponseMfData = uiState.config.scanResponseManufacturerData,
                        onAddScanRespMfData = {
                            val id = scanRespMfIdInput.toIntOrNull()
                            if (id != null && scanRespMfDataInput.isNotBlank()) {
                                viewModel.updateScanResponseManufacturerData(
                                    uiState.config.scanResponseManufacturerData + (id to scanRespMfDataInput.trim())
                                )
                                scanRespMfIdInput = ""
                                scanRespMfDataInput = ""
                            }
                        },
                        onRemoveScanRespMfData = { companyId ->
                            viewModel.updateScanResponseManufacturerData(
                                uiState.config.scanResponseManufacturerData.filter { it.key != companyId }
                            )
                        },
                        enabled = !isAdvertising
                    )
                }

                // ─── Start/Stop Button ───
                item {
                    if (isAdvertising) {
                        Button(
                            onClick = {
                                @SuppressWarnings("MissingPermission")
                                viewModel.stopAdvertising()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("停止广播")
                        }
                    } else {
                        FilledTonalButton(
                            onClick = {
                                @SuppressWarnings("MissingPermission")
                                viewModel.startAdvertising(uiState.config)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("开始广播")
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}


// ─── Status Card ───

@Composable
private fun StatusCard(state: AdvertiserState, durationMs: Long) {
    val (label, color) = when (state) {
        is AdvertiserState.Idle -> "未广播" to MaterialTheme.colorScheme.outline
        is AdvertiserState.Advertising -> "广播中" to MaterialTheme.colorScheme.primary
        is AdvertiserState.Error -> "错误" to MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "广播状态",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
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
            if (state is AdvertiserState.Advertising) {
                val seconds = durationMs / 1000
                val minutes = seconds / 60
                val secs = seconds % 60
                Text(
                    text = "%02d:%02d".format(minutes, secs),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
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

// ─── Mode Selector ───

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeSelector(
    currentMode: Int,
    onModeChange: (Int) -> Unit,
    enabled: Boolean
) {
    val modes = listOf(
        AdvertiseSettings.ADVERTISE_MODE_LOW_POWER to "低功耗",
        AdvertiseSettings.ADVERTISE_MODE_BALANCED to "平衡",
        AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY to "低延迟"
    )

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
                text = "广播模式",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = currentMode == mode,
                        onClick = { onModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = modes.size
                        ),
                        enabled = enabled
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

// ─── TX Power Selector ───

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TxPowerSelector(
    currentLevel: Int,
    onLevelChange: (Int) -> Unit,
    enabled: Boolean
) {
    val levels = listOf(
        AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW to "极低",
        AdvertiseSettings.ADVERTISE_TX_POWER_LOW to "低",
        AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM to "中",
        AdvertiseSettings.ADVERTISE_TX_POWER_HIGH to "高"
    )

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
                text = "TX 功率",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                levels.forEachIndexed { index, (level, label) ->
                    SegmentedButton(
                        selected = currentLevel == level,
                        onClick = { onLevelChange(level) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = levels.size
                        ),
                        enabled = enabled
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

// ─── Toggle Row ───

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}


// ─── Advertise Data Section ───

@Composable
private fun AdvertiseDataSection(
    includeName: Boolean,
    onIncludeNameChange: (Boolean) -> Unit,
    serviceUuidInput: String,
    onServiceUuidInputChange: (String) -> Unit,
    serviceUuids: List<String>,
    onAddServiceUuid: () -> Unit,
    onRemoveServiceUuid: (String) -> Unit,
    manufacturerIdInput: String,
    onManufacturerIdChange: (String) -> Unit,
    manufacturerDataInput: String,
    onManufacturerDataChange: (String) -> Unit,
    manufacturerData: Map<Int, String>,
    onAddManufacturerData: () -> Unit,
    onRemoveManufacturerData: (Int) -> Unit,
    enabled: Boolean
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
                text = "广播数据",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )

            // Include device name toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "包含设备名称",
                    style = MaterialTheme.typography.bodySmall
                )
                Switch(
                    checked = includeName,
                    onCheckedChange = onIncludeNameChange,
                    enabled = enabled
                )
            }

            // Service UUID input
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = serviceUuidInput,
                    onValueChange = onServiceUuidInputChange,
                    label = { Text("服务 UUID") },
                    placeholder = { Text("0000180A-0000-1000-8000-00805F9B34FB") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = enabled
                )
                FilledTonalButton(
                    onClick = onAddServiceUuid,
                    enabled = enabled && serviceUuidInput.isNotBlank()
                ) {
                    Text("添加")
                }
            }

            // Service UUID list
            serviceUuids.forEach { uuid ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uuid,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { onRemoveServiceUuid(uuid) },
                        enabled = enabled,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("移除", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Manufacturer data input
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = manufacturerIdInput,
                    onValueChange = onManufacturerIdChange,
                    label = { Text("公司 ID") },
                    placeholder = { Text("76") },
                    modifier = Modifier.weight(0.3f),
                    singleLine = true,
                    enabled = enabled
                )
                OutlinedTextField(
                    value = manufacturerDataInput,
                    onValueChange = onManufacturerDataChange,
                    label = { Text("数据 (Hex)") },
                    placeholder = { Text("0102AABB") },
                    modifier = Modifier.weight(0.5f),
                    singleLine = true,
                    enabled = enabled
                )
                FilledTonalButton(
                    onClick = onAddManufacturerData,
                    enabled = enabled && manufacturerIdInput.isNotBlank() && manufacturerDataInput.isNotBlank()
                ) {
                    Text("添加")
                }
            }

            // Manufacturer data list
            manufacturerData.forEach { (companyId, hex) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ID=$companyId: $hex",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { onRemoveManufacturerData(companyId) },
                        enabled = enabled,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("移除", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

// ─── Scan Response Section ───

@Composable
private fun ScanResponseSection(
    scanRespUuidInput: String,
    onScanRespUuidChange: (String) -> Unit,
    scanResponseUuids: List<String>,
    onAddScanRespUuid: () -> Unit,
    onRemoveScanRespUuid: (String) -> Unit,
    scanRespMfIdInput: String,
    onScanRespMfIdChange: (String) -> Unit,
    scanRespMfDataInput: String,
    onScanRespMfDataChange: (String) -> Unit,
    scanResponseMfData: Map<Int, String>,
    onAddScanRespMfData: () -> Unit,
    onRemoveScanRespMfData: (Int) -> Unit,
    enabled: Boolean
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
                text = "扫描响应数据",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )

            // Scan response UUID input
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = scanRespUuidInput,
                    onValueChange = onScanRespUuidChange,
                    label = { Text("服务 UUID") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = enabled
                )
                FilledTonalButton(
                    onClick = onAddScanRespUuid,
                    enabled = enabled && scanRespUuidInput.isNotBlank()
                ) {
                    Text("添加")
                }
            }

            scanResponseUuids.forEach { uuid ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uuid,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { onRemoveScanRespUuid(uuid) },
                        enabled = enabled,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("移除", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Scan response manufacturer data
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = scanRespMfIdInput,
                    onValueChange = onScanRespMfIdChange,
                    label = { Text("公司 ID") },
                    modifier = Modifier.weight(0.3f),
                    singleLine = true,
                    enabled = enabled
                )
                OutlinedTextField(
                    value = scanRespMfDataInput,
                    onValueChange = onScanRespMfDataChange,
                    label = { Text("数据 (Hex)") },
                    modifier = Modifier.weight(0.5f),
                    singleLine = true,
                    enabled = enabled
                )
                FilledTonalButton(
                    onClick = onAddScanRespMfData,
                    enabled = enabled && scanRespMfIdInput.isNotBlank() && scanRespMfDataInput.isNotBlank()
                ) {
                    Text("添加")
                }
            }

            scanResponseMfData.forEach { (companyId, hex) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ID=$companyId: $hex",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { onRemoveScanRespMfData(companyId) },
                        enabled = enabled,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("移除", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
