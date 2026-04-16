package top.expli.bluetoothtester.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.expli.bluetoothtester.model.DeviceType
import top.expli.bluetoothtester.model.UnifiedDeviceResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicePickerSheet(
    bondedDevices: List<BondedDeviceItem> = emptyList(),
    scannedDevices: List<UnifiedDeviceResult> = emptyList(),
    showBonded: Boolean = true,
    showScanned: Boolean = true,
    deviceTypeFilter: Set<DeviceType>? = null,
    onStartScan: (() -> Unit)? = null,
    onDismissRequest: () -> Unit,
    onSelect: (address: String, name: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredBonded = remember(bondedDevices, deviceTypeFilter) {
        if (deviceTypeFilter != null) {
            bondedDevices.filter { it.deviceType in deviceTypeFilter }
        } else {
            bondedDevices
        }
    }

    val filteredScanned = remember(scannedDevices, deviceTypeFilter) {
        if (deviceTypeFilter != null) {
            scannedDevices.filter { it.deviceType in deviceTypeFilter }
        } else {
            scannedDevices
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {
        if (showBonded && showScanned) {
            // Dual-tab mode
            var selectedTab by remember { mutableIntStateOf(0) }

            // Trigger scan when switching to scanned tab
            LaunchedEffect(selectedTab) {
                if (selectedTab == 1) {
                    onStartScan?.invoke()
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
                    onSelect = { address, name ->
                        onSelect(address, name)
                        onDismissRequest()
                    }
                )
                1 -> ScannedDeviceList(
                    devices = filteredScanned,
                    onSelect = { address, name ->
                        onSelect(address, name)
                        onDismissRequest()
                    }
                )
            }
        } else if (showBonded) {
            // Single list: bonded only
            Text(
                "选择已绑定设备",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            BondedDeviceList(
                devices = filteredBonded,
                onSelect = { address, name ->
                    onSelect(address, name)
                    onDismissRequest()
                }
            )
        } else if (showScanned) {
            // Single list: scanned only
            LaunchedEffect(Unit) {
                onStartScan?.invoke()
            }
            Text(
                "扫描结果",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            ScannedDeviceList(
                devices = filteredScanned,
                onSelect = { address, name ->
                    onSelect(address, name)
                    onDismissRequest()
                }
            )
        }
    }
}

@Composable
private fun BondedDeviceList(
    devices: List<BondedDeviceItem>,
    onSelect: (address: String, name: String?) -> Unit
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
                        onSelect(dev.address, dev.name.ifBlank { null })
                    }
                )
            }
        }
    }
}

@Composable
private fun ScannedDeviceList(
    devices: List<UnifiedDeviceResult>,
    onSelect: (address: String, name: String?) -> Unit
) {
    if (devices.isEmpty()) {
        Text(
            "暂无扫描结果",
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
                contentType = { "scanned" }
            ) { device ->
                ScannedDeviceItem(
                    device = device,
                    onClick = {
                        onSelect(device.address, device.name)
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
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
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
