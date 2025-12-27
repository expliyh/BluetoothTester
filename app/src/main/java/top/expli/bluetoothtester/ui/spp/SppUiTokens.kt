package top.expli.bluetoothtester.ui.spp

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.ui.graphics.vector.ImageVector
import top.expli.bluetoothtester.model.SppConnectionState
import top.expli.bluetoothtester.model.SppRole

internal fun SppRole.label(): String = when (this) {
    SppRole.Client -> "客户端"
    SppRole.Server -> "服务端"
}

internal fun SppRole.icon(): ImageVector = when (this) {
    SppRole.Client -> Icons.Default.PhoneAndroid
    SppRole.Server -> Icons.Default.Dns
}

internal fun SppConnectionState.label(): String = when (this) {
    SppConnectionState.Idle -> "未连接"
    SppConnectionState.Connecting -> "连接中"
    SppConnectionState.Listening -> "监听中"
    SppConnectionState.Connected -> "已连接"
    SppConnectionState.Closed -> "已关闭"
    SppConnectionState.Error -> "异常"
}
