package top.expli.bluetoothtester.model

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 已配对设备管理 ViewModel。
 * 负责读取已配对设备列表、发起配对/取消配对操作，并监听配对状态变化。
 */
class PairedDevicesViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val TAG = "PairedDevicesVM"
    }

    // ─── BondState 枚举 ───

    enum class BondState {
        Bonding, Bonded, Failed, None
    }

    // ─── State flows ───

    private val _pairedDevices = MutableStateFlow<List<PairedDeviceInfo>>(emptyList())
    val pairedDevices: StateFlow<List<PairedDeviceInfo>> = _pairedDevices.asStateFlow()

    private val _bondingState = MutableStateFlow<Map<String, BondState>>(emptyMap())
    val bondingState: StateFlow<Map<String, BondState>> = _bondingState.asStateFlow()

    // ─── Bluetooth references ───

    private val bluetoothManager: BluetoothManager?
        get() = getApplication<Application>()
            .getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val adapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    // ─── BroadcastReceiver for bond state changes ───

    private val bondStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return

            @Suppress("DEPRECATION")
            val device: BluetoothDevice =
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) ?: return
            val bondState = intent.getIntExtra(
                BluetoothDevice.EXTRA_BOND_STATE,
                BluetoothDevice.BOND_NONE
            )

            val address = device.address ?: return
            val mapped = mapBondState(bondState)

            Log.d(TAG, "Bond state changed: $address -> $mapped")

            _bondingState.value = _bondingState.value.toMutableMap().apply {
                this[address] = mapped
            }

            // Refresh paired devices list after bond state changes
            try {
                refreshPairedDevices()
            } catch (e: SecurityException) {
                Log.w(TAG, "Missing BLUETOOTH_CONNECT permission for refresh", e)
            }
        }
    }

    // ─── Init ───

    init {
        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        getApplication<Application>().registerReceiver(bondStateReceiver, filter)

        try {
            refreshPairedDevices()
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing BLUETOOTH_CONNECT permission on init", e)
        }
    }

    // ─── Public API ───

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun refreshPairedDevices() {
        val bonded = adapter?.bondedDevices ?: emptySet()
        _pairedDevices.value = bonded.map { device ->
            PairedDeviceInfo(
                device = device,
                name = device.name,
                address = device.address,
                deviceType = mapDeviceType(device.type),
                uuids = device.uuids?.map { it.uuid }?.toList() ?: emptyList()
            )
        }
    }

    @SuppressLint("DiscouragedPrivateApi")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun removeBond(device: BluetoothDevice): Result<Unit> {
        return try {
            val method = device.javaClass.getMethod("removeBond")
            val result = method.invoke(device) as? Boolean ?: false
            if (result) {
                Result.success(Unit)
            } else {
                Result.failure(RuntimeException("removeBond() returned false"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "removeBond reflection failed", e)
            Result.failure(e)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun createBond(device: BluetoothDevice) {
        device.createBond()
    }

    // ─── Lifecycle ───

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<Application>().unregisterReceiver(bondStateReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering bond state receiver", e)
        }
    }

    // ─── Internal helpers ───

    private fun mapBondState(state: Int): BondState {
        return when (state) {
            BluetoothDevice.BOND_BONDING -> BondState.Bonding
            BluetoothDevice.BOND_BONDED -> BondState.Bonded
            BluetoothDevice.BOND_NONE -> BondState.None
            else -> BondState.Failed
        }
    }

    private fun mapDeviceType(type: Int): DeviceType {
        return when (type) {
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> DeviceType.Classic
            BluetoothDevice.DEVICE_TYPE_LE -> DeviceType.BLE
            BluetoothDevice.DEVICE_TYPE_DUAL -> DeviceType.Dual
            else -> DeviceType.Classic
        }
    }
}
