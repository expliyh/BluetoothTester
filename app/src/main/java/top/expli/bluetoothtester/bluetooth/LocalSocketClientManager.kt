package top.expli.bluetoothtester.bluetooth

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException

/**
 * LocalSocket 回环模式的 Client 端。
 * 继承 SendRecvBluetoothProfileManager 以复用 send/receive/speedTest 能力。
 * 使用 LocalSocket 替代 BluetoothSocket，无需蓝牙权限。
 */
class LocalSocketClientManager(
    context: Context,
    private val uuid: String
) : SendRecvBluetoothProfileManager(context) {

    enum class ConnectionState { Idle, Connecting, Connected, Closed, Error }

    private val _connectionState = MutableStateFlow(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private var localSocket: LocalSocket? = null

    /** 最近一次 connect() 的耗时（毫秒），仅在连接成功时有值 */
    var lastConnectDurationMs: Long? = null
        private set

    /**
     * 从 LocalSocketRegistry 查找 Server 注册的 name，创建 LocalSocket 并连接。
     * 如果 Server 还没注册（find 返回 null），设置 Error 状态。
     */
    override fun connect() {
        _connectionState.value = ConnectionState.Connecting
        val name = LocalSocketRegistry.find(uuid)
        if (name == null) {
            _connectionState.value = ConnectionState.Error
            return
        }
        try {
            val sock = LocalSocket()
            val startNs = System.nanoTime()
            sock.connect(LocalSocketAddress(name, LocalSocketAddress.Namespace.ABSTRACT))
            val endNs = System.nanoTime()
            lastConnectDurationMs = (endNs - startNs) / 1_000_000

            localSocket = sock
            input = sock.inputStream
            output = sock.outputStream
            _connectionState.value = ConnectionState.Connected
        } catch (_: IOException) {
            disconnect()
            _connectionState.value = ConnectionState.Error
        }
    }

    /** 关闭 LocalSocket 连接。 */
    override fun disconnect() {
        try { localSocket?.close() } catch (_: IOException) {}
        localSocket = null
        input = null
        output = null
        _connectionState.value = ConnectionState.Closed
    }

    /**
     * 接收：与 SocketLikeBluetoothClientManager.receive() 行为一致。
     * available() 检查，-1 或 IOException 视为断开。
     * 当 available()==0 时，检查 socket 是否仍然连接。
     */
    override fun receive(maxBytes: Int): ByteArray? {
        val inp = input ?: return null
        if (maxBytes <= 0) return null
        return try {
            val available = inp.available()
            if (available <= 0) {
                // LocalSocket 没有 ACL 广播，需要主动检测对端断开
                val sock = localSocket
                if (sock == null || !sock.isConnected) {
                    disconnect()
                    _connectionState.value = ConnectionState.Closed
                    return null
                }
                return ByteArray(0)
            }
            val toRead = minOf(maxBytes, available)
            val buf = ByteArray(toRead)
            val read = inp.read(buf, 0, toRead)
            when {
                read == -1 -> {
                    disconnect()
                    _connectionState.value = ConnectionState.Closed
                    null
                }
                read > 0 -> buf.copyOf(read)
                else -> ByteArray(0)
            }
        } catch (_: IOException) {
            disconnect()
            _connectionState.value = ConnectionState.Closed
            null
        }
    }

    /**
     * 发送：IOException 时 disconnect。
     */
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
