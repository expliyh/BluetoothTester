package top.expli.bluetoothtester.model

import android.Manifest
import android.app.Application
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.expli.bluetoothtester.bluetooth.BleScanner
import top.expli.bluetoothtester.bluetooth.ClassicScanner

class ScanViewModel(app: Application) : AndroidViewModel(app) {

    val bleScanner = BleScanner(app.applicationContext)
    val classicScanner = ClassicScanner(app.applicationContext)

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val _filter = MutableStateFlow(BleScanFilter())

    init {
        // Collect from all scanner flows and update UI state
        viewModelScope.launch {
            combine(
                bleScanner.state,
                bleScanner.devices,
                classicScanner.state,
                classicScanner.devices,
                _filter
            ) { values ->
                val bleState = values[0] as BleScanner.ScanState
                @Suppress("UNCHECKED_CAST")
                val bleDevices = values[1] as List<BleDeviceResult>
                val classicState = values[2] as ClassicScanner.ScanState
                @Suppress("UNCHECKED_CAST")
                val classicDevices = values[3] as List<ClassicDeviceResult>
                val filter = values[4] as BleScanFilter

                val mappedBleState = mapBleScanState(bleState)
                val mappedClassicState = mapClassicScanState(classicState)
                val combined = buildCombinedDevices(bleDevices, classicDevices)

                // Clear warning when both scans are stopped
                val bothStopped = bleState !is BleScanner.ScanState.Scanning &&
                        classicState !is ClassicScanner.ScanState.Scanning
                val currentWarning = _uiState.value.showClassicAffectsBleWarning

                ScanUiState(
                    bleScanState = mappedBleState,
                    classicScanState = mappedClassicState,
                    combinedDevices = combined,
                    filter = filter,
                    showClassicAffectsBleWarning = if (bothStopped) false else currentWarning
                )
            }.distinctUntilChanged { old, new -> old.equalsIgnoreTimestamp(new) }
            .collect { newState ->
                _uiState.value = newState
            }
        }
    }

    // ─── Public API ───

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startBleScan(filter: BleScanFilter? = null) {
        // Bidirectional warning: check if classic scan is running
        if (classicScanner.state.value is ClassicScanner.ScanState.Scanning) {
            _uiState.update { it.copy(showClassicAffectsBleWarning = true) }
        }
        if (filter != null) {
            _filter.value = filter
        }
        bleScanner.startScan(filter)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopBleScan() {
        bleScanner.stopScan()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startClassicScan() {
        // Bidirectional warning: check if BLE scan is running
        if (bleScanner.state.value is BleScanner.ScanState.Scanning) {
            _uiState.update { it.copy(showClassicAffectsBleWarning = true) }
        }
        classicScanner.startScan()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopClassicScan() {
        classicScanner.stopScan()
    }

    // ─── Combined devices merge logic ───

    @androidx.annotation.VisibleForTesting
    internal fun buildCombinedDevices(
        bleDevices: List<BleDeviceResult>,
        classicDevices: List<ClassicDeviceResult>
    ): List<UnifiedDeviceResult> {
        val resultMap = mutableMapOf<String, UnifiedDeviceResult>()

        // Add BLE devices
        for (ble in bleDevices) {
            resultMap[ble.address] = UnifiedDeviceResult(
                address = ble.address,
                name = ble.name,
                deviceType = DeviceType.BLE,
                rssi = ble.rssi,
                bleData = ble,
                classicData = null
            )
        }

        // Add Classic devices, merging with existing BLE entries
        for (classic in classicDevices) {
            val existing = resultMap[classic.address]
            if (existing != null) {
                // Same address found in both BLE and Classic → mark as Dual
                resultMap[classic.address] = existing.copy(
                    deviceType = DeviceType.Dual,
                    name = existing.name ?: classic.name,
                    classicData = classic
                )
            } else {
                resultMap[classic.address] = UnifiedDeviceResult(
                    address = classic.address,
                    name = classic.name,
                    deviceType = classic.deviceType,
                    rssi = null,
                    bleData = null,
                    classicData = classic
                )
            }
        }

        return resultMap.values.toList()
    }

    // ─── State mapping ───

    private fun mapBleScanState(state: BleScanner.ScanState): BleScanState {
        return when (state) {
            is BleScanner.ScanState.Idle -> BleScanState.Idle
            is BleScanner.ScanState.Scanning -> BleScanState.Scanning
            is BleScanner.ScanState.Stopped -> BleScanState.Stopped
            is BleScanner.ScanState.Error -> BleScanState.Error(state.message)
        }
    }

    private fun mapClassicScanState(state: ClassicScanner.ScanState): ClassicScanState {
        return when (state) {
            is ClassicScanner.ScanState.Idle -> ClassicScanState.Idle
            is ClassicScanner.ScanState.Scanning -> ClassicScanState.Scanning
            is ClassicScanner.ScanState.Finished -> ClassicScanState.Finished
            is ClassicScanner.ScanState.Error -> ClassicScanState.Error(state.message)
        }
    }
}

// ─── Extension functions for content-level equality (ignoring lastSeenTimestamp) ───

internal fun ScanUiState.equalsIgnoreTimestamp(other: ScanUiState): Boolean {
    if (bleScanState != other.bleScanState) return false
    if (classicScanState != other.classicScanState) return false
    if (filter != other.filter) return false
    if (showClassicAffectsBleWarning != other.showClassicAffectsBleWarning) return false
    if (combinedDevices.size != other.combinedDevices.size) return false
    return combinedDevices.zip(other.combinedDevices).all { (a, b) ->
        a.address == b.address &&
        a.name == b.name &&
        a.deviceType == b.deviceType &&
        a.rssi == b.rssi &&
        a.classicData == b.classicData &&
        a.bleData?.equalsIgnoreTimestamp(b.bleData) ?: (b.bleData == null)
    }
}

internal fun BleDeviceResult.equalsIgnoreTimestamp(other: BleDeviceResult?): Boolean {
    if (other == null) return false
    return address == other.address &&
        name == other.name &&
        rssi == other.rssi &&
        serviceUuids == other.serviceUuids &&
        manufacturerData == other.manufacturerData &&
        txPowerLevel == other.txPowerLevel &&
        rawScanRecord == other.rawScanRecord
        // lastSeenTimestamp is intentionally excluded
}
