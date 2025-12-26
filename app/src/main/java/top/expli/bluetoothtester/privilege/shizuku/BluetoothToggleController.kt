package top.expli.bluetoothtester.privilege.shizuku

import android.bluetooth.BluetoothAdapter
import android.content.Context
import kotlinx.coroutines.delay

enum class BluetoothState { On, Off, TurningOn, TurningOff, Unavailable }

data class ToggleResult(val success: Boolean, val message: String? = null)

data class CommandResult(val success: Boolean, val exitCode: Int? = null, val error: String? = null)

class BluetoothToggleController(private val context: Context) {
    private val adapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }

    fun currentState(): BluetoothState {
        val state = adapter?.state ?: return BluetoothState.Unavailable
        return when (state) {
            BluetoothAdapter.STATE_ON -> BluetoothState.On
            BluetoothAdapter.STATE_OFF -> BluetoothState.Off
            BluetoothAdapter.STATE_TURNING_ON -> BluetoothState.TurningOn
            BluetoothAdapter.STATE_TURNING_OFF -> BluetoothState.TurningOff
            else -> BluetoothState.Unavailable
        }
    }

    suspend fun setEnabled(enable: Boolean): ToggleResult {
        if (adapter == null) return ToggleResult(false, "设备不支持蓝牙")
        val shizukuState = ShizukuHelper.currentState(context)
        if (shizukuState != ShizukuState.Granted) return ToggleResult(false, "Shizuku 未授权")

        val commands = if (enable) ENABLE_COMMANDS else DISABLE_COMMANDS
        var lastMessage: String? = null
        commands.forEach { command ->
            val result = ShizukuHelper.runCmd(context, command)
            if (result.success) {
                val targetState =
                    if (enable) BluetoothAdapter.STATE_ON else BluetoothAdapter.STATE_OFF
                val applied = waitForState(targetState)
                return ToggleResult(applied, if (applied) null else "命令已执行但状态未变更")
            }
            lastMessage = result.error ?: result.exitCode?.let { "exit=$it" }
        }
        // TODO: root / adb fallback can be inserted here later.
        return ToggleResult(false, lastMessage ?: "命令执行失败")
    }


    private suspend fun waitForState(targetState: Int, timeoutMs: Long = 2500L): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val state = adapter?.state ?: return false
            if (state == targetState) return true
            delay(120)
        }
        return false
    }

    suspend fun runLoop(
        iterations: Int,
        onDurationMs: Long,
        offDurationMs: Long,
        onProgress: (completed: Int, state: BluetoothState) -> Unit,
        onError: (String) -> Unit
    ) {
        val safeIterations = iterations.coerceAtLeast(1)
        val onMs = onDurationMs.coerceAtLeast(0)
        val offMs = offDurationMs.coerceAtLeast(0)
        repeat(safeIterations) { idx ->
            val onResult = setEnabled(true)
            val onState = currentState()
            if (!onResult.success) {
                onError(onResult.message ?: "开启失败")
                return
            }
            onProgress(idx, onState)
            if (onMs > 0) delay(onMs)

            val offResult = setEnabled(false)
            val offState = currentState()
            if (!offResult.success) {
                onError(offResult.message ?: "关闭失败")
                return
            }
            onProgress(idx + 1, offState)
            if (offMs > 0) delay(offMs)
        }
    }

    companion object {
        private val ENABLE_COMMANDS = listOf(
            "cmd bluetooth_manager set-adapter-enabled true",
            "svc bluetooth enable"
        )
        private val DISABLE_COMMANDS = listOf(
            "cmd bluetooth_manager set-adapter-enabled false",
            "svc bluetooth disable"
        )
    }
}
