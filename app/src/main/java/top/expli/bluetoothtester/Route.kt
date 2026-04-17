package top.expli.bluetoothtester

import kotlinx.serialization.Serializable

@Serializable
sealed interface Route {
    @Serializable
    data object Main : Route

    @Serializable
    data object Settings : Route

    @Serializable
    data object Scan : Route

    @Serializable
    data object PairedDevices : Route

    @Serializable
    data object AdvancedPermission : Route

    @Serializable
    data object BluetoothToggle : Route

    @Serializable
    data object Spp : Route

    @Serializable
    data class GattClient(val address: String = "", val name: String = "") : Route

    @Serializable
    data class GattServer(val dummy: Int = 0) : Route

    @Serializable
    data class L2cap(val address: String = "", val name: String = "") : Route

    @Serializable
    data class DeviceDetail(val address: String) : Route

    @Serializable
    data object Advertiser : Route

    @Serializable
    data object AdbHelp : Route

    @Serializable
    data object OpenSourceLicenses : Route
}
