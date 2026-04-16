package top.expli.bluetoothtester.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import top.expli.bluetoothtester.model.ClassicDeviceResult
import top.expli.bluetoothtester.model.DeviceType

/**
 * 经典蓝牙扫描工具类，封装 BluetoothAdapter.startDiscovery() 和 BroadcastReceiver 监听。
 * 独立类，不继承 BasicBluetoothProfileManager。
 */
class ClassicScanner(private val context: Context) {

    companion object {
        private const val TAG = "ClassicScanner"
    }

    // ─── Internal scan state ───

    sealed interface ScanState {
        data object Idle : ScanState
        data object Scanning : ScanState
        data object Finished : ScanState
        data class Error(val message: String) : ScanState
    }

    // ─── State flows ───

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private val _devices = MutableStateFlow<List<ClassicDeviceResult>>(emptyList())
    val devices: StateFlow<List<ClassicDeviceResult>> = _devices.asStateFlow()

    // ─── Internal state ───

    private val deviceMap = mutableMapOf<String, ClassicDeviceResult>()
    private var receiverRegistered = false

    private val bluetoothManager: BluetoothManager?
        get() = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val adapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    // ─── BroadcastReceiver ───

    private val discoveryReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> handleDeviceFound(intent)
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> handleDiscoveryFinished()
            }
        }
    }

    // ─── Public API ───

    fun clearDevices() {
        deviceMap.clear()
        _devices.value = emptyList()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan(clearResults: Boolean = true) {
        val btAdapter = adapter
        if (btAdapter == null) {
            _state.value = ScanState.Error("BluetoothAdapter is null")
            return
        }

        if (!btAdapter.isEnabled) {
            _state.value = ScanState.Error("Bluetooth is not enabled")
            return
        }

        if (clearResults) {
            deviceMap.clear()
            _devices.value = emptyList()
        }

        // Register receiver for discovery broadcasts
        registerReceiver()

        // If already discovering, cancel first
        if (btAdapter.isDiscovering) {
            btAdapter.cancelDiscovery()
        }

        val started = btAdapter.startDiscovery()
        if (started) {
            _state.value = ScanState.Scanning
        } else {
            _state.value = ScanState.Error("Failed to start discovery (missing BLUETOOTH_SCAN permission?)")
            unregisterReceiver()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        try {
            adapter?.cancelDiscovery()
        } catch (e: Exception) {
            Log.w(TAG, "Error cancelling discovery", e)
        }

        unregisterReceiver()

        if (_state.value is ScanState.Scanning) {
            _state.value = ScanState.Finished
        }
    }

    // ─── Internal helpers ───

    private fun handleDeviceFound(intent: Intent) {
        val device: BluetoothDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            ?: return

        val address = device.address ?: return
        val name: String? = intent.getStringExtra(BluetoothDevice.EXTRA_NAME)
            ?: device.name

        val btClass: BluetoothClass? = intent.getParcelableExtra(BluetoothDevice.EXTRA_CLASS, BluetoothClass::class.java)

        val majorClass = btClass?.majorDeviceClass ?: 0
        val minorClass = btClass?.deviceClass ?: 0

        val deviceType = mapDeviceType(device.type)

        val result = ClassicDeviceResult(
            address = address,
            name = name,
            majorDeviceClass = majorClass,
            minorDeviceClass = minorClass,
            deviceType = deviceType
        )

        deviceMap[address] = result
        _devices.value = deviceMap.values.toList()
    }

    private fun handleDiscoveryFinished() {
        Log.d(TAG, "Discovery finished, found ${deviceMap.size} devices")
        _state.value = ScanState.Finished
        unregisterReceiver()
    }

    private fun mapDeviceType(type: Int): DeviceType {
        return when (type) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> DeviceType.Classic
            BluetoothDevice.DEVICE_TYPE_LE -> DeviceType.BLE
            BluetoothDevice.DEVICE_TYPE_DUAL -> DeviceType.Dual
            else -> DeviceType.Classic
        }
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(discoveryReceiver, filter)
        receiverRegistered = true
    }

    private fun unregisterReceiver() {
        if (!receiverRegistered) return
        try {
            context.unregisterReceiver(discoveryReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering receiver", e)
        }
        receiverRegistered = false
    }
}
