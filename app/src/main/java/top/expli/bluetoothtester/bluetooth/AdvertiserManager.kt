package top.expli.bluetoothtester.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.expli.bluetoothtester.model.AdvertiseConfig
import java.util.UUID

/**
 * BLE 广播管理器，独立类，不继承 BasicBluetoothProfileManager。
 */
class AdvertiserManager(private val context: Context) {

    companion object {
        private const val TAG = "AdvertiserManager"
    }

    sealed interface AdvState {
        data object Idle : AdvState
        data object Advertising : AdvState
        data class Error(val errorCode: Int, val message: String) : AdvState
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow<AdvState>(AdvState.Idle)
    val state: StateFlow<AdvState> = _state.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val bluetoothManager: BluetoothManager?
        get() = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val adapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val advertiser: BluetoothLeAdvertiser?
        get() = adapter?.bluetoothLeAdvertiser

    private var timerJob: Job? = null
    private var startTimeMs: Long = 0L

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(TAG, "Advertising started successfully")
            _state.value = AdvState.Advertising
            startDurationTimer()
        }

        override fun onStartFailure(errorCode: Int) {
            val message = when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "广播数据过大"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "广播实例过多"
                ADVERTISE_FAILED_ALREADY_STARTED -> "广播已在运行"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "内部错误"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "设备不支持广播"
                else -> "未知错误 ($errorCode)"
            }
            Log.e(TAG, "Advertising failed: $message (code=$errorCode)")
            _state.value = AdvState.Error(errorCode, message)
        }
    }

    // ─── Public API ───

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun startAdvertising(config: AdvertiseConfig) {
        val bleAdvertiser = advertiser
        if (adapter == null) {
            _state.value = AdvState.Error(-1, "BluetoothAdapter 不可用")
            return
        }
        if (bleAdvertiser == null) {
            _state.value = AdvState.Error(-1, "BluetoothLeAdvertiser 不可用（蓝牙可能未开启）")
            return
        }

        // Build AdvertiseSettings
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(config.mode)
            .setTxPowerLevel(config.txPowerLevel)
            .setConnectable(config.connectable)
            .setTimeout(0) // No timeout, manual stop
            .build()

        // Build AdvertiseData
        val dataBuilder = AdvertiseData.Builder()
            .setIncludeDeviceName(config.includeName)
            .setIncludeTxPowerLevel(false)

        config.serviceUuids.forEach { uuidStr ->
            try {
                dataBuilder.addServiceUuid(ParcelUuid(UUID.fromString(uuidStr)))
            } catch (_: IllegalArgumentException) {
                Log.w(TAG, "Invalid service UUID: $uuidStr")
            }
        }

        config.manufacturerData.forEach { (companyId, hexStr) ->
            val bytes = hexStringToByteArray(hexStr)
            if (bytes != null) {
                dataBuilder.addManufacturerData(companyId, bytes)
            }
        }

        val advertiseData = dataBuilder.build()

        // Build Scan Response (optional)
        val scanResponse = buildScanResponse(config)

        // Reset duration
        _durationMs.value = 0L

        try {
            if (scanResponse != null) {
                bleAdvertiser.startAdvertising(settings, advertiseData, scanResponse, advertiseCallback)
            } else {
                bleAdvertiser.startAdvertising(settings, advertiseData, advertiseCallback)
            }
        } catch (e: Exception) {
            _state.value = AdvState.Error(-1, e.message ?: "启动广播异常")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun stopAdvertising() {
        timerJob?.cancel()
        timerJob = null

        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping advertising", e)
        }

        _state.value = AdvState.Idle
        _durationMs.value = 0L
    }

    // ─── Internal ───

    private fun startDurationTimer() {
        timerJob?.cancel()
        startTimeMs = System.currentTimeMillis()
        timerJob = scope.launch {
            while (isActive) {
                _durationMs.value = System.currentTimeMillis() - startTimeMs
                delay(1000L)
            }
        }
    }

    private fun buildScanResponse(config: AdvertiseConfig): AdvertiseData? {
        if (config.scanResponseServiceUuids.isEmpty() &&
            config.scanResponseManufacturerData.isEmpty()
        ) return null

        val builder = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)

        config.scanResponseServiceUuids.forEach { uuidStr ->
            try {
                builder.addServiceUuid(ParcelUuid(UUID.fromString(uuidStr)))
            } catch (_: IllegalArgumentException) {
                Log.w(TAG, "Invalid scan response UUID: $uuidStr")
            }
        }

        config.scanResponseManufacturerData.forEach { (companyId, hexStr) ->
            val bytes = hexStringToByteArray(hexStr)
            if (bytes != null) {
                builder.addManufacturerData(companyId, bytes)
            }
        }

        return builder.build()
    }

    private fun hexStringToByteArray(hex: String): ByteArray? {
        val cleaned = hex.replace(" ", "").replace(":", "")
        if (cleaned.isEmpty()) return byteArrayOf()
        if (cleaned.length % 2 != 0) return null
        return try {
            ByteArray(cleaned.length / 2) { i ->
                cleaned.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (_: NumberFormatException) {
            null
        }
    }
}
