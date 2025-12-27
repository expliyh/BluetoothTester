package top.expli.bluetoothtester.bluetooth

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException

/**
 * 蓝牙客户端基类：封装 RFCOMM 客户端的连接/断开、ACL 监听、状态流和基础收发。
 * 子类只需提供目标设备与服务 UUID，即可调用 [connect] 建立连接，调用 [disconnect] 关闭。
 */
abstract class SocketLikeBluetoothClientManager(
    context: Context
) : SendRecvBluetoothProfileManager(context) {

    enum class ConnectionState { Idle, Connecting, Connected, Closed, Error }

    private val _connectionState = MutableStateFlow(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    // 子类提供目标设备与具体的 socket 创建方式（可为 RFCOMM、L2CAP 等）
    protected abstract val device: BluetoothDevice
    protected abstract fun createSocket(): BluetoothSocket?

    private var aclReceiver: BroadcastReceiver? = null

    /** 建立连接，初始化输入/输出流，并注册 ACL 广播辅助感知断开。 */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun connect() {
        registerAclReceiver()
        _connectionState.value = ConnectionState.Connecting
        try {
            socket?.let {
                if (it.isConnected) {
                    _connectionState.value = ConnectionState.Connected; return
                }
            }

            if (adapter?.isDiscovering == true) {
                adapter?.cancelDiscovery()
            }

            val tmp = createSocket() ?: run {
                _connectionState.value = ConnectionState.Error
                return
            }

            // 建立连接
            tmp.connect()
            socket = tmp
            input = tmp.inputStream
            output = tmp.outputStream
            _connectionState.value = ConnectionState.Connected
        } catch (_: IOException) {
            _connectionState.value = ConnectionState.Error
            disconnect()
        }
    }

    /** 关闭当前连接并注销 ACL 广播。 */
    override fun disconnect() {
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        socket = null
        input = null
        output = null
        _connectionState.value = ConnectionState.Closed
        unregisterAclReceiver()
    }

    // ACL 广播：只在断开时强制清理；ACL 连接不代表 RFCOMM 已连，因此不更新状态
    private fun registerAclReceiver() {
        if (aclReceiver != null) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        aclReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val dev = intent?.getParcelableExtra(
                    BluetoothDevice.EXTRA_DEVICE,
                    BluetoothDevice::class.java
                )
                if (dev?.address != device.address) return
                // 对端或系统请求断开：清理并更新状态
                disconnect()
            }
        }
        this.context.registerReceiver(aclReceiver, filter)
    }

    private fun unregisterAclReceiver() {
        aclReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        aclReceiver = null
    }

    // 发送：IOException 视为断开
    override fun send(bytes: ByteArray): Boolean {
        val out = output ?: return false
        return try {
            out.write(bytes)
            out.flush()
            true
        } catch (_: IOException) {
            disconnect()
            _connectionState.value = ConnectionState.Closed
            false
        }
    }

    // 接收：-1 或 IOException 视为断开；0 返回空数组表示暂时无数据
    override fun receive(maxBytes: Int): ByteArray? {
        val inp = input ?: return null
        if (maxBytes <= 0) return null
        return try {
            val buf = ByteArray(maxBytes)
            val read = inp.read(buf)
            when {
                read == -1 -> {
                    disconnect()
                    _connectionState.value = ConnectionState.Closed
                    null
                }

                read > 0 -> buf.copyOf(read)
                else -> ByteArray(0) // read == 0
            }
        } catch (_: IOException) {
            disconnect()
            _connectionState.value = ConnectionState.Closed
            null
        }
    }
}
