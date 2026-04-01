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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.expli.bluetoothtester.bluetooth.AdvertiserManager

class AdvertiserViewModel(app: Application) : AndroidViewModel(app) {

    private val manager = AdvertiserManager(app.applicationContext)

    private val _uiState = MutableStateFlow(AdvertiserUiState())
    val uiState: StateFlow<AdvertiserUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                manager.state,
                manager.durationMs
            ) { advState, duration ->
                advState to duration
            }.collect { (advState, duration) ->
                _uiState.update { state ->
                    state.copy(
                        state = advState.toUiState(),
                        durationMs = duration,
                        lastError = when (advState) {
                            is AdvertiserManager.AdvState.Error -> advState.message
                            else -> state.lastError
                        }
                    )
                }
            }
        }
    }

    // ─── Public API ───

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun startAdvertising(config: AdvertiseConfig) {
        _uiState.update { it.copy(lastError = null, config = config) }
        // TODO: ForegroundService integration — call addReason(Reason.Advertising)
        manager.startAdvertising(config)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun stopAdvertising() {
        manager.stopAdvertising()
        // TODO: ForegroundService integration — call removeReason(Reason.Advertising)
        _uiState.update { it.copy(lastError = null) }
    }

    fun updateConfig(config: AdvertiseConfig) {
        _uiState.update { it.copy(config = config) }
    }

    fun updateMode(mode: Int) {
        _uiState.update { it.copy(config = it.config.copy(mode = mode)) }
    }

    fun updateTxPowerLevel(level: Int) {
        _uiState.update { it.copy(config = it.config.copy(txPowerLevel = level)) }
    }

    fun updateConnectable(connectable: Boolean) {
        _uiState.update { it.copy(config = it.config.copy(connectable = connectable)) }
    }

    fun updateIncludeName(include: Boolean) {
        _uiState.update { it.copy(config = it.config.copy(includeName = include)) }
    }

    fun updateServiceUuids(uuids: List<String>) {
        _uiState.update { it.copy(config = it.config.copy(serviceUuids = uuids)) }
    }

    fun updateManufacturerData(data: Map<Int, String>) {
        _uiState.update { it.copy(config = it.config.copy(manufacturerData = data)) }
    }

    fun updateScanResponseServiceUuids(uuids: List<String>) {
        _uiState.update { it.copy(config = it.config.copy(scanResponseServiceUuids = uuids)) }
    }

    fun updateScanResponseManufacturerData(data: Map<Int, String>) {
        _uiState.update { it.copy(config = it.config.copy(scanResponseManufacturerData = data)) }
    }

    fun clearError() {
        _uiState.update { it.copy(lastError = null) }
    }

    // ─── Lifecycle ───

    override fun onCleared() {
        super.onCleared()
        try {
            manager.stopAdvertising()
        } catch (_: SecurityException) {
            // Permission may have been revoked while advertising
        }
    }

    // ─── Helpers ───

    private fun AdvertiserManager.AdvState.toUiState(): AdvertiserState {
        return when (this) {
            AdvertiserManager.AdvState.Idle -> AdvertiserState.Idle
            AdvertiserManager.AdvState.Advertising -> AdvertiserState.Advertising
            is AdvertiserManager.AdvState.Error -> AdvertiserState.Error(errorCode, message)
        }
    }
}
