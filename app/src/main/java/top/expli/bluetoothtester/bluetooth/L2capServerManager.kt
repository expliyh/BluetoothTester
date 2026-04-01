package top.expli.bluetoothtester.bluetooth

import android.bluetooth.BluetoothServerSocket
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

/**
 * L2CAP CoC 服务端管理器：继承 SocketLikeBluetoothServerManager，
 * 覆写 registerSocket() 使用 BluetoothAdapter.listenUsingL2capChannel()。
 * 暴露 assignedPsm 以显示系统分配的 PSM 值。
 */
class L2capServerManager(
    context: Context
) : SocketLikeBluetoothServerManager(context) {

    override val serviceName: String = "BluetoothTesterL2CAP"

    private val _assignedPsm = MutableStateFlow<Int?>(null)
    val assignedPsm: StateFlow<Int?> = _assignedPsm.asStateFlow()

    override fun registerSocket(): BluetoothServerSocket? = try {
        val serverSocket = adapter?.listenUsingL2capChannel()
        serverSocket?.also {
            _assignedPsm.value = it.psm
        }
    } catch (_: IOException) {
        null
    }

    override fun disconnect() {
        super.disconnect()
        _assignedPsm.value = null
    }
}
