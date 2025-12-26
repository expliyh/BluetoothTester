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
    data object BleScanner : Route

    @Serializable
    data object ClassicBluetooth : Route
}

