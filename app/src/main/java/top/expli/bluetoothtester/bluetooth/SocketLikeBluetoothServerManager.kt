package top.expli.bluetoothtester.bluetooth

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import top.expli.bluetoothtester.execeptions.AdapterNotInitialized
import java.io.IOException

/**
 * 蓝牙服务端基类：封装 RFCOMM 服务端的 listen/accept 生命周期与资源管理。
 * 子类只需提供服务名（UUID 由子类管理），使用 register() 启动监听并在后台接受客户端连接。
 */
abstract class SocketLikeBluetoothServerManager(
    context: Context
) : SendRecvBluetoothProfileManager(context) {

    enum class ConnectionState { Idle, Listening, Connected, Closed, Error }

    // 连接状态 StateFlow（只读对外暴露）
    private val _connectionState = MutableStateFlow(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    // 由子类提供服务名（UUID 由具体子类自行管理，不在基类限定）
    protected abstract val serviceName: String

    // 服务端资源与并发（避免与父类 BluetoothSocket? socket 命名冲突，使用 serverSocket 命名）
    protected var serverSocket: BluetoothServerSocket? = null
    protected var acceptJob: Job? = null

    private var aclReceiver: BroadcastReceiver? = null

    /** 客户端接入成功钩子：子类可覆写做额外处理（如认证/握手）。 */
    protected open fun onClientAccepted(socket: BluetoothSocket) {
        this.socket = socket
    }

    /** 服务端不主动连接，保持空实现。 */
    override fun connect() { /* no-op for server */
    }

    // 子类提供具体的服务端 socket 创建逻辑（可为 RFCOMM、L2CAP 等）。
    protected abstract fun registerSocket(): BluetoothServerSocket?

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun register(): Boolean {
        serverSocket?.let { return true }
        if (adapter == null) throw AdapterNotInitialized()
        try {
            if (adapter!!.isDiscovering) adapter!!.cancelDiscovery()
        } catch (_: SecurityException) {
        }

        val srv = registerSocket() ?: run {
            _connectionState.value = ConnectionState.Error
            return false
        }
        serverSocket = srv
        if (!startAcceptLoop()) {
            try {
                serverSocket?.close()
            } catch (_: IOException) {
            }
            serverSocket = null
            _connectionState.value = ConnectionState.Error
            return false
        }
        return true
    }

    // 注册 ACL 广播监听：连接与断开
    private fun registerAclReceiver() {
        if (aclReceiver != null) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        aclReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val action = intent?.action ?: return
                val dev: BluetoothDevice? = intent.getParcelableExtra(
                    BluetoothDevice.EXTRA_DEVICE,
                    BluetoothDevice::class.java
                )
                val remote = socket?.remoteDevice
                if (remote == null || dev == null || dev.address != remote.address) return
                when (action) {
                    // ACL 连接并不意味着 RFCOMM Socket 已连接，这里不更改状态
                    BluetoothDevice.ACTION_ACL_CONNECTED -> { /* no-op */
                    }

                    BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED,
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        disconnect()
                        _connectionState.value = ConnectionState.Closed
                    }
                }
            }
        }
        context.registerReceiver(aclReceiver, filter)
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

    // 将 accept 后台协程封装到父类，直接使用成员 serverSocket
    // 返回布尔值表示是否成功启动；无法启动（例如 serverSocket 为 null）则返回 false
    protected fun startAcceptLoop(): Boolean {
        val srv = serverSocket ?: return false
        _connectionState.value = ConnectionState.Listening
        // 开始监听时注册 ACL 广播（用于辅助感知断开）
        registerAclReceiver()
        return try {
            acceptJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    val accepted = srv.accept()
                    // 关闭旧的客户端连接
                    socket?.let { s ->
                        try {
                            s.close()
                        } catch (_: IOException) {
                        }
                    }
                    // 建立新连接
                    socket = accepted
                    input = accepted.inputStream
                    output = accepted.outputStream
                    _connectionState.value = ConnectionState.Connected
                    onClientAccepted(accepted)

                    // 已成功建立一个连接：不再继续接受新的连接，关闭 serverSocket
                    try {
                        srv.close()
                    } catch (_: IOException) {
                    }
                    serverSocket = null
                } catch (_: IOException) {
                    // 接受失败：关闭监听并清理
                    try {
                        srv.close()
                    } catch (_: IOException) {
                    }
                    serverSocket = null
                    _connectionState.value = ConnectionState.Error
                }
            }
            true
        } catch (_: Exception) {
            _connectionState.value = ConnectionState.Error
            false
        }
    }

    /**
     * 关闭当前连接与服务端监听资源。
     */
    override fun disconnect() {
        // 关闭已建立的客户端连接
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        socket = null
        input = null
        output = null
        _connectionState.value = ConnectionState.Closed
        // 取消后台 accept 任务并关闭服务端监听
        acceptJob?.cancel(); acceptJob = null
        try {
            serverSocket?.close()
        } catch (_: IOException) {
        }
        serverSocket = null
        unregisterAclReceiver()
    }

    // 精细化处理接收：仅在读到 -1（EOF）或抛出 IOException 时认为断开；正常无数据不直接断开
    override fun receive(maxBytes: Int): ByteArray? {
        val inp = input ?: return null
        if (maxBytes <= 0) return null
        return try {
            val available = inp.available()
            if (available <= 0) return ByteArray(0)
            val toRead = minOf(maxBytes, available)
            val buf = ByteArray(toRead)
            val read = inp.read(buf, 0, toRead)
            when {
                read == -1 -> {
                    // 远端关闭
                    disconnect()
                    _connectionState.value = ConnectionState.Closed
                    null
                }

                read > 0 -> buf.copyOf(read)
                else -> {
                    // read == 0：可能是暂时无数据或非阻塞场景，返回空数组，不判定为断开
                    ByteArray(0)
                }
            }
        } catch (_: IOException) {
            // IO 异常视为断开
            disconnect()
            _connectionState.value = ConnectionState.Closed
            null
        }
    }

    // 精细化处理发送：IOException 视为断开
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
}
