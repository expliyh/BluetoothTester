package top.expli.bluetoothtester.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import java.io.IOException

/**
 * L2CAP CoC 客户端管理器：继承 SocketLikeBluetoothClientManager，
 * 仅覆写 createSocket() 使用 BluetoothDevice.createL2capChannel(psm)。
 */
class L2capClientManager(
    context: Context,
    private val targetDevice: BluetoothDevice,
    private val psm: Int
) : SocketLikeBluetoothClientManager(context) {

    override val device: BluetoothDevice = targetDevice

    override fun createSocket(): BluetoothSocket? = try {
        targetDevice.createL2capChannel(psm)
    } catch (_: IOException) {
        null
    }
}
