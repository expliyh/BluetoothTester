package top.expli.bluetoothtester.ui.common

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import top.expli.bluetoothtester.model.BleScanState
import top.expli.bluetoothtester.model.ClassicScanState
import top.expli.bluetoothtester.model.DeviceType
import top.expli.bluetoothtester.model.ScanMode
import top.expli.bluetoothtester.model.ScanViewModel
import top.expli.bluetoothtester.model.UnifiedDeviceResult

private enum class ScanSortOrder(val label: String) {
    RssiDesc("信号强度↓"),
    RssiAsc("信号强度↑"),
    NameAsc("名称 A→Z"),
    NameDesc("名称 Z→A"),
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicePickerSheet(
    defaultScanMode: ScanMode = ScanMode.Dual,
    deviceTypeFilter: Set<DeviceType>? = null,
    ensureBluetoothPermissions: (() -> Unit) -> Unit,
    onDismissRequest: () -> Unit,
    onSelect: (address: String, name: String?, transport: DeviceType) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scanViewModel: ScanViewModel = viewModel()
    val scanUiState by scanViewModel.uiState.collectAsState()

    // Load bonded devices on first composition
    var bondedDevices by remember { mutableStateOf<List<BondedDeviceItem>>(emptyList()) }
    LaunchedEffect(Unit) {
        val result = runCatching { loadBondedDeviceItems(context) }.getOrNull()
        bondedDevices = when (result) {
            is BondedDeviceLoadResult.Success -> result.devices
            else -> emptyList()
        }
    }

    // Stop scans when sheet is dismissed/disposed
    DisposableEffect(Unit) {
        onDispose {
            scanViewModel.stopAllScans()
        }
    }

    val scannedDevices = scanUiState.combinedDevices
    val isScanning = scanUiState.bleScanState is BleScanState.Scanning ||
            scanUiState.classicScanState is ClassicScanState.Scanning

    val filteredBonded = remember(bondedDevices, deviceTypeFilter) {
        if (deviceTypeFilter != null) {
            bondedDevices.filter { it.deviceType in deviceTypeFilter }
        } else {
            bondedDevices
        }
    }

    var scanMode by remember { mutableStateOf(defaultScanMode) }
    var namedOnly by remember { mutableStateOf(false) }
    var sortOrder by remember { mutableStateOf(ScanSortOrder.RssiDesc) }

    val filteredScanned = remember(scannedDevices, deviceTypeFilter, scanMode, namedOnly, sortOrder) {
        val scanModeTypes = when (scanMode) {
            ScanMode.BrOnly -> setOf(DeviceType.Classic, DeviceType.Dual)
            ScanMode.LeOnly -> setOf(DeviceType.BLE, DeviceType.Dual)
            ScanMode.Dual -> null // show all
        }
        scannedDevices.filter { device ->
            (deviceTypeFilter == null || device.deviceType in deviceTypeFilter) &&
                    (scanModeTypes == null || device.deviceType in scanModeTypes) &&
                    (!namedOnly || !device.name.isNullOrBlank())
        }.let { list ->
            when (sortOrder) {
                ScanSortOrder.RssiDesc -> list.sortedByDescending { it.rssi ?: Int.MIN_VALUE }
                ScanSortOrder.RssiAsc -> list.sortedBy { it.rssi ?: Int.MIN_VALUE }
                ScanSortOrder.NameAsc -> list.sortedBy { (it.name ?: it.address).lowercase() }
                ScanSortOrder.NameDesc -> list.sortedByDescending { (it.name ?: it.address).lowercase() }
            }
        }
    }

    // State for Dual device transport selection dialog
    var pendingDualDevice by remember { mutableStateOf<Pair<String, String?>?>(null) }

    // Resolve transport for a selected device based on current scan mode
    fun handleDeviceSelected(address: String, name: String?, deviceType: DeviceType) {
        val transport = when (scanMode) {
            ScanMode.BrOnly -> DeviceType.Classic
            ScanMode.LeOnly -> DeviceType.BLE
            ScanMode.Dual -> when (deviceType) {
                DeviceType.Dual -> {
                    // Need user to choose — show dialog
                    pendingDualDevice = address to name
                    return
                }
                else -> deviceType
            }
        }
        onSelect(address, name, transport)
        onDismissRequest()
    }

    // Dual transport selection dialog
    if (pendingDualDevice != null) {
        val (addr, devName) = pendingDualDevice!!
        AlertDialog(
            onDismissRequest = { pendingDualDevice = null },
            title = { Text("选择连接方式") },
            text = { Text("${devName ?: addr} 是双模设备，请选择连接方式：") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDualDevice = null
                    onSelect(addr, devName, DeviceType.BLE)
                    onDismissRequest()
                }) { Text("BLE") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingDualDevice = null
                    onSelect(addr, devName, DeviceType.Classic)
                    onDismissRequest()
                }) { Text("Classic") }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {
        var selectedTab by remember { mutableIntStateOf(0) }

        // Trigger scan when switching to scanned tab or changing scan mode
        LaunchedEffect(selectedTab, scanMode) {
            if (selectedTab == 1) {
                ensureBluetoothPermissions {
                    scanViewModel.startScan(scanMode)
                }
            }
        }

        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("已配对设备") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("扫描结果") }
            )
        }

        when (selectedTab) {
            0 -> BondedDeviceList(
                devices = filteredBonded,
                onSelect = { address, name, deviceType ->
                    handleDeviceSelected(address, name, deviceType)
                }
            )
            1 -> {
                ScanToolbar(
                    scanMode = scanMode,
                    onScanModeChange = { scanMode = it },
                    namedOnly = namedOnly,
                    onNamedOnlyChange = { namedOnly = it },
                    sortOrder = sortOrder,
                    onSortOrderChange = { sortOrder = it },
                    isScanning = isScanning,
                    onRescan = {
                        ensureBluetoothPermissions {
                            scanViewModel.startScan(scanMode)
                        }
                    }
                )
                ScannedDeviceList(
                    devices = filteredScanned,
                    sortOrder = sortOrder,
                    onSelect = { address, name, deviceType ->
                        handleDeviceSelected(address, name, deviceType)
                    }
                )
            }
        }
    }
}

