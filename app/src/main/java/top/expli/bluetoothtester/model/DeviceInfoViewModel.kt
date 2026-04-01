package top.expli.bluetoothtester.model

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import top.expli.bluetoothtester.util.BluetoothClassMapper

class DeviceInfoViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "DeviceInfoViewModel"
    }

    private val adapter: BluetoothAdapter? =
        app.getSystemService(BluetoothManager::class.java)?.adapter

    private val _uiState = MutableStateFlow(
        DeviceDetailInfo(
            address = "",
            name = null,
            deviceType = DeviceType.Classic,
            bondState = BluetoothDevice.BOND_NONE,
            bluetoothClass = null,
            uuids = emptyList(),
            bleScanData = null
        )
    )
    val uiState: StateFlow<DeviceDetailInfo> = _uiState.asStateFlow()

    // UUID cache: address -> list of UUID strings
    private val uuidCache = mutableMapOf<String, List<String>>()

    private var uuidReceiver: BroadcastReceiver? = null

    // ─── Public API ───

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun loadDevice(address: String) {
        val device = try {
            adapter?.getRemoteDevice(address)
        } catch (_: IllegalArgumentException) {
            _uiState.update { it.copy(address = address, name = "无效地址") }
            return
        }
        if (device == null) {
            _uiState.update { it.copy(address = address, name = "设备不可用") }
            return
        }

        val btClass = device.bluetoothClass
        val classInfo = btClass?.let {
            BluetoothClassInfo(
                majorClass = it.majorDeviceClass,
                majorClassDescription = BluetoothClassMapper.majorClassName(it.majorDeviceClass),
                minorClass = it.deviceClass,
                minorClassDescription = BluetoothClassMapper.minorClassName(it)
            )
        }

        val deviceType = when (device.type) {
            BluetoothDevice.DEVICE_TYPE_LE -> DeviceType.BLE
            BluetoothDevice.DEVICE_TYPE_DUAL -> DeviceType.Dual
            else -> DeviceType.Classic
        }

        // Use cached UUIDs or device.uuids
        val cachedUuids = uuidCache[address]
        val uuids = cachedUuids ?: device.uuids?.map { it.uuid.toString() } ?: emptyList()

        _uiState.update {
            DeviceDetailInfo(
                address = address,
                name = device.name,
                deviceType = deviceType,
                bondState = device.bondState,
                bluetoothClass = classInfo,
                uuids = uuids,
                bleScanData = it.bleScanData
            )
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun fetchUuidsForCurrentDevice() {
        val address = _uiState.value.address
        if (address.isBlank()) return
        val device = try {
            adapter?.getRemoteDevice(address)
        } catch (_: IllegalArgumentException) {
            return
        } ?: return
        fetchUuids(device)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun fetchUuids(device: BluetoothDevice) {
        // Register receiver for ACTION_UUID
        unregisterUuidReceiver()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != BluetoothDevice.ACTION_UUID) return
                val extraDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(
                        BluetoothDevice.EXTRA_DEVICE,
                        BluetoothDevice::class.java
                    )
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                }
                if (extraDevice?.address != device.address) return

                val parcelUuids: Array<ParcelUuid>? =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableArrayExtra(
                            BluetoothDevice.EXTRA_UUID,
                            ParcelUuid::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION", "UNCHECKED_CAST")
                        intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID)
                            ?.filterIsInstance<ParcelUuid>()?.toTypedArray()
                    }

                val uuidStrings = parcelUuids?.map { it.uuid.toString() } ?: emptyList()
                uuidCache[device.address] = uuidStrings

                _uiState.update { it.copy(uuids = uuidStrings) }
                Log.d(TAG, "Received ${uuidStrings.size} UUIDs for ${device.address}")
            }
        }

        uuidReceiver = receiver
        val filter = IntentFilter(BluetoothDevice.ACTION_UUID)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getApplication<Application>().registerReceiver(
                receiver, filter, Context.RECEIVER_EXPORTED
            )
        } else {
            getApplication<Application>().registerReceiver(receiver, filter)
        }

        // Trigger SDP query
        device.fetchUuidsWithSdp()
    }

    // ─── Lifecycle ───

    override fun onCleared() {
        super.onCleared()
        unregisterUuidReceiver()
    }

    private fun unregisterUuidReceiver() {
        uuidReceiver?.let {
            try {
                getApplication<Application>().unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
                // Already unregistered
            }
        }
        uuidReceiver = null
    }
}
