package top.expli.bluetoothtester.model

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothManager
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.expli.bluetoothtester.bluetooth.GattServerManager
import java.util.UUID

class GattServerViewModel(app: Application) : AndroidViewModel(app) {

    private val adapter =
        app.getSystemService(BluetoothManager::class.java)?.adapter

    private val manager = GattServerManager(app.applicationContext)

    private val _uiState = MutableStateFlow(GattServerUiState())
    val uiState: StateFlow<GattServerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                manager.serverState,
                manager.activeServices,
                manager.connectedDevices,
                manager.requestLog
            ) { serverState, services, devices, log ->
                GattServerUiState(
                    serverState = serverState.toUiState(),
                    activeServices = services,
                    connectedDevices = devices.map { it.address },
                    log = log,
                    lastError = (serverState as? GattServerManager.ServerState.Error)?.message
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    // ─── Server Lifecycle ───

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun openServer() {
        // TODO: ForegroundService integration — call addReason(Reason.GattConnection)
        manager.openServer()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun closeServer() {
        // TODO: ForegroundService integration — call removeReason(Reason.GattConnection)
        manager.closeServer()
    }

    // ─── Service Management ───

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun addService(config: GattServiceConfig) {
        val result = manager.addService(config)
        if (!result) {
            _uiState.update { it.copy(lastError = "添加服务失败: ${config.serviceUuid}") }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun removeService(serviceUuid: String) {
        val uuid = parseUuid(serviceUuid) ?: return
        manager.removeService(uuid)
    }

    // ─── Characteristic Value ───

    fun updateCharacteristicValue(
        serviceUuid: String,
        charUuid: String,
        value: ByteArray
    ) {
        val sUuid = parseUuid(serviceUuid) ?: return
        val cUuid = parseUuid(charUuid) ?: return
        manager.updateCharacteristicValue(sUuid, cUuid, value)
    }

    // ─── Notification ───

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendNotification(
        serviceUuid: String,
        charUuid: String,
        deviceAddress: String,
        value: ByteArray
    ) {
        val sUuid = parseUuid(serviceUuid) ?: return
        val cUuid = parseUuid(charUuid) ?: return
        val device = try {
            adapter?.getRemoteDevice(deviceAddress)
        } catch (_: IllegalArgumentException) {
            _uiState.update { it.copy(lastError = "无效的蓝牙地址: $deviceAddress") }
            return
        }
        if (device == null) {
            _uiState.update { it.copy(lastError = "无法获取蓝牙设备") }
            return
        }
        val result = manager.sendNotification(sUuid, cUuid, device, value)
        if (!result) {
            _uiState.update { it.copy(lastError = "发送通知失败") }
        }
    }

    // ─── Lifecycle ───

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCleared() {
        super.onCleared()
        manager.closeServer()
    }

    // ─── Helpers ───

    private fun parseUuid(uuidStr: String): UUID? {
        return try {
            UUID.fromString(uuidStr)
        } catch (_: IllegalArgumentException) {
            _uiState.update { it.copy(lastError = "无效的 UUID 格式: $uuidStr") }
            null
        }
    }

    private fun GattServerManager.ServerState.toUiState(): GattServerState {
        return when (this) {
            GattServerManager.ServerState.Idle -> GattServerState.Idle
            GattServerManager.ServerState.Running -> GattServerState.Running
            is GattServerManager.ServerState.Error -> GattServerState.Error(message)
        }
    }
}
