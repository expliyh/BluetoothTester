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
import top.expli.bluetoothtester.bluetooth.GattClientManager
import top.expli.bluetoothtester.bluetooth.GattResult
import java.util.UUID

class GattClientViewModel(app: Application) : AndroidViewModel(app) {

    private val adapter =
        app.getSystemService(BluetoothManager::class.java)?.adapter

    private val manager = GattClientManager(app.applicationContext)

    private val _uiState = MutableStateFlow(GattClientUiState())
    val uiState: StateFlow<GattClientUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                manager.connectionState,
                manager.services,
                manager.operationLog
            ) { connState, services, log ->
                Triple(connState, services, log)
            }.collect { (connState, services, log) ->
                _uiState.update { state ->
                    state.copy(
                        connectionState = connState.toUiState(),
                        services = services,
                        log = log
                    )
                }
            }
        }
    }

    // ─── Route parameter support ───

    fun setTarget(address: String, name: String) {
        _uiState.update { it.copy(targetAddress = address) }
    }

    // ─── Connection ───

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(address: String, name: String) {
        val device = try {
            adapter?.getRemoteDevice(address)
        } catch (_: IllegalArgumentException) {
            _uiState.update { it.copy(lastError = "无效的蓝牙地址: $address") }
            return
        }
        if (device == null) {
            _uiState.update { it.copy(lastError = "无法获取蓝牙设备") }
            return
        }
        _uiState.update { it.copy(targetAddress = address, lastError = null) }
        // TODO: ForegroundService integration — call addReason(Reason.GattConnection)
        manager.connect(device)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        // TODO: ForegroundService integration — call removeReason(Reason.GattConnection)
        manager.disconnect()
    }

    // ─── Service Discovery ───

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun discoverServices() {
        viewModelScope.launch {
            handleResult(manager.discoverServices())
        }
    }

    // ─── Characteristic Operations ───

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun readCharacteristic(serviceUuid: String, charUuid: String) {
        val sUuid = parseUuid(serviceUuid) ?: return
        val cUuid = parseUuid(charUuid) ?: return
        viewModelScope.launch {
            handleResult(manager.readCharacteristic(sUuid, cUuid))
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun writeCharacteristic(
        serviceUuid: String,
        charUuid: String,
        data: ByteArray,
        writeType: Int
    ) {
        val sUuid = parseUuid(serviceUuid) ?: return
        val cUuid = parseUuid(charUuid) ?: return
        viewModelScope.launch {
            handleResult(manager.writeCharacteristic(sUuid, cUuid, data, writeType))
        }
    }

    // ─── Descriptor Operations ───

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun readDescriptor(serviceUuid: String, charUuid: String, descUuid: String) {
        val sUuid = parseUuid(serviceUuid) ?: return
        val cUuid = parseUuid(charUuid) ?: return
        val dUuid = parseUuid(descUuid) ?: return
        viewModelScope.launch {
            handleResult(manager.readDescriptor(sUuid, cUuid, dUuid))
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun writeDescriptor(
        serviceUuid: String,
        charUuid: String,
        descUuid: String,
        data: ByteArray
    ) {
        val sUuid = parseUuid(serviceUuid) ?: return
        val cUuid = parseUuid(charUuid) ?: return
        val dUuid = parseUuid(descUuid) ?: return
        viewModelScope.launch {
            handleResult(manager.writeDescriptor(sUuid, cUuid, dUuid, data))
        }
    }

    // ─── Notification ───

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun setNotification(serviceUuid: String, charUuid: String, enable: Boolean) {
        val sUuid = parseUuid(serviceUuid) ?: return
        val cUuid = parseUuid(charUuid) ?: return
        viewModelScope.launch {
            handleResult(manager.setNotification(sUuid, cUuid, enable))
        }
    }

    // ─── MTU ───

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun requestMtu(mtu: Int) {
        viewModelScope.launch {
            val result = manager.requestMtu(mtu)
            when (result) {
                is GattResult.Success -> {
                    _uiState.update { it.copy(currentMtu = result.value, lastError = null) }
                }
                is GattResult.Error -> {
                    _uiState.update { it.copy(lastError = "MTU 协商失败: ${result.message}") }
                }
                is GattResult.Timeout -> {
                    _uiState.update { it.copy(lastError = "MTU 协商超时") }
                }
            }
        }
    }

    // ─── Lifecycle ───

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCleared() {
        super.onCleared()
        manager.close()
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

    private fun <T> handleResult(result: GattResult<T>) {
        when (result) {
            is GattResult.Success -> {
                _uiState.update { it.copy(lastError = null) }
            }
            is GattResult.Error -> {
                _uiState.update { it.copy(lastError = "操作失败 (status=${result.status}): ${result.message}") }
            }
            is GattResult.Timeout -> {
                _uiState.update { it.copy(lastError = "操作超时: ${result.operationName}") }
            }
        }
    }

    private fun GattClientManager.ConnectionState.toUiState(): GattConnectionState {
        return when (this) {
            GattClientManager.ConnectionState.Idle -> GattConnectionState.Idle
            GattClientManager.ConnectionState.Connecting -> GattConnectionState.Connecting
            GattClientManager.ConnectionState.Connected -> GattConnectionState.Connected
            GattClientManager.ConnectionState.Disconnected -> GattConnectionState.Disconnected
            is GattClientManager.ConnectionState.Error -> GattConnectionState.Error(status)
        }
    }
}
