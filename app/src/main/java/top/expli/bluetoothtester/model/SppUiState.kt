package top.expli.bluetoothtester.model

import kotlinx.serialization.Serializable

@Serializable
enum class ActiveTestType {
    SpeedTest, PeriodicSend, ConnectionCycle, PingRtt, PassiveReceive
}

@Serializable
enum class SpeedTestStopCondition { ByTime, ByBytes }

@Serializable
sealed interface PeriodicStopCondition {
    @Serializable
    data class ByCount(val count: Long) : PeriodicStopCondition
    @Serializable
    data class ByTime(val seconds: Long) : PeriodicStopCondition
}

@Serializable
enum class SppChatDirection { In, Out, System }

@Serializable
enum class SppSpeedTestMode { TxOnly, RxOnly, Duplex }

@Serializable
data class SppChatItem(
    val id: Long,
    val direction: SppChatDirection,
    val text: String
)

@Serializable
data class SppSpeedSample(
    val id: Long,
    val elapsedMs: Long,
    val txInstantBps: Double,
    val rxInstantBps: Double,
    val txAvgBps: Double,
    val rxAvgBps: Double,
    val txTotalBytes: Long,
    val rxTotalBytes: Long
)

@Serializable
sealed interface SppConnectionState {
    @Serializable
    data object Idle : SppConnectionState
    @Serializable
    data object Connecting : SppConnectionState
    @Serializable
    data object Connected : SppConnectionState
    @Serializable
    data object Closed : SppConnectionState
    @Serializable
    data object Error : SppConnectionState
}

@Serializable
enum class ServerListenerState {
    Idle,       // 未监听
    Listening,  // 监听中（ServerSocket 已注册，等待 accept）
    Error       // 监听失败
}

@Serializable
enum class SecurityMode {
    Secure,   // createRfcommSocketToServiceRecord / listenUsingRfcommWithServiceRecord
    Insecure  // createInsecureRfcommSocketToServiceRecord / listenUsingInsecureRfcommWithServiceRecord
}

@Serializable
data class ServerTabConfig(
    val tabId: String,          // UUID v4, Tab unique identifier
    val uuid: String,           // Bluetooth SPP UUID
    val name: String,           // User-defined name
    val createdAt: Long,        // Creation timestamp for ordering
    val securityMode: SecurityMode = SecurityMode.Secure  // 新增，默认 Secure，旧数据自动兼容
)

@Serializable
data class ServerSessionSnapshot(
    val id: String = java.util.UUID.randomUUID().toString(),
    val remoteDeviceAddress: String,
    val remoteDeviceName: String? = null,
    val disconnectedAt: Long,
    val chat: List<SppChatItem>,
    val speedTestTxAvgBps: Double? = null,
    val speedTestRxAvgBps: Double? = null,
    val speedTestSamples: List<SppSpeedSample> = emptyList(),
    val securityMode: SecurityMode = SecurityMode.Secure  // 新增，默认 Secure，旧数据自动兼容
)

@Serializable
data class ClientSessionSnapshot(
    val sessionId: String,                                        // UUID v4，会话创建时生成
    val remoteDeviceAddress: String,                              // 远端设备 MAC 地址
    val remoteDeviceName: String? = null,                         // 远端设备名称（可空）
    val uuid: String,                                             // 连接时使用的 SPP UUID
    val securityMode: SecurityMode = SecurityMode.Secure,         // 创建会话时的 Security_Mode
    val disconnectedAt: Long,                                     // 断开时间戳
    val chat: List<SppChatItem> = emptyList(),                    // 聊天记录
    val speedTestTxAvgBps: Double? = null,                        // 测速发送平均速率
    val speedTestRxAvgBps: Double? = null                         // 测速接收平均速率
)

// TODO: 未来迁移 SppSession 平铺字段到以下嵌套状态类，减少 copy() 开销
// SpeedTestState, PeriodicTestState, ConnectionCycleState, PingTestState, AutoReconnectConfig

