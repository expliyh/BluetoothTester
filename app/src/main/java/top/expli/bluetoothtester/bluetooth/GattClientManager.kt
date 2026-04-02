package top.expli.bluetoothtester.bluetooth

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import top.expli.bluetoothtester.model.GattCharacteristicInfo
import top.expli.bluetoothtester.model.GattDescriptorInfo
import top.expli.bluetoothtester.model.GattLogEntry
import top.expli.bluetoothtester.model.GattOperation
import top.expli.bluetoothtester.model.GattServiceInfo
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * Independent GATT Client Manager — does NOT inherit from BasicBluetoothProfileManager
 * or SendRecvBluetoothProfileManager. Uses the callback-based GATT model with
 * GattOperationQueue for serial operation execution.
 */
class GattClientManager(private val context: Context) {

    companion object {
        private const val TAG = "GattClientManager"
        /** Standard Client Characteristic Configuration Descriptor UUID */
        private val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    // ─── Connection State ───

    sealed interface ConnectionState {
        data object Idle : ConnectionState
        data object Connecting : ConnectionState
        data object Connected : ConnectionState
        data object Disconnected : ConnectionState
        data class Error(val status: Int) : ConnectionState
    }

    // ─── State Flows ───

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _services = MutableStateFlow<List<GattServiceInfo>>(emptyList())
    val services: StateFlow<List<GattServiceInfo>> = _services.asStateFlow()

    private val _operationLog = MutableStateFlow<List<GattLogEntry>>(emptyList())
    val operationLog: StateFlow<List<GattLogEntry>> = _operationLog.asStateFlow()

    // ─── Internal State ───

    private var gatt: BluetoothGatt? = null
    private val operationQueue = GattOperationQueue()
    private val logIdCounter = AtomicLong(0)

    // Deferreds for ongoing operations (completed by BluetoothGattCallback)
    @Volatile private var readCharDeferred: CompletableDeferred<GattResult<ByteArray>>? = null
    @Volatile private var writeCharDeferred: CompletableDeferred<GattResult<Unit>>? = null
    @Volatile private var readDescDeferred: CompletableDeferred<GattResult<ByteArray>>? = null
    @Volatile private var writeDescDeferred: CompletableDeferred<GattResult<Unit>>? = null
    @Volatile private var mtuDeferred: CompletableDeferred<GattResult<Int>>? = null
    @Volatile private var discoverDeferred: CompletableDeferred<GattResult<Unit>>? = null

    // ─── Helpers ───

    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02X".format(it) }

    private fun ByteArray.toUtf8Safe(): String = try {
        String(this, Charsets.UTF_8)
    } catch (_: Exception) {
        "(invalid UTF-8)"
    }

    private fun addLogEntry(
        operation: GattOperation,
        serviceUuid: String,
        characteristicUuid: String? = null,
        descriptorUuid: String? = null,
        data: ByteArray? = null,
        status: Int? = null,
        success: Boolean
    ) {
        val entry = GattLogEntry(
            id = logIdCounter.incrementAndGet(),
            timestamp = System.currentTimeMillis(),
            operation = operation,
            serviceUuid = serviceUuid,
            characteristicUuid = characteristicUuid,
            descriptorUuid = descriptorUuid,
            dataHex = data?.toHexString(),
            dataUtf8 = data?.toUtf8Safe(),
            status = status,
            success = success
        )
        _operationLog.update { it + entry }
    }

    /**
     * Find a BluetoothGattCharacteristic from the discovered services.
     */
    private fun findCharacteristic(
        serviceUuid: UUID,
        charUuid: UUID
    ): BluetoothGattCharacteristic? {
        val service = gatt?.getService(serviceUuid) ?: return null
        return service.getCharacteristic(charUuid)
    }

    /**
     * Find a BluetoothGattDescriptor from the discovered services.
     */
    private fun findDescriptor(
        serviceUuid: UUID,
        charUuid: UUID,
        descUuid: UUID
    ): BluetoothGattDescriptor? {
        val characteristic = findCharacteristic(serviceUuid, charUuid) ?: return null
        return characteristic.getDescriptor(descUuid)
    }

    // ─── BluetoothGattCallback ───

    private val gattCallback = object : BluetoothGattCallback() {

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "onConnectionStateChange: status=$status, newState=$newState")
            when {
                status != BluetoothGatt.GATT_SUCCESS -> {
                    _connectionState.value = ConnectionState.Error(status)
                    gatt.close()
                    this@GattClientManager.gatt = null
                }
                newState == BluetoothProfile.STATE_CONNECTED -> {
                    _connectionState.value = ConnectionState.Connected
                    // Auto-discover services on connect
                    gatt.discoverServices()
                }
                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    _connectionState.value = ConnectionState.Disconnected
                    gatt.close()
                    this@GattClientManager.gatt = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(TAG, "onServicesDiscovered: status=$status")
            val success = status == BluetoothGatt.GATT_SUCCESS
            if (success) {
                val serviceInfoList = gatt.services.map { service ->
                    GattServiceInfo(
                        uuid = service.uuid.toString(),
                        isPrimary = service.type == android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY,
                        characteristics = service.characteristics.map { char ->
                            GattCharacteristicInfo(
                                uuid = char.uuid.toString(),
                                properties = char.properties,
                                permissions = char.permissions,
                                descriptors = char.descriptors.map { desc ->
                                    GattDescriptorInfo(
                                        uuid = desc.uuid.toString(),
                                        permissions = desc.permissions
                                    )
                                }
                            )
                        }
                    )
                }
                _services.value = serviceInfoList
            }
            addLogEntry(
                operation = GattOperation.ServiceDiscovery,
                serviceUuid = "*",
                status = status,
                success = success
            )
            discoverDeferred?.complete(
                if (success) GattResult.Success(Unit)
                else GattResult.Error(status, "Service discovery failed with status $status")
            )
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val success = status == BluetoothGatt.GATT_SUCCESS
            val data = if (success) characteristic.value else null
            addLogEntry(
                operation = GattOperation.Read,
                serviceUuid = characteristic.service.uuid.toString(),
                characteristicUuid = characteristic.uuid.toString(),
                data = data,
                status = status,
                success = success
            )
            readCharDeferred?.complete(
                if (success && data != null) GattResult.Success(data)
                else GattResult.Error(status, "Read characteristic failed with status $status")
            )
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            val success = status == BluetoothGatt.GATT_SUCCESS
            addLogEntry(
                operation = if (characteristic.writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE)
                    GattOperation.WriteNoResponse else GattOperation.Write,
                serviceUuid = characteristic.service.uuid.toString(),
                characteristicUuid = characteristic.uuid.toString(),
                status = status,
                success = success
            )
            writeCharDeferred?.complete(
                if (success) GattResult.Success(Unit)
                else GattResult.Error(status, "Write characteristic failed with status $status")
            )
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            val data = characteristic.value
            val isIndicate = (characteristic.properties and
                    BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
            addLogEntry(
                operation = if (isIndicate) GattOperation.Indicate else GattOperation.Notify,
                serviceUuid = characteristic.service.uuid.toString(),
                characteristicUuid = characteristic.uuid.toString(),
                data = data,
                success = true
            )
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onDescriptorRead(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            val success = status == BluetoothGatt.GATT_SUCCESS
            val data = if (success) descriptor.value else null
            addLogEntry(
                operation = GattOperation.ReadDescriptor,
                serviceUuid = descriptor.characteristic.service.uuid.toString(),
                characteristicUuid = descriptor.characteristic.uuid.toString(),
                descriptorUuid = descriptor.uuid.toString(),
                data = data,
                status = status,
                success = success
            )
            readDescDeferred?.complete(
                if (success && data != null) GattResult.Success(data)
                else GattResult.Error(status, "Read descriptor failed with status $status")
            )
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            val success = status == BluetoothGatt.GATT_SUCCESS
            addLogEntry(
                operation = GattOperation.WriteDescriptor,
                serviceUuid = descriptor.characteristic.service.uuid.toString(),
                characteristicUuid = descriptor.characteristic.uuid.toString(),
                descriptorUuid = descriptor.uuid.toString(),
                status = status,
                success = success
            )
            writeDescDeferred?.complete(
                if (success) GattResult.Success(Unit)
                else GattResult.Error(status, "Write descriptor failed with status $status")
            )
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            val success = status == BluetoothGatt.GATT_SUCCESS
            addLogEntry(
                operation = GattOperation.MtuRequest,
                serviceUuid = "*",
                data = mtu.toString().toByteArray(),
                status = status,
                success = success
            )
            mtuDeferred?.complete(
                if (success) GattResult.Success(mtu)
                else GattResult.Error(status, "MTU request failed with status $status")
            )
        }
    }

    // ─── Public API ───

    /**
     * Connect to a BLE device via GATT.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(device: BluetoothDevice, autoConnect: Boolean = false) {
        if (_connectionState.value is ConnectionState.Connecting ||
            _connectionState.value is ConnectionState.Connected
        ) {
            Log.w(TAG, "Already connecting or connected, ignoring connect call")
            return
        }
        _connectionState.value = ConnectionState.Connecting
        _services.value = emptyList()
        gatt = device.connectGatt(context, autoConnect, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    /**
     * Disconnect from the GATT server and close resources.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        gatt?.let {
            it.disconnect()
            it.close()
        }
        gatt = null
        _connectionState.value = ConnectionState.Disconnected
    }

    /**
     * Trigger service discovery. Results arrive via onServicesDiscovered callback.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun discoverServices(): GattResult<Unit> {
        val g = gatt ?: return GattResult.Error(-1, "Not connected")
        return operationQueue.enqueue(operationName = "discoverServices", timeout = 10000L) { deferred ->
            discoverDeferred = deferred
            if (!g.discoverServices()) {
                deferred.complete(GattResult.Error(-1, "discoverServices() returned false"))
            }
        }
    }

    /**
     * Read a characteristic value.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun readCharacteristic(
        serviceUuid: UUID,
        charUuid: UUID
    ): GattResult<ByteArray> {
        val g = gatt ?: return GattResult.Error(-1, "Not connected")
        val characteristic = findCharacteristic(serviceUuid, charUuid)
            ?: return GattResult.Error(-1, "Characteristic not found: $charUuid in service $serviceUuid")

        return operationQueue.enqueue(operationName = "readCharacteristic") { deferred ->
            readCharDeferred = deferred
            @Suppress("DEPRECATION")
            if (!g.readCharacteristic(characteristic)) {
                deferred.complete(GattResult.Error(-1, "readCharacteristic() returned false"))
            }
        }
    }

    /**
     * Write a value to a characteristic.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun writeCharacteristic(
        serviceUuid: UUID,
        charUuid: UUID,
        value: ByteArray,
        writeType: Int = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
    ): GattResult<Unit> {
        val g = gatt ?: return GattResult.Error(-1, "Not connected")
        val characteristic = findCharacteristic(serviceUuid, charUuid)
            ?: return GattResult.Error(-1, "Characteristic not found: $charUuid in service $serviceUuid")

        return operationQueue.enqueue(operationName = "writeCharacteristic") { deferred ->
            writeCharDeferred = deferred
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val result = g.writeCharacteristic(characteristic, value, writeType)
                if (result != BluetoothGatt.GATT_SUCCESS) {
                    deferred.complete(GattResult.Error(result, "writeCharacteristic() failed with code $result"))
                }
            } else {
                @Suppress("DEPRECATION")
                characteristic.writeType = writeType
                @Suppress("DEPRECATION")
                characteristic.value = value
                @Suppress("DEPRECATION")
                if (!g.writeCharacteristic(characteristic)) {
                    deferred.complete(GattResult.Error(-1, "writeCharacteristic() returned false"))
                }
            }
        }
    }

    /**
     * Read a descriptor value.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun readDescriptor(
        serviceUuid: UUID,
        charUuid: UUID,
        descUuid: UUID
    ): GattResult<ByteArray> {
        val g = gatt ?: return GattResult.Error(-1, "Not connected")
        val descriptor = findDescriptor(serviceUuid, charUuid, descUuid)
            ?: return GattResult.Error(-1, "Descriptor not found: $descUuid")

        return operationQueue.enqueue(operationName = "readDescriptor") { deferred ->
            readDescDeferred = deferred
            @Suppress("DEPRECATION")
            if (!g.readDescriptor(descriptor)) {
                deferred.complete(GattResult.Error(-1, "readDescriptor() returned false"))
            }
        }
    }

    /**
     * Write a value to a descriptor.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun writeDescriptor(
        serviceUuid: UUID,
        charUuid: UUID,
        descUuid: UUID,
        value: ByteArray
    ): GattResult<Unit> {
        val g = gatt ?: return GattResult.Error(-1, "Not connected")
        val descriptor = findDescriptor(serviceUuid, charUuid, descUuid)
            ?: return GattResult.Error(-1, "Descriptor not found: $descUuid")

        return operationQueue.enqueue(operationName = "writeDescriptor") { deferred ->
            writeDescDeferred = deferred
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val result = g.writeDescriptor(descriptor, value)
                if (result != BluetoothGatt.GATT_SUCCESS) {
                    deferred.complete(GattResult.Error(result, "writeDescriptor() failed with code $result"))
                }
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = value
                @Suppress("DEPRECATION")
                if (!g.writeDescriptor(descriptor)) {
                    deferred.complete(GattResult.Error(-1, "writeDescriptor() returned false"))
                }
            }
        }
    }

    /**
     * Enable or disable notifications/indications for a characteristic by writing the CCCD.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun setNotification(
        serviceUuid: UUID,
        charUuid: UUID,
        enable: Boolean
    ): GattResult<Unit> {
        val g = gatt ?: return GattResult.Error(-1, "Not connected")
        val characteristic = findCharacteristic(serviceUuid, charUuid)
            ?: return GattResult.Error(-1, "Characteristic not found: $charUuid in service $serviceUuid")

        // Enable local notification listener
        if (!g.setCharacteristicNotification(characteristic, enable)) {
            return GattResult.Error(-1, "setCharacteristicNotification() returned false")
        }

        // Determine CCCD value based on characteristic properties
        val cccdValue = if (enable) {
            val hasIndicate = (characteristic.properties and
                    BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
            if (hasIndicate) {
                BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            } else {
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            }
        } else {
            BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
        }

        // Write the CCCD descriptor
        val descriptor = characteristic.getDescriptor(CCCD_UUID)
            ?: return GattResult.Error(-1, "CCCD descriptor not found for characteristic $charUuid")

        return operationQueue.enqueue(operationName = "setNotification") { deferred ->
            writeDescDeferred = deferred
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val result = g.writeDescriptor(descriptor, cccdValue)
                if (result != BluetoothGatt.GATT_SUCCESS) {
                    deferred.complete(GattResult.Error(result, "writeDescriptor(CCCD) failed with code $result"))
                }
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = cccdValue
                @Suppress("DEPRECATION")
                if (!g.writeDescriptor(descriptor)) {
                    deferred.complete(GattResult.Error(-1, "writeDescriptor(CCCD) returned false"))
                }
            }
        }
    }

    /**
     * Request an MTU change.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun requestMtu(mtu: Int): GattResult<Int> {
        val g = gatt ?: return GattResult.Error(-1, "Not connected")
        return operationQueue.enqueue(operationName = "requestMtu") { deferred ->
            mtuDeferred = deferred
            if (!g.requestMtu(mtu)) {
                deferred.complete(GattResult.Error(-1, "requestMtu() returned false"))
            }
        }
    }

    /**
     * Close and cleanup all resources.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun close() {
        gatt?.let {
            it.disconnect()
            it.close()
        }
        gatt = null
        _connectionState.value = ConnectionState.Idle
        _services.value = emptyList()
        // Cancel any pending deferreds
        readCharDeferred?.cancel()
        writeCharDeferred?.cancel()
        readDescDeferred?.cancel()
        writeDescDeferred?.cancel()
        mtuDeferred?.cancel()
        discoverDeferred?.cancel()
        readCharDeferred = null
        writeCharDeferred = null
        readDescDeferred = null
        writeDescDeferred = null
        mtuDeferred = null
        discoverDeferred = null
    }
}
