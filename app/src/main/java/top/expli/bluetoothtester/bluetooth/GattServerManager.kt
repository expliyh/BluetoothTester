package top.expli.bluetoothtester.bluetooth

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import top.expli.bluetoothtester.model.GattServiceConfig
import top.expli.bluetoothtester.model.GattServerEvent
import top.expli.bluetoothtester.model.GattServerLogEntry
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Independent GATT Server Manager — does NOT inherit from any base class.
 * Manages a BluetoothGattServer, handles read/write requests, notifications,
 * and CCCD subscriptions.
 */
class GattServerManager(private val context: Context) {

    companion object {
        private const val TAG = "GattServerManager"
        /** Standard Client Characteristic Configuration Descriptor UUID */
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    // ─── Server State ───

    sealed interface ServerState {
        data object Idle : ServerState
        data object Running : ServerState
        data class Error(val message: String) : ServerState
    }

    // ─── State Flows ───

    private val _serverState = MutableStateFlow<ServerState>(ServerState.Idle)
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

    private val _connectedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val connectedDevices: StateFlow<List<BluetoothDevice>> = _connectedDevices.asStateFlow()

    private val _activeServices = MutableStateFlow<List<GattServiceConfig>>(emptyList())
    val activeServices: StateFlow<List<GattServiceConfig>> = _activeServices.asStateFlow()

    private val _requestLog = MutableStateFlow<List<GattServerLogEntry>>(emptyList())
    val requestLog: StateFlow<List<GattServerLogEntry>> = _requestLog.asStateFlow()

    // ─── Internal State ───

    private var gattServer: BluetoothGattServer? = null
    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    private val logIdCounter = AtomicLong(0)

    /** Stored characteristic values: key = "serviceUuid/charUuid" */
    private val characteristicValues = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()

    /** CCCD subscriptions: key = charUuid string, value = set of subscribed devices */
    private val cccdSubscriptions = java.util.concurrent.ConcurrentHashMap<String, MutableSet<BluetoothDevice>>()

    // ─── Helpers ───

    private fun charKey(serviceUuid: UUID, charUuid: UUID): String =
        "${serviceUuid}/${charUuid}"

    private fun charKey(serviceUuid: String, charUuid: String): String =
        "${serviceUuid}/${charUuid}"

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02X".format(it) }

    private fun String.hexToByteArray(): ByteArray {
        if (isEmpty()) return byteArrayOf()
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun addLogEntry(
        eventType: GattServerEvent,
        deviceAddress: String,
        serviceUuid: String? = null,
        characteristicUuid: String? = null,
        requestId: Int? = null,
        data: String? = null,
        offset: Int? = null
    ) {
        val entry = GattServerLogEntry(
            id = logIdCounter.incrementAndGet(),
            timestamp = System.currentTimeMillis(),
            eventType = eventType,
            deviceAddress = deviceAddress,
            serviceUuid = serviceUuid,
            characteristicUuid = characteristicUuid,
            requestId = requestId,
            data = data,
            offset = offset
        )
        _requestLog.update { it + entry }
    }

    // ─── BluetoothGattServerCallback ───

    private val gattServerCallback = object : BluetoothGattServerCallback() {

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange: device=${device.address}, status=$status, newState=$newState")
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    _connectedDevices.update { current ->
                        if (current.none { it.address == device.address }) current + device else current
                    }
                    addLogEntry(
                        eventType = GattServerEvent.DeviceConnected,
                        deviceAddress = device.address
                    )
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectedDevices.update { current ->
                        current.filter { it.address != device.address }
                    }
                    // Remove device from all CCCD subscriptions
                    cccdSubscriptions.values.forEach { it.remove(device) }
                    addLogEntry(
                        eventType = GattServerEvent.DeviceDisconnected,
                        deviceAddress = device.address
                    )
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic
        ) {
            Log.d(TAG, "onCharacteristicReadRequest: device=${device.address}, char=${characteristic.uuid}, offset=$offset")
            val serviceUuid = characteristic.service.uuid.toString()
            val charUuid = characteristic.uuid.toString()
            val key = charKey(serviceUuid, charUuid)
            val value = characteristicValues[key] ?: byteArrayOf()

            addLogEntry(
                eventType = GattServerEvent.ReadRequest,
                deviceAddress = device.address,
                serviceUuid = serviceUuid,
                characteristicUuid = charUuid,
                requestId = requestId,
                data = value.toHexString(),
                offset = offset
            )

            val responseValue = if (offset > value.size) {
                byteArrayOf()
            } else {
                value.copyOfRange(offset, value.size)
            }

            gattServer?.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                offset,
                responseValue
            )
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            Log.d(TAG, "onCharacteristicWriteRequest: device=${device.address}, char=${characteristic.uuid}, offset=$offset")
            val serviceUuid = characteristic.service.uuid.toString()
            val charUuid = characteristic.uuid.toString()
            val key = charKey(serviceUuid, charUuid)
            val writeData = value ?: byteArrayOf()

            // Update stored value
            characteristicValues[key] = writeData

            addLogEntry(
                eventType = GattServerEvent.WriteRequest,
                deviceAddress = device.address,
                serviceUuid = serviceUuid,
                characteristicUuid = charUuid,
                requestId = requestId,
                data = writeData.toHexString(),
                offset = offset
            )

            if (responseNeeded) {
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    writeData
                )
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onDescriptorReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            descriptor: BluetoothGattDescriptor
        ) {
            Log.d(TAG, "onDescriptorReadRequest: device=${device.address}, desc=${descriptor.uuid}")
            val value = if (descriptor.uuid == CCCD_UUID) {
                val charUuid = descriptor.characteristic.uuid.toString()
                val subscribed = cccdSubscriptions[charUuid]?.contains(device) == true
                if (subscribed) {
                    val hasIndicate = (descriptor.characteristic.properties and
                            BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                    if (hasIndicate) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                } else {
                    BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                }
            } else {
                descriptor.value ?: byteArrayOf()
            }

            gattServer?.sendResponse(
                device,
                requestId,
                BluetoothGatt.GATT_SUCCESS,
                offset,
                value
            )
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            Log.d(TAG, "onDescriptorWriteRequest: device=${device.address}, desc=${descriptor.uuid}")
            if (descriptor.uuid == CCCD_UUID) {
                val charUuid = descriptor.characteristic.uuid.toString()
                val writeValue = value ?: byteArrayOf()
                if (writeValue.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                    writeValue.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                ) {
                    cccdSubscriptions.getOrPut(charUuid) { java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap()) }.add(device)
                    Log.d(TAG, "CCCD subscription enabled for char=$charUuid, device=${device.address}")
                } else {
                    cccdSubscriptions[charUuid]?.remove(device)
                    Log.d(TAG, "CCCD subscription disabled for char=$charUuid, device=${device.address}")
                }
            }

            if (responseNeeded) {
                gattServer?.sendResponse(
                    device,
                    requestId,
                    BluetoothGatt.GATT_SUCCESS,
                    offset,
                    value
                )
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            Log.d(TAG, "onNotificationSent: device=${device.address}, status=$status")
            addLogEntry(
                eventType = GattServerEvent.NotificationSent,
                deviceAddress = device.address,
                data = "status=$status"
            )
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            Log.d(TAG, "onMtuChanged: device=${device.address}, mtu=$mtu")
            addLogEntry(
                eventType = GattServerEvent.MtuChanged,
                deviceAddress = device.address,
                data = "mtu=$mtu"
            )
        }
    }

    // ─── Service Config → BluetoothGattService Conversion ───

    private fun buildGattService(config: GattServiceConfig): BluetoothGattService {
        val service = BluetoothGattService(
            UUID.fromString(config.serviceUuid),
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        for (charConfig in config.characteristics) {
            val characteristic = BluetoothGattCharacteristic(
                UUID.fromString(charConfig.uuid),
                charConfig.properties,
                charConfig.permissions
            )

            // Set initial value
            val initialValue = charConfig.initialValue.hexToByteArray()
            if (initialValue.isNotEmpty()) {
                characteristic.value = initialValue
            }
            // Store initial value in our tracking map
            characteristicValues[charKey(config.serviceUuid, charConfig.uuid)] = initialValue

            // Add user-defined descriptors
            for (descConfig in charConfig.descriptors) {
                val descriptor = BluetoothGattDescriptor(
                    UUID.fromString(descConfig.uuid),
                    descConfig.permissions
                )
                if (descConfig.initialValue.isNotEmpty()) {
                    descriptor.value = descConfig.initialValue.hexToByteArray()
                }
                characteristic.addDescriptor(descriptor)
            }

            // Always add CCCD descriptor for characteristics with NOTIFY or INDICATE property
            val hasNotify = (charConfig.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0
            val hasIndicate = (charConfig.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
            if (hasNotify || hasIndicate) {
                // Only add CCCD if not already present from user-defined descriptors
                if (characteristic.getDescriptor(CCCD_UUID) == null) {
                    val cccd = BluetoothGattDescriptor(
                        CCCD_UUID,
                        BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
                    )
                    characteristic.addDescriptor(cccd)
                }
            }

            service.addCharacteristic(characteristic)
        }

        return service
    }

    // ─── Public API ───

    /**
     * Opens the BluetoothGattServer via BluetoothManager.openGattServer().
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun openServer() {
        if (_serverState.value is ServerState.Running) {
            Log.w(TAG, "Server already running, ignoring openServer call")
            return
        }

        try {
            gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
            if (gattServer != null) {
                _serverState.value = ServerState.Running
                Log.d(TAG, "GATT server opened successfully")
            } else {
                _serverState.value = ServerState.Error("Failed to open GATT server")
                Log.e(TAG, "openGattServer returned null")
            }
        } catch (e: Exception) {
            _serverState.value = ServerState.Error("Failed to open GATT server: ${e.message}")
            Log.e(TAG, "Error opening GATT server", e)
        }
    }

    /**
     * Creates a BluetoothGattService from config and adds it to the server.
     * Supports at least 3 simultaneous services.
     * @return true if the service was added successfully
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun addService(config: GattServiceConfig): Boolean {
        val server = gattServer
        if (server == null) {
            Log.w(TAG, "Server not open, cannot add service")
            return false
        }

        return try {
            val service = buildGattService(config)
            val result = server.addService(service)
            if (result) {
                _activeServices.update { it + config }
                Log.d(TAG, "Service added: ${config.serviceUuid}")
            } else {
                Log.e(TAG, "Failed to add service: ${config.serviceUuid}")
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error adding service: ${config.serviceUuid}", e)
            false
        }
    }

    /**
     * Removes a service from the server by its UUID.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun removeService(serviceUuid: UUID) {
        val server = gattServer ?: return
        val service = server.getService(serviceUuid)
        if (service != null) {
            server.removeService(service)
            _activeServices.update { current ->
                current.filter { it.serviceUuid != serviceUuid.toString() }
            }
            // Clean up stored values for this service
            val prefix = "${serviceUuid}/"
            characteristicValues.keys.removeAll { it.startsWith(prefix) }
            Log.d(TAG, "Service removed: $serviceUuid")
        } else {
            Log.w(TAG, "Service not found for removal: $serviceUuid")
        }
    }

    /**
     * Updates the stored value for a characteristic.
     */
    fun updateCharacteristicValue(serviceUuid: UUID, charUuid: UUID, value: ByteArray) {
        val key = charKey(serviceUuid, charUuid)
        characteristicValues[key] = value
        Log.d(TAG, "Characteristic value updated: $key -> ${value.toHexString()}")
    }

    /**
     * Sends a notification/indication to a specific connected device.
     * @return true if the notification was sent successfully
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendNotification(
        serviceUuid: UUID,
        charUuid: UUID,
        device: BluetoothDevice,
        value: ByteArray
    ): Boolean {
        val server = gattServer ?: return false
        val service = server.getService(serviceUuid) ?: run {
            Log.w(TAG, "Service not found: $serviceUuid")
            return false
        }
        val characteristic = service.getCharacteristic(charUuid) ?: run {
            Log.w(TAG, "Characteristic not found: $charUuid in service $serviceUuid")
            return false
        }

        // Update stored value
        characteristicValues[charKey(serviceUuid, charUuid)] = value

        val hasIndicate = (characteristic.properties and
                BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0

        return try {
            @Suppress("DEPRECATION")
            characteristic.value = value
            @Suppress("DEPRECATION")
            val result = server.notifyCharacteristicChanged(device, characteristic, hasIndicate)
            Log.d(TAG, "Notification sent to ${device.address}: result=$result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error sending notification", e)
            false
        }
    }

    /**
     * Closes the GATT server and disconnects all devices.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun closeServer() {
        // Disconnect all connected devices
        _connectedDevices.value.forEach { device ->
            try {
                gattServer?.cancelConnection(device)
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting device: ${device.address}", e)
            }
        }

        gattServer?.close()
        gattServer = null

        // Reset all state
        _serverState.value = ServerState.Idle
        _connectedDevices.value = emptyList()
        _activeServices.value = emptyList()
        characteristicValues.clear()
        cccdSubscriptions.clear()

        Log.d(TAG, "GATT server closed")
    }
}
