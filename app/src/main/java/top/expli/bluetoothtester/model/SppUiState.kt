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
    val payloadSize: Int = 256
)
