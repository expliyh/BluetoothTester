package top.expli.bluetoothtester.model

import kotlinx.serialization.Serializable

@Serializable
data class SppDevice(
    val name: String,
    val address: String = "",
    val uuid: String = DEFAULT_SPP_UUID,
    val role: SppRole = SppRole.Client,
    val note: String? = null
) {
    companion object {
        const val DEFAULT_SPP_UUID: String = "00001101-0000-1000-8000-00805F9B34FB"
    }
}

@Serializable
enum class SppRole { Client, Server }
