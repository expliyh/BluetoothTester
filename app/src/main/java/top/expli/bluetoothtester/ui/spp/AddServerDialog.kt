package top.expli.bluetoothtester.ui.spp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import top.expli.bluetoothtester.bluetooth.UuidHelper
import top.expli.bluetoothtester.model.SppDevice

@Composable
fun AddServerDialog(
    onDismiss: () -> Unit,
    onConfirm: (uuid: String, name: String) -> Unit
) {
    var name by remember { mutableStateOf("SPP Server") }
    var uuid by remember { mutableStateOf(UuidHelper.toShortForm(SppDevice.DEFAULT_SPP_UUID)) }
    var uuidError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加 Server Tab") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                        { Text(text = uuidError!!, color = MaterialTheme.colorScheme.error) }
                    } else null
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = uuidError == null && uuid.isNotBlank(),
                onClick = {
                    val parseResult = UuidHelper.parse(uuid)
                    if (parseResult is UuidHelper.UuidParseResult.Invalid) {
                        uuidError = parseResult.reason
                        return@TextButton
                    }
                    val fullUuid = (parseResult as UuidHelper.UuidParseResult.Valid).uuid.toString()
                    val finalName = name.ifBlank { "SPP Server" }
                    onConfirm(fullUuid, finalName)
                }
            ) { Text("创建") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
