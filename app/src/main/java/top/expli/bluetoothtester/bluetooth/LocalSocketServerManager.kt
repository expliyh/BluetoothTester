package top.expli.bluetoothtester.bluetooth

import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * LocalSocket 回环模式的 Server 端。
 * 继承 SendRecvBluetoothProfileManager 以复用 send/receive/speedTest 能力。
 * 使用 LocalServerSocket 替代 BluetoothServerSocket，无需蓝牙权限。
 */
class LocalSocketServerManager(
    context: Context,
    private val uuid: String
) : SendRecvBluetoothProfileManager(context) {

    enum class ConnectionState { Idle, Listening, Connected, Closed, Error }

    private val _connectionState = MutableStateFlow(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private var serverSocket: LocalServerSocket? = null
    private var clientSocket: LocalSocket? = null
    private var acceptJob: Job? = null
    private var userStopped = false
    private val clientDisconnecting = AtomicBoolean(false)

    /** 服务端不主动连接，保持空实现。 */
    override fun connect() { /* no-op for server */ }

    /**
     * 创建 LocalServerSocket，注册到 LocalSocketRegistry，启动后台 accept 协程。
     */
    fun register(): Boolean {
        serverSocket?.let { return true }
        userStopped = false
        return try {
            val name = LocalSocketRegistry.register(uuid)
            val srv = try {
                LocalServerSocket(name)
            } catch (_: IOException) {
                LocalSocketRegistry.unregister(uuid)
                _connectionState.value = ConnectionState.Error
                return false
            }
            serverSocket = srv
            if (!startAcceptLoop()) {
                try { srv.close() } catch (_: IOException) {}
                serverSocket = null
                LocalSocketRegistry.unregister(uuid)
                _connectionState.value = ConnectionState.Error
                false
            } else {
                true
            }
        } catch (_: IOException) {
            LocalSocketRegistry.unregister(uuid)
            _connectionState.value = ConnectionState.Error
            false
        }
    }

    private fun startAcceptLoop(): Boolean {
        val srv = serverSocket ?: return false
        acceptJob?.cancel()
        _connectionState.value = ConnectionState.Listening
        return try {
            acceptJob = CoroutineScope(Dispatchers.IO).launch {
                try {
                    val accepted = srv.accept()
                    // 关闭旧的客户端连接
                    clientSocket?.let { s ->
                        try { s.close() } catch (_: IOException) {}
                    }
                    clientSocket = accepted
                    input = accepted.inputStream
                    output = accepted.outputStream
                    _connectionState.value = ConnectionState.Connected
                } catch (_: IOException) {
                    if (!userStopped) {
                        try { srv.close() } catch (_: IOException) {}
                        serverSocket = null
                        LocalSocketRegistry.unregister(uuid)
                        _connectionState.value = ConnectionState.Error
                    }
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
        try { clientSocket?.close() } catch (_: IOException) {}
        clientSocket = null
        input = null
        output = null
        _connectionState.value = ConnectionState.Closed
        acceptJob?.cancel(); acceptJob = null
        try { serverSocket?.close() } catch (_: IOException) {}
        serverSocket = null
        LocalSocketRegistry.unregister(uuid)
    }

    /**
     * 仅断开当前客户端连接（不关闭 serverSocket），触发自动重新 accept。
     * 与 SocketLikeBluetoothServerManager.disconnectClient() 行为一致。
     */
    fun disconnectClient() {
        if (!clientDisconnecting.compareAndSet(false, true)) return
        try {
            try { clientSocket?.close() } catch (_: IOException) {}
            clientSocket = null
            input = null
            output = null
            _connectionState.value = ConnectionState.Listening
            // 自动重新 accept
            if (!userStopped && serverSocket != null) {
                startAcceptLoop()
            }
        } finally {
            clientDisconnecting.set(false)
        }
    }

    /**
     * 用户主动关闭服务端：设置 userStopped 标志后完全关闭（含已连接的客户端）。
     */
    fun stopServer() {
        userStopped = true
        disconnect()
    }

    /**
     * 仅停止监听（关闭 serverSocket，取消 accept 协程），不断开已建立的客户端连接。
     */
    fun stopListening() {
        userStopped = true
        acceptJob?.cancel(); acceptJob = null
        try {
            serverSocket?.close()
        } catch (_: IOException) {
        }
        serverSocket = null
        LocalSocketRegistry.unregister(uuid)
        if (_connectionState.value != ConnectionState.Connected) {
            _connectionState.value = ConnectionState.Idle
        }
    }

    /**
     * 接收：与 SocketLikeBluetoothServerManager.receive() 行为一致。
     * available() 检查，EOF 时调用 disconnectClient。
     * 当 available()==0 时，检查 socket 是否仍然有效（通过 isClosed 判断）。
     */
    override fun receive(maxBytes: Int): ByteArray? {
        val inp = input ?: return null
        if (maxBytes <= 0) return null
        return try {
            val available = inp.available()
            if (available <= 0) {
                // LocalSocket 没有 ACL 广播，需要主动检测对端断开
                // 检查 clientSocket 是否已关闭
                val cs = clientSocket
                if (cs == null || !cs.isConnected) {
                    disconnectClient()
                    return null
                }
                return ByteArray(0)
            }
            val toRead = minOf(maxBytes, available)
            val buf = ByteArray(toRead)
            val read = inp.read(buf, 0, toRead)
            when {
                read == -1 -> {
                    disconnectClient()
                    null
                }
                read > 0 -> buf.copyOf(read)
                else -> ByteArray(0)
            }
        } catch (_: IOException) {
            disconnectClient()
            null
        }
    }

    /**
     * 发送：IOException 时调用 disconnectClient。
     */
    override fun send(bytes: ByteArray): Boolean {
        val out = output ?: return false
        return try {
            out.write(bytes)
            out.flush()
            true
        } catch (_: IOException) {
            disconnectClient()
            false
        }
    }
}