@Serializable
data class SppSession(
    val device: SppDevice,
    val connectionState: SppConnectionState = SppConnectionState.Idle,
    val serverListenerState: ServerListenerState = ServerListenerState.Idle,
    val remoteDeviceAddress: String? = null,
    val lastError: String? = null,
    val chat: List<SppChatItem> = emptyList(),
    val sendingText: String = "",
    val receiveBufferSize: Int = 4096,
    val parseIncomingAsText: Boolean = true,
    val muteConsoleDuringTest: Boolean = true,
    val speedTestMode: SppSpeedTestMode = SppSpeedTestMode.TxOnly,
    val speedTestWithCrc: Boolean = false,
    val speedTestStopCondition: SpeedTestStopCondition = SpeedTestStopCondition.ByTime,
    val speedTestTargetBytes: Long = 1_048_576,
    val sendPayloadSize: Int = 512,
    val speedTestPayload: String = "",  // 自定义测速payload（空=使用默认序列）
    val speedTestWindowOpen: Boolean = false,
    val speedTestRunning: Boolean = false,
    val speedTestElapsedMs: Long = 0L,
    val speedTestTxTotalBytes: Long = 0L,
    val speedTestRxTotalBytes: Long = 0L,
    val speedTestTxInstantBps: Double? = null,
    val speedTestRxInstantBps: Double? = null,
    val speedTestTxAvgBps: Double? = null,
    val speedTestRxAvgBps: Double? = null,
    val speedTestTxWriteAvgMs: Double? = null,
    val speedTestTxWriteMaxMs: Double? = null,
    val speedTestRxReadAvgMs: Double? = null,
    val speedTestRxReadMaxMs: Double? = null,
    val speedTestRxReadAvgBytes: Double? = null,
    val speedTestTxFirstWriteDelayMs: Long? = null,
    val speedTestRxFirstByteDelayMs: Long? = null,
    val speedTestSamples: List<SppSpeedSample> = emptyList(),
    val activeTestType: ActiveTestType? = null,
    // ── 连接循环压测状态 ──
    val connectionCycleRunning: Boolean = false,
    val connectionCycleTotalCount: Int = 0,
    val connectionCycleSuccessCount: Int = 0,
    val connectionCycleFailCount: Int = 0,
    val connectionCycleAvgMs: Double = 0.0,
    val connectionCycleMaxMs: Long = 0,
    val connectionCycleMinMs: Long = Long.MAX_VALUE,
    val connectionCycleInterval: Long = 1000,       // 断开后等待间隔
    val connectionCycleTimeout: Long = 10000,       // 单次连接超时
    val connectionCycleTargetCount: Int = 10,        // 0 = 无限
    // ── Ping RTT 测量状态 ──
    val pingTestRunning: Boolean = false,
    val pingTestCount: Int = 0,
    val pingTestSuccessCount: Int = 0,
    val pingTestTimeoutCount: Int = 0,
    val pingTestAvgRtt: Double = 0.0,
    val pingTestMaxRtt: Double = 0.0,
    val pingTestMinRtt: Double = Double.MAX_VALUE,
    val pingTestLastRtt: Double? = null,
    val pingTestInterval: Long = 1000,
    val pingTestTimeout: Long = 5000,
    val pingTestTargetCount: Int = 10,               // 0 = 无限
    val pingTestPaddingSize: Int = 0,
    // ── 定时/循环发送状态 ──
    val periodicTestRunning: Boolean = false,
    val periodicTestInterval: Long = 0,              // 0 = 最大速率
    val periodicTestSentCount: Long = 0,
    val periodicTestSentBytes: Long = 0,
    val periodicTestSuccessCount: Long = 0,
    val periodicTestFailCount: Long = 0,
    val periodicTestElapsedMs: Long = 0,
    val periodicTestStopCondition: PeriodicStopCondition = PeriodicStopCondition.ByCount(100),
    val periodicTestSendPayloadSize: Int = 512,
    // ── 会话安全模式 ──
    val securityMode: SecurityMode = SecurityMode.Secure,
    // ── 自动重连配置 ──
    val autoReconnectEnabled: Boolean = false,
    val autoReconnectInterval: Long = 2000,
    val autoReconnectMaxRetries: Int = 3,
    // ── 连接时间戳 ──
    val connectedAt: Long? = null,
    // ── Server 历史会话 ──
    val sessionHistory: List<ServerSessionSnapshot> = emptyList()
)

@Serializable
data class SppUiState(
    val registered: List<SppDevice> = emptyList(),
    val selectedKey: String? = null,
    val sessions: Map<String, SppSession> = emptyMap(),
    val serverTabs: List<ServerTabConfig> = emptyList(),
    val clientSessionHistory: List<ClientSessionSnapshot> = emptyList()
)
