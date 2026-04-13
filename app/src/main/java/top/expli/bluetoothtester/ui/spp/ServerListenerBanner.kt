package top.expli.bluetoothtester.ui.spp

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import top.expli.bluetoothtester.model.ServerListenerState

@Composable
fun ServerListenerBanner(
    listenerState: ServerListenerState,
    uuid: String,
    onToggleListening: () -> Unit,
    modifier: Modifier = Modifier,
    lastError: String? = null
) {
    val targetColor = when (listenerState) {
        ServerListenerState.Idle -> MaterialTheme.colorScheme.surfaceVariant
        ServerListenerState.Listening -> MaterialTheme.colorScheme.tertiary
        ServerListenerState.Error -> MaterialTheme.colorScheme.error
    }

    val backgroundColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 300),
        label = "listenerBannerColor"
    )

    val contentColor = when (listenerState) {
        ServerListenerState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
        ServerListenerState.Listening -> MaterialTheme.colorScheme.onTertiary
        ServerListenerState.Error -> MaterialTheme.colorScheme.onError
    }

    val textContentColor by animateColorAsState(
        targetValue = contentColor,
        animationSpec = tween(durationMillis = 300),
        label = "listenerBannerContentColor"
    )

    val stateLabel = when (listenerState) {
        ServerListenerState.Idle -> "未监听"
        ServerListenerState.Listening -> "监听中"
        ServerListenerState.Error -> lastError ?: "监听失败"
    }

    val buttonLabel = when (listenerState) {
        ServerListenerState.Idle, ServerListenerState.Error -> "开始监听"
        ServerListenerState.Listening -> "停止监听"
    }

    val stateIcon = when (listenerState) {
        ServerListenerState.Idle -> Icons.Default.BluetoothDisabled
        ServerListenerState.Listening -> Icons.AutoMirrored.Filled.BluetoothSearching
        ServerListenerState.Error -> Icons.Default.Warning
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = stateIcon,
                    contentDescription = null,
                    tint = textContentColor,
                    modifier = Modifier.size(16.dp)
                )
                Column {
                    Text(
                        text = stateLabel,
                        color = textContentColor,
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        text = uuid,
                        color = textContentColor.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
            }
            TextButton(onClick = onToggleListening) {
                Text(
                    text = buttonLabel,
                    color = textContentColor
                )
            }
        }
    }
}
