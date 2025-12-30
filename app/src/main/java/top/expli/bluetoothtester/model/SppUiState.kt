package top.expli.bluetoothtester.model

import kotlinx.serialization.Serializable

@Serializable
enum class SppChatDirection { In, Out, System }

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
    data object Listening : SppConnectionState
    @Serializable
    data object Connected : SppConnectionState
    @Serializable
    data object Closed : SppConnectionState
    @Serializable
    data object Error : SppConnectionState
}

@Serializable
data class SppUiState(
    val registered: List<SppDevice> = emptyList(),
    val selected: SppDevice? = null,
    val connectionState: SppConnectionState = SppConnectionState.Idle,
    val lastError: String? = null,
    val chat: List<SppChatItem> = emptyList(),
    val sendingText: String = "",
    val payloadSize: Int = 256,
    val parseIncomingAsText: Boolean = true,
    val speedTestRunning: Boolean = false,
    val speedTestElapsedMs: Long = 0L,
    val speedTestTxTotalBytes: Long = 0L,
    val speedTestRxTotalBytes: Long = 0L,
    val speedTestTxInstantBps: Double? = null,
    val speedTestRxInstantBps: Double? = null,
    val speedTestTxAvgBps: Double? = null,
    val speedTestRxAvgBps: Double? = null,
    val speedTestSamples: List<SppSpeedSample> = emptyList()
)
