package top.expli.bluetoothtester.ui.device

import android.bluetooth.BluetoothDevice
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import top.expli.bluetoothtester.model.BleDeviceResult
import top.expli.bluetoothtester.model.BluetoothClassInfo
import top.expli.bluetoothtester.model.DeviceDetailInfo
import top.expli.bluetoothtester.model.DeviceInfoViewModel
import top.expli.bluetoothtester.model.DeviceType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    address: String,
    onBackClick: () -> Unit,
    onNavigateToSpp: () -> Unit = {},
    onNavigateToGattClient: (String, String) -> Unit = { _, _ -> },
    onNavigateToL2cap: (String, String) -> Unit = { _, _ -> },
    viewModel: DeviceInfoViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(address) {
        if (address.isNotBlank()) {
            @SuppressWarnings("MissingPermission")
            viewModel.loadDevice(address)
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
                            text = "设备详情",
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
                // ─── Basic Info ───
                item {
                    BasicInfoCard(info = uiState)
                }

                // ─── BLE Scan Data ───
                if (uiState.bleScanData != null) {
                    item {
                        BleScanDataCard(bleData = uiState.bleScanData!!)
                    }
                }

                // ─── UUID List ───
                if (uiState.uuids.isNotEmpty()) {
                    item {
                        UuidListCard(uuids = uiState.uuids)
                    }
                } else if (uiState.bondState == BluetoothDevice.BOND_BONDED) {
                    item {
                        FetchUuidsCard(
                            address = uiState.address,
                            viewModel = viewModel
                        )
                    }
                }

                // ─── Bluetooth Class Info ───
                if (uiState.bluetoothClass != null) {
                    item {
                        BluetoothClassCard(classInfo = uiState.bluetoothClass!!)
                    }
                }

                // ─── Quick Actions ───
                item {
                    QuickActionsCard(
                        address = uiState.address,
                        name = uiState.name ?: "",
                        onNavigateToSpp = onNavigateToSpp,
                        onNavigateToGattClient = onNavigateToGattClient,
                        onNavigateToL2cap = onNavigateToL2cap
                    )
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}


// ─── Basic Info Card ───

@Composable
private fun BasicInfoCard(info: DeviceDetailInfo) {
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
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "基本信息",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            InfoRow(label = "设备名称", value = info.name ?: "未知")
            InfoRow(label = "MAC 地址", value = info.address)
            InfoRow(label = "设备类型", value = when (info.deviceType) {
                DeviceType.Classic -> "经典蓝牙"
                DeviceType.BLE -> "BLE"
                DeviceType.Dual -> "双模"
            })
            InfoRow(label = "配对状态", value = when (info.bondState) {
                BluetoothDevice.BOND_BONDED -> "已配对"
                BluetoothDevice.BOND_BONDING -> "配对中"
                else -> "未配对"
            })
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace
        )
    }
}

// ─── BLE Scan Data Card ───

@Composable
private fun BleScanDataCard(bleData: BleDeviceResult) {
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
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "BLE 扫描数据",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            InfoRow(label = "RSSI", value = "${bleData.rssi} dBm")

            if (bleData.txPowerLevel != null) {
                InfoRow(label = "TX Power", value = "${bleData.txPowerLevel} dBm")
            }

            if (bleData.rawScanRecord.isNotBlank()) {
                Text(
                    text = "原始广播数据:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = bleData.rawScanRecord.chunked(32).joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (bleData.serviceUuids.isNotEmpty()) {
                Text(
                    text = "服务 UUID (${bleData.serviceUuids.size}):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                bleData.serviceUuids.forEach { uuid ->
                    Text(
                        text = uuid,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (bleData.manufacturerData.isNotEmpty()) {
                Text(
                    text = "厂商数据:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                bleData.manufacturerData.forEach { (companyId, hex) ->
                    Text(
                        text = "0x${companyId.toString(16).uppercase()}: $hex",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ─── UUID List Card ───

@Composable
private fun UuidListCard(uuids: List<String>) {
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
            Text(
                text = "UUID 列表 (${uuids.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            uuids.forEach { uuid ->
                Text(
                    text = uuid,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ─── Fetch UUIDs Card ───

@Composable
private fun FetchUuidsCard(
    address: String,
    viewModel: DeviceInfoViewModel
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
                text = "UUID 列表",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "暂无 UUID 数据，点击按钮获取",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(
                onClick = {
                    @SuppressWarnings("MissingPermission")
                    viewModel.fetchUuidsForCurrentDevice()
                }
            ) {
                Text("获取 UUID")
            }
        }
    }
}

// ─── Bluetooth Class Card ───

@Composable
private fun BluetoothClassCard(classInfo: BluetoothClassInfo) {
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
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "蓝牙设备类",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            InfoRow(
                label = "主设备类",
                value = classInfo.majorClassDescription
            )
            InfoRow(
                label = "次设备类",
                value = classInfo.minorClassDescription
            )
            InfoRow(
                label = "主设备类代码",
                value = "0x${classInfo.majorClass.toString(16).uppercase()}"
            )
            InfoRow(
                label = "设备类代码",
                value = "0x${classInfo.minorClass.toString(16).uppercase()}"
            )
        }
    }
}

// ─── Quick Actions Card ───

@Composable
private fun QuickActionsCard(
    address: String,
    name: String,
    onNavigateToSpp: () -> Unit,
    onNavigateToGattClient: (String, String) -> Unit,
    onNavigateToL2cap: (String, String) -> Unit
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
                text = "快捷操作",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onNavigateToSpp,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("SPP")
                }
                FilledTonalButton(
                    onClick = { onNavigateToGattClient(address, name) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("GATT")
                }
                FilledTonalButton(
                    onClick = { onNavigateToL2cap(address, name) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("L2CAP")
                }
            }
        }
    }
}
