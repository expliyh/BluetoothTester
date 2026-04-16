package top.expli.bluetoothtester.model

import android.bluetooth.BluetoothDevice
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ─── 设备类型枚举 ───

@Serializable
enum class DeviceType { Classic, BLE, Dual }

enum class ScanMode { BrOnly, LeOnly, Dual }

// ─── BLE 扫描结果 ───

@Serializable
data class BleDeviceResult(
    val address: String,
    val name: String?,
    val rssi: Int,
    val serviceUuids: List<String>,
    val manufacturerData: Map<Int, String>,  // companyId -> hex string
    val txPowerLevel: Int?,
    val rawScanRecord: String,  // hex string
    val lastSeenTimestamp: Long
)

/**
 * BLE 扫描记录的运行时数据，包含 ByteArray 和 BluetoothDevice 等不可序列化类型。
 * 仅在内存中使用，不参与序列化。
 */
data class BleScanRecordData(
    val serviceUuids: List<java.util.UUID>,
    val manufacturerData: Map<Int, ByteArray>,  // companyId -> data
    val txPowerLevel: Int?,
    val rawBytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BleScanRecordData) return false
        return serviceUuids == other.serviceUuids &&
                manufacturerData.keys == other.manufacturerData.keys &&
                manufacturerData.all { (k, v) -> other.manufacturerData[k]?.contentEquals(v) == true } &&
                txPowerLevel == other.txPowerLevel &&
                rawBytes.contentEquals(other.rawBytes)
    }

    override fun hashCode(): Int {
        var result = serviceUuids.hashCode()
        result = 31 * result + manufacturerData.entries.sumOf { it.key.hashCode() + it.value.contentHashCode() }
        result = 31 * result + (txPowerLevel ?: 0)
        result = 31 * result + rawBytes.contentHashCode()
        return result
    }
}


// ─── BLE 扫描过滤 ───

data class BleScanFilter(
    val nameKeyword: String? = null,
    val serviceUuid: java.util.UUID? = null,
    val rssiThreshold: Int? = null  // e.g. -70
)

// ─── 经典蓝牙扫描结果 ───

@Serializable
data class ClassicDeviceResult(
    val address: String,
    val name: String?,
    val majorDeviceClass: Int,
    val minorDeviceClass: Int,
    val deviceType: DeviceType,
    val rssi: Short? = null
)

// ─── 统一设备结果（合并 BLE 和经典扫描结果） ───

@Serializable
data class UnifiedDeviceResult(
    val address: String,
    val name: String?,
    val deviceType: DeviceType,
    val rssi: Int? = null,
    val bleData: BleDeviceResult? = null,
    val classicData: ClassicDeviceResult? = null
)

// ─── 已配对设备信息（运行时，包含 BluetoothDevice） ───

/**
 * 已配对设备信息，包含 BluetoothDevice 引用。
 * 仅在内存中使用，不参与序列化。
 */
data class PairedDeviceInfo(
    val device: BluetoothDevice,
    val name: String?,
    val address: String,
    val deviceType: DeviceType,
    val uuids: List<java.util.UUID>
)


// ─── GATT 服务树形结构 ───

@Serializable
data class GattServiceInfo(
    val uuid: String,
    val isPrimary: Boolean,
    val characteristics: List<GattCharacteristicInfo>
)

@Serializable
data class GattCharacteristicInfo(
    val uuid: String,
    val properties: Int,
    val permissions: Int,
    val descriptors: List<GattDescriptorInfo>,
    val lastReadValue: String? = null  // hex string
)

@Serializable
data class GattDescriptorInfo(
    val uuid: String,
    val permissions: Int,
    val lastReadValue: String? = null
)

// ─── GATT 操作日志 ───

@Serializable
data class GattLogEntry(
    val id: Long,
    val timestamp: Long,
    val operation: GattOperation,
    val serviceUuid: String,
    val characteristicUuid: String?,
    val descriptorUuid: String?,
    val dataHex: String?,
    val dataUtf8: String?,
    val status: Int?,
    val success: Boolean
)

@Serializable
enum class GattOperation {
    Read, Write, WriteNoResponse, Notify, Indicate,
    ReadDescriptor, WriteDescriptor, MtuRequest, ServiceDiscovery
}


// ─── GATT 服务端配置数据（使用 String UUID 以支持序列化） ───

@Serializable
data class GattServiceConfig(
    val serviceUuid: String,
    val characteristics: List<GattCharacteristicConfig>
)

@Serializable
data class GattCharacteristicConfig(
    val uuid: String,
    val properties: Int,
    val permissions: Int,
    val initialValue: String = "",  // hex string
    val descriptors: List<GattDescriptorConfig> = emptyList()
)

@Serializable
data class GattDescriptorConfig(
    val uuid: String,
    val permissions: Int,
    val initialValue: String = ""
)

// ─── GATT 服务端日志 ───

@Serializable
data class GattServerLogEntry(
    val id: Long,
    val timestamp: Long,
    val eventType: GattServerEvent,
    val deviceAddress: String,
    val serviceUuid: String?,
    val characteristicUuid: String?,
    val requestId: Int?,
    val data: String?,
    val offset: Int?
)

@Serializable
enum class GattServerEvent {
    DeviceConnected, DeviceDisconnected,
    ReadRequest, WriteRequest,
    NotificationSent, MtuChanged
}


// ─── BLE 广播配置数据 ───

@Serializable
data class AdvertiseConfig(
    val mode: Int = 1,           // 0=LOW_POWER, 1=BALANCED, 2=LOW_LATENCY
    val txPowerLevel: Int = 1,   // 0=ULTRA_LOW, 1=LOW, 2=MEDIUM, 3=HIGH
    val connectable: Boolean = true,
    val serviceUuids: List<String> = emptyList(),
    val includeName: Boolean = true,
    val manufacturerData: Map<Int, String> = emptyMap(),  // companyId -> hex
    val scanResponseServiceUuids: List<String> = emptyList(),
    val scanResponseManufacturerData: Map<Int, String> = emptyMap()
)

// ─── ADB 命令与响应数据 ───

/**
 * ADB 命令解析结果。仅在内存中使用，不参与序列化。
 */
data class AdbCommand(
    val command: String,
    val params: Map<String, String>
)

@Serializable
data class AdbResponse(
    val success: Boolean,
    val data: JsonElement? = null,
    val error: String? = null,
    val message: String? = null
)

// ─── 设备详情数据 ───

@Serializable
data class DeviceDetailInfo(
    val address: String,
    val name: String?,
    val deviceType: DeviceType,
    val bondState: Int,
    val bluetoothClass: BluetoothClassInfo?,
    val uuids: List<String>,
    val bleScanData: BleDeviceResult? = null
)

@Serializable
data class BluetoothClassInfo(
    val majorClass: Int,
    val majorClassDescription: String,
    val minorClass: Int,
    val minorClassDescription: String
)
