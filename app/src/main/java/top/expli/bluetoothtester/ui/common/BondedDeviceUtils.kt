package top.expli.bluetoothtester.ui.common

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import top.expli.bluetoothtester.model.DeviceType

data class BondedDeviceItem(
    val name: String,
    val address: String,
    val deviceType: DeviceType = DeviceType.Classic
)

sealed interface BondedDeviceLoadResult {
    data class Success(val devices: List<BondedDeviceItem>) : BondedDeviceLoadResult
    data object BluetoothDisabled : BondedDeviceLoadResult
    data object BluetoothUnavailable : BondedDeviceLoadResult
}

@SuppressLint("MissingPermission")
fun loadBondedDeviceItems(context: Context): BondedDeviceLoadResult {
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        ?: return BondedDeviceLoadResult.BluetoothUnavailable
    if (!adapter.isEnabled) return BondedDeviceLoadResult.BluetoothDisabled
    val devices = adapter.bondedDevices ?: emptySet()
    return devices
        .asSequence()
        .map { dev ->
            val name = runCatching { dev.name }.getOrNull().orEmpty()
            BondedDeviceItem(
                name = name,
                address = dev.address,
                deviceType = mapBluetoothDeviceType(dev.type)
            )
        }
        .filter { it.address.isNotBlank() }
        .distinctBy { it.address }
        .sortedWith(
            compareBy<BondedDeviceItem> { it.name.ifBlank { it.address }.lowercase() }
                .thenBy { it.address }
        )
        .toList()
        .let { BondedDeviceLoadResult.Success(it) }
}

private fun mapBluetoothDeviceType(type: Int): DeviceType = when (type) {
    BluetoothDevice.DEVICE_TYPE_LE -> DeviceType.BLE
    BluetoothDevice.DEVICE_TYPE_CLASSIC -> DeviceType.Classic
    BluetoothDevice.DEVICE_TYPE_DUAL -> DeviceType.Dual
    else -> DeviceType.Classic
}
