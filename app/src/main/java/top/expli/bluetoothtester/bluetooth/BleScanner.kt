package top.expli.bluetoothtester.bluetooth

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.expli.bluetoothtester.model.BleDeviceResult
import top.expli.bluetoothtester.model.BleScanFilter
import top.expli.bluetoothtester.model.BleScanRecordData
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * BLE 扫描工具类，封装 BluetoothLeScanner 的扫描生命周期。
 * 独立类，不继承 BasicBluetoothProfileManager。
 */
class BleScanner(private val context: Context) {

    companion object {
        private const val TAG = "BleScanner"
        private const val AUTO_STOP_DELAY_MS = 30_000L
        internal const val FLUSH_INTERVAL_MS = 300L
    }

    // ─── Internal scan state ───

    sealed interface ScanState {
        data object Idle : ScanState
        data object Scanning : ScanState
        data object Stopped : ScanState
        data class Error(val message: String) : ScanState
    }

    // ─── Internal runtime device entry ───

    data class BleDeviceEntry(
        val device: BluetoothDevice,
        val name: String?,
        val rssi: Int,
        val scanRecord: BleScanRecordData,
        val lastSeenMs: Long,
        val cachedRawHex: String,
        val cachedManufacturerHex: Map<Int, String>
    )

    // ─── State flows ───

    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private val _devices = MutableStateFlow<List<BleDeviceResult>>(emptyList())
    val devices: StateFlow<List<BleDeviceResult>> = _devices.asStateFlow()

    // ─── Internal state ───

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val deviceMap = ConcurrentHashMap<String, BleDeviceEntry>()
    private var currentFilter: BleScanFilter? = null
    private var autoStopJob: Job? = null
    private var flushJob: Job? = null

    private val bluetoothManager: BluetoothManager?
        get() = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val adapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val scanner: BluetoothLeScanner?
        get() = adapter?.bluetoothLeScanner

    // ─── Scan callback ───

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed with error code: $errorCode")
            _state.value = ScanState.Error("Scan failed: error code $errorCode")
            autoStopJob?.cancel()
            flushJob?.cancel()
            flushJob = null
        }
    }

    // ─── Public API ───

    fun clearDevices() {
        deviceMap.clear()
        _devices.value = emptyList()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan(filter: BleScanFilter? = null, clearResults: Boolean = true) {
        val bleScanner = scanner
        if (adapter == null) {
            _state.value = ScanState.Error("BluetoothAdapter is null")
            return
        }
        if (bleScanner == null) {
            _state.value = ScanState.Error("BluetoothLeScanner is null (Bluetooth may be off)")
            return
        }

        // Reset state for new scan
        currentFilter = filter
        if (clearResults) {
            deviceMap.clear()
            _devices.value = emptyList()
        }
        flushJob?.cancel()
        flushJob = null
        _state.value = ScanState.Scanning

        // Start BLE scan with explicit settings for reliable results
        // Using SCAN_MODE_LOW_LATENCY for active scanning; no Android-level ScanFilter (filtering in-memory)
        val settings = android.bluetooth.le.ScanSettings.Builder()
            .setScanMode(android.bluetooth.le.ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        bleScanner.startScan(null, settings, scanCallback)

        // 30-second auto-stop timer
        autoStopJob?.cancel()
        autoStopJob = scope.launch {
            delay(AUTO_STOP_DELAY_MS)
            stopScan()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        autoStopJob?.cancel()
        autoStopJob = null
        flushJob?.cancel()
        flushJob = null

        try {
            scanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping scan", e)
        }

        if (_state.value is ScanState.Scanning) {
            _state.value = ScanState.Stopped
        }

        // Final flush to ensure last batch of data is not lost
        scope.launch(Dispatchers.Main.immediate) { flushDeviceList() }
    }

    // ─── Internal helpers ───

    @VisibleForTesting
    internal fun handleScanResult(result: ScanResult) {
        val device = result.device
        val address = device.address
        val scanRecordData = parseScanRecord(result)

        val entry = BleDeviceEntry(
            device = device,
            name = result.scanRecord?.deviceName ?: device.name,
            rssi = result.rssi,
            scanRecord = scanRecordData,
            lastSeenMs = System.currentTimeMillis(),
            cachedRawHex = scanRecordData.rawBytes.joinToString("") { "%02X".format(it) },
            cachedManufacturerHex = scanRecordData.manufacturerData.mapValues { (_, bytes) ->
                bytes.joinToString("") { "%02X".format(it) }
            }
        )
        deviceMap[address] = entry

        // Throttle: only schedule a flush if one isn't already pending
        if (flushJob?.isActive != true) {
            flushJob = scope.launch {
                delay(FLUSH_INTERVAL_MS)
                flushDeviceList()
            }
        }
    }

    private fun parseScanRecord(result: ScanResult): BleScanRecordData {
        val record = result.scanRecord
        val serviceUuids: List<UUID> = record?.serviceUuids
            ?.map { it.uuid } ?: emptyList()

        val manufacturerData = mutableMapOf<Int, ByteArray>()
        record?.manufacturerSpecificData?.let { sparseArray ->
            for (i in 0 until sparseArray.size()) {
                val key = sparseArray.keyAt(i)
                val value = sparseArray.valueAt(i)
                manufacturerData[key] = value
            }
        }

        val txPowerLevel: Int? = record?.txPowerLevel?.let {
            if (it == Int.MIN_VALUE) null else it
        }

        val rawBytes: ByteArray = record?.bytes ?: byteArrayOf()

        return BleScanRecordData(
            serviceUuids = serviceUuids,
            manufacturerData = manufacturerData,
            txPowerLevel = txPowerLevel,
            rawBytes = rawBytes
        )
    }

    @VisibleForTesting
    internal fun flushDeviceList() {
        val snapshot = HashMap(deviceMap)
        _devices.value = buildFilteredDeviceList(snapshot)
    }

    @VisibleForTesting
    internal fun buildFilteredDeviceList(snapshot: Map<String, BleDeviceEntry> = HashMap(deviceMap)): List<BleDeviceResult> {
        val filter = currentFilter
        return snapshot.values
            .filter { entry -> matchesFilter(entry, filter) }
            .map { entry -> entry.toBleDeviceResult() }
    }

    @VisibleForTesting
    internal fun matchesFilter(entry: BleDeviceEntry, filter: BleScanFilter?): Boolean {
        if (filter == null) return true

        // Name keyword fuzzy match
        filter.nameKeyword?.let { keyword ->
            if (keyword.isNotBlank()) {
                val name = entry.name ?: ""
                if (!name.contains(keyword, ignoreCase = true)) return false
            }
        }

        // UUID contains match
        filter.serviceUuid?.let { targetUuid ->
            if (entry.scanRecord.serviceUuids.none { it == targetUuid }) return false
        }

        // RSSI threshold comparison
        filter.rssiThreshold?.let { threshold ->
            if (entry.rssi < threshold) return false
        }

        return true
    }

    @VisibleForTesting
    internal fun BleDeviceEntry.toBleDeviceResult(): BleDeviceResult {
        return BleDeviceResult(
            address = device.address,
            name = name,
            rssi = rssi,
            serviceUuids = scanRecord.serviceUuids.map { it.toString() },
            manufacturerData = cachedManufacturerHex,
            txPowerLevel = scanRecord.txPowerLevel,
            rawScanRecord = cachedRawHex,
            lastSeenTimestamp = lastSeenMs
        )
    }
}
