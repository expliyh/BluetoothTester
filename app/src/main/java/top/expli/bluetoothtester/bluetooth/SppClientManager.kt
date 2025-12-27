package top.expli.bluetoothtester.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import java.io.IOException
import java.util.UUID

// 客户端管理器：负责主动连接远端 SPP 服务
class SppClientManager(
    context: Context,
    private val targetDevice: BluetoothDevice,
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
) : SocketLikeBluetoothClientManager(context) {

    override val device: BluetoothDevice = targetDevice

    override fun createSocket(): BluetoothSocket? = try {
        targetDevice.createRfcommSocketToServiceRecord(sppUuid)
    } catch (_: IOException) {
        null
    }
}
