package top.expli.bluetoothtester.model

import kotlinx.serialization.Serializable

// ─── BLE 扫描状态 ───

@Serializable
sealed interface BleScanState {
    @Serializable data object Idle : BleScanState
    @Serializable data object Scanning : BleScanState
    @Serializable data object Stopped : BleScanState
    @Serializable data class Error(val message: String) : BleScanState
}

// ─── 经典蓝牙扫描状态 ───

@Serializable
sealed interface ClassicScanState {
    @Serializable data object Idle : ClassicScanState
    @Serializable data object Scanning : ClassicScanState
    @Serializable data object Finished : ClassicScanState
    @Serializable data class Error(val message: String) : ClassicScanState
}

// ─── 扫描页面 UI 状态 ───

data class ScanUiState(
    val bleScanState: BleScanState = BleScanState.Idle,
    val classicScanState: ClassicScanState = ClassicScanState.Idle,
    val combinedDevices: List<UnifiedDeviceResult> = emptyList(),
    val filter: BleScanFilter = BleScanFilter(),
    val showClassicAffectsBleWarning: Boolean = false
)

// ─── GATT 客户端连接状态 ───

@Serializable
sealed interface GattConnectionState {
    @Serializable data object Idle : GattConnectionState
    @Serializable data object Connecting : GattConnectionState
    @Serializable data object Connected : GattConnectionState
    @Serializable data object Disconnected : GattConnectionState
    @Serializable data class Error(val status: Int) : GattConnectionState
}


// ─── GATT 客户端 UI 状态 ───

@Serializable
data class GattClientUiState(
    val connectionState: GattConnectionState = GattConnectionState.Idle,
    val targetAddress: String = "",
    val services: List<GattServiceInfo> = emptyList(),
    val log: List<GattLogEntry> = emptyList(),
    val currentMtu: Int = 23,
    val lastError: String? = null
)

// ─── GATT 服务端状态 ───

@Serializable
sealed interface GattServerState {
    @Serializable data object Idle : GattServerState
    @Serializable data object Running : GattServerState
    @Serializable data class Error(val message: String) : GattServerState
}

// ─── GATT 服务端 UI 状态 ───

@Serializable
data class GattServerUiState(
    val serverState: GattServerState = GattServerState.Idle,
    val activeServices: List<GattServiceConfig> = emptyList(),
    val connectedDevices: List<String> = emptyList(),
    val log: List<GattServerLogEntry> = emptyList(),
    val lastError: String? = null
)

// ─── L2CAP 连接状态 ───

@Serializable
sealed interface L2capConnectionState {
    @Serializable data object Idle : L2capConnectionState
    @Serializable data object Connecting : L2capConnectionState
    @Serializable data object Listening : L2capConnectionState
    @Serializable data object Connected : L2capConnectionState
    @Serializable data object Closed : L2capConnectionState
    @Serializable data class Error(val message: String) : L2capConnectionState
}

// ─── L2CAP 角色 ───

@Serializable
enum class L2capRole { Client, Server }

// ─── L2CAP UI 状态 ───

@Serializable
data class L2capUiState(
    val role: L2capRole = L2capRole.Client,
    val connectionState: L2capConnectionState = L2capConnectionState.Idle,
    val targetAddress: String = "",
    val psm: Int? = null,
    val assignedPsm: Int? = null,
    val chat: List<SppChatItem> = emptyList(),
    val sendingText: String = "",
    val lastError: String? = null
)

// ─── BLE 广播状态 ───

@Serializable
sealed interface AdvertiserState {
    @Serializable data object Idle : AdvertiserState
    @Serializable data object Advertising : AdvertiserState
    @Serializable data class Error(val errorCode: Int, val message: String) : AdvertiserState
}

// ─── BLE 广播 UI 状态 ───

@Serializable
data class AdvertiserUiState(
    val state: AdvertiserState = AdvertiserState.Idle,
    val config: AdvertiseConfig = AdvertiseConfig(),
    val durationMs: Long = 0L,
    val lastError: String? = null
)

// ─── Foreground Service 状态 ───

enum class ForegroundReason {
    SppConnection,
    GattConnection,
    L2capConnection,
    BleAdvertising,
    SpeedTest
}

data class ForegroundServiceState(
    val isRunning: Boolean = false,
    val activeReasons: Set<ForegroundReason> = emptySet()
)
