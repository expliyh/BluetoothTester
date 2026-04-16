package top.expli.bluetoothtester.ui.spp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.expli.bluetoothtester.bluetooth.UuidHelper
import top.expli.bluetoothtester.model.SecurityMode

@Composable
fun ClientControlCard(
    address: String,
    onAddressChange: (String) -> Unit,
    uuid: String,
    onUuidChange: (String) -> Unit,
    securityMode: SecurityMode,
    onSecurityModeChange: (SecurityMode) -> Unit,
    onConnect: () -> Unit,
    onDiscoverDevices: () -> Unit,
    connectEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    var uuidError by remember { mutableStateOf<String?>(null) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = address,
                onValueChange = onAddressChange,
                label = { Text("地址") },
                placeholder = { Text("AA:BB:CC:DD:EE:FF") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = onDiscoverDevices) {
                        Icon(Icons.AutoMirrored.Filled.BluetoothSearching, "选择设备")
                    }
                }
            )
            OutlinedTextField(
                value = uuid,
                onValueChange = { newValue ->
                    onUuidChange(newValue)
                    val result = UuidHelper.parse(newValue)
                    uuidError = when (result) {
                        is UuidHelper.UuidParseResult.Invalid -> result.reason
                        is UuidHelper.UuidParseResult.Valid -> null
                    }
                },
                label = { Text("UUID") },
                placeholder = { Text("1101") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = uuidError != null,
                supportingText = if (uuidError != null) { { Text(uuidError!!, color = MaterialTheme.colorScheme.error) } } else null
            )
            SecurityModeSelector(selected = securityMode, onSelectionChange = onSecurityModeChange)
            Button(
                onClick = onConnect,
                enabled = connectEnabled && uuidError == null && uuid.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("连接") }
        }
    }
}
