package top.expli.bluetoothtester.ui.spp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.expli.bluetoothtester.model.SecurityMode
import top.expli.bluetoothtester.model.ServerListenerState

@Composable
fun ServerControlCard(
    listenerState: ServerListenerState,
    uuid: String,
    onUuidChange: (String) -> Unit,
    securityMode: SecurityMode,
    onSecurityModeChange: (SecurityMode) -> Unit,
    onStartListening: () -> Unit,
    onStopListening: () -> Unit,
    listenEnabled: Boolean,
    lastError: String?,
    acceptCount: Int = 1,
    modifier: Modifier = Modifier
) {
    val isListening = listenerState == ServerListenerState.Listening

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // 状态指示
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val stateText = when (listenerState) {
                    ServerListenerState.Idle -> "● 未监听"
                    ServerListenerState.Listening -> "● 监听中"
                    ServerListenerState.Error -> "● 错误"
                }
                val stateColor = when (listenerState) {
                    ServerListenerState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
                    ServerListenerState.Listening -> MaterialTheme.colorScheme.primary
                    ServerListenerState.Error -> MaterialTheme.colorScheme.error
                }
                Text(text = stateText, color = stateColor, style = MaterialTheme.typography.labelMedium)
                Text(text = "Accept: $acceptCount (V2 支持多连接)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            OutlinedTextField(
                value = uuid,
                onValueChange = onUuidChange,
                label = { Text("UUID") },
                placeholder = { Text("1101") },
                singleLine = true,
                enabled = !isListening,
                modifier = Modifier.fillMaxWidth()
            )

            SecurityModeSelector(selected = securityMode, onSelectionChange = onSecurityModeChange, enabled = !isListening)

            if (lastError != null) {
                Text(text = lastError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onStartListening,
                    enabled = listenEnabled && !isListening,
                    modifier = Modifier.weight(1f)
                ) { Text("Listen") }
                OutlinedButton(
                    onClick = onStopListening,
                    enabled = isListening,
                    modifier = Modifier.weight(1f)
                ) { Text("Stop") }
            }
        }
    }
}
