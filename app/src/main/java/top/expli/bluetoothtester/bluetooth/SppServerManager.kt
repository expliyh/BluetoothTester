package top.expli.bluetoothtester.bluetooth

import android.Manifest
import android.bluetooth.BluetoothServerSocket
import android.content.Context
import androidx.annotation.RequiresPermission
import top.expli.bluetoothtester.execeptions.AdapterNotInitialized
import java.io.IOException
import java.util.UUID

class SppServerManager(
    context: Context,
    private val serviceUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
) : SocketLikeBluetoothServerManager(context) {
    override val serviceName: String = "BluetoothTesterSPP"

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun registerSocket(): BluetoothServerSocket? {
        return try {
            if (adapter == null) throw AdapterNotInitialized()
            adapter!!.listenUsingRfcommWithServiceRecord(serviceName, serviceUuid)
        } catch (_: IOException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    override fun connect() { /* no-op for server */
    }
}
