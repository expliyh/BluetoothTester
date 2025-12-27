package top.expli.bluetoothtester.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context

abstract class BasicBluetoothProfileManager(protected val context: Context) {
    protected val manager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }
    protected val adapter: BluetoothAdapter? get() = manager?.adapter

    abstract fun connect()
    abstract fun disconnect()
}
