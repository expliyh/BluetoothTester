package top.expli.bluetoothtester.ui.spp

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.expli.bluetoothtester.model.SppConnectionState

@Composable
fun ConnectionStatusBanner(
    state: SppConnectionState,
    remoteAddress: String? = null,
    lastError: String? = null,
    modifier: Modifier = Modifier
) {
    val targetColor = when (state) {
        SppConnectionState.Idle -> MaterialTheme.colorScheme.surfaceVariant
        SppConnectionState.Connecting -> MaterialTheme.colorScheme.tertiary
        SppConnectionState.Connected -> MaterialTheme.colorScheme.primary
        SppConnectionState.Error -> MaterialTheme.colorScheme.error
        SppConnectionState.Closed -> MaterialTheme.colorScheme.outline
    }

    val backgroundColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 300),
        label = "connectionBannerColor"
    )

    val contentColor = when (state) {
        SppConnectionState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
        SppConnectionState.Connecting -> MaterialTheme.colorScheme.onTertiary
        SppConnectionState.Connected -> MaterialTheme.colorScheme.onPrimary
        SppConnectionState.Error -> MaterialTheme.colorScheme.onError
        SppConnectionState.Closed -> MaterialTheme.colorScheme.surface
    }

    val textContentColor by animateColorAsState(
        targetValue = contentColor,
        animationSpec = tween(durationMillis = 300),
        label = "connectionBannerContentColor"
    )

    val text = when (state) {
        SppConnectionState.Idle -> "未连接"
        SppConnectionState.Connecting -> "连接中…"
        SppConnectionState.Connected -> if (remoteAddress != null) "已连接 · $remoteAddress" else "已连接"
        SppConnectionState.Error -> if (lastError != null) "错误 · $lastError" else "错误"
        SppConnectionState.Closed -> "已断开"
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
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (state) {
                SppConnectionState.Connecting -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = textContentColor
                    )
                }
                SppConnectionState.Connected -> {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = textContentColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
                SppConnectionState.Error -> {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = textContentColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
                SppConnectionState.Idle, SppConnectionState.Closed -> {
                    Icon(
                        imageVector = Icons.Default.LinkOff,
                        contentDescription = null,
                        tint = textContentColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text(
                text = text,
                color = textContentColor,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