@Composable
private fun ScanToolbar(
    scanMode: ScanMode,
    onScanModeChange: (ScanMode) -> Unit,
    namedOnly: Boolean,
    onNamedOnlyChange: (Boolean) -> Unit,
    sortOrder: ScanSortOrder,
    onSortOrderChange: (ScanSortOrder) -> Unit,
    isScanning: Boolean,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        // Row 1: scan mode + named-only filter
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = scanMode == ScanMode.BrOnly,
                onClick = { onScanModeChange(ScanMode.BrOnly) },
                label = { Text("BR Only") }
            )
            FilterChip(
                selected = scanMode == ScanMode.LeOnly,
                onClick = { onScanModeChange(ScanMode.LeOnly) },
                label = { Text("LE Only") }
            )
            FilterChip(
                selected = scanMode == ScanMode.Dual,
                onClick = { onScanModeChange(ScanMode.Dual) },
                label = { Text("Dual") }
            )
            FilterChip(
                selected = namedOnly,
                onClick = { onNamedOnlyChange(!namedOnly) },
                label = { Text("仅有名称") }
            )
        }
        // Row 2: sort + scan/loading button on the right
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                FilterChip(
                    selected = true,
                    onClick = { showSortMenu = true },
                    label = { Text(sortOrder.label) }
                )
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    ScanSortOrder.entries.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                onSortOrderChange(option)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            if (isScanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                IconButton(onClick = onRescan, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = "重新扫描")
                }
            }
        }
    }
}

@Composable
private fun BondedDeviceList(
    devices: List<BondedDeviceItem>,
    onSelect: (address: String, name: String?, deviceType: DeviceType) -> Unit
) {
    if (devices.isEmpty()) {
        Text(
            "未找到已绑定设备，请先在系统蓝牙设置中配对",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 520.dp)
        ) {
            items(
                items = devices,
                key = { it.address },
                contentType = { "bonded" }
            ) { dev ->
                ListItem(
                    headlineContent = {
                        Text(dev.name.ifBlank { dev.address })
                    },
                    supportingContent = {
                        Text(dev.address, fontFamily = FontFamily.Monospace)
                    },
                    modifier = Modifier.clickable {
                        onSelect(dev.address, dev.name.ifBlank { null }, dev.deviceType)
                    }
                )
            }
        }
    }
}

@Composable
private fun ScannedDeviceList(
    devices: List<UnifiedDeviceResult>,
    sortOrder: ScanSortOrder,
    onSelect: (address: String, name: String?, deviceType: DeviceType) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(sortOrder) {
        listState.scrollToItem(0)
    }

    if (devices.isEmpty()) {
        Text(
            "暂无扫描结果",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    } else {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 520.dp)
        ) {
            items(
                items = devices,
                key = { it.address },
                contentType = { "scanned" }
            ) { device ->
                ScannedDeviceItem(
                    device = device,
                    onClick = {
                        onSelect(device.address, device.name, device.deviceType)
                    }
                )
            }
        }
    }
}

@Composable
private fun ScannedDeviceItem(
    device: UnifiedDeviceResult,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = device.name ?: device.address,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = device.address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                    device.rssi?.let { rssi ->
                        Text(
                            text = "${rssi} dBm",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            DeviceTypeChip(deviceType = device.deviceType)
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
