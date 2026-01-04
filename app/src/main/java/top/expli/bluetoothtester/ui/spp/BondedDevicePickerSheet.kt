package top.expli.bluetoothtester.ui.spp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

data class BondedDeviceItem(
    val name: String,
    val address: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BondedDevicePickerSheet(
    devices: List<BondedDeviceItem>,
    onDismissRequest: () -> Unit,
    onSelect: (BondedDeviceItem) -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier
    ) {
        Text(
            "选择已绑定设备",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (devices.isEmpty()) {
            Text(
                "未找到已绑定设备，请先在系统蓝牙设置中配对。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.heightIn(max = 520.dp)
            ) {
                items(devices, key = { it.address }) { dev ->
                    ListItem(
                        headlineContent = {
                            Text(dev.name.ifBlank { dev.address })
                        },
                        supportingContent = {
                            Text(dev.address, fontFamily = FontFamily.Monospace)
                        },
                        modifier = Modifier.clickable { onSelect(dev) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

