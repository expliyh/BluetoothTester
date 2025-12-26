package top.expli.bluetoothtester.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import top.expli.bluetoothtester.privilege.shizuku.BluetoothState
import top.expli.bluetoothtester.privilege.shizuku.BluetoothToggleController
import top.expli.bluetoothtester.privilege.shizuku.ToggleResult

data class BluetoothToggleUiState(
    val state: BluetoothState = BluetoothState.Unavailable,
    val inProgress: Boolean = false,
    val lastError: String? = null,
    val loopRunning: Boolean = false,
    val loopIterations: Int = 5,
    val loopCompleted: Int = 0,
    val loopOnDurationMs: Long = 1500,
    val loopOffDurationMs: Long = 1500
)

class BluetoothToggleViewModel(app: Application) : AndroidViewModel(app) {
    private val controller = BluetoothToggleController(app.applicationContext)
    private var loopJob: Job? = null

    private val _uiState =
        MutableStateFlow(BluetoothToggleUiState(state = controller.currentState()))
    val uiState: StateFlow<BluetoothToggleUiState> = _uiState

    fun refresh() {
        _uiState.update { it.copy(state = controller.currentState()) }
    }

    fun toggle() {
        val enable = when (_uiState.value.state) {
            BluetoothState.On, BluetoothState.TurningOn -> false
            BluetoothState.Off, BluetoothState.TurningOff -> true
            BluetoothState.Unavailable -> return
        }
        executeToggle(enable)
    }

    fun setEnabled(enable: Boolean) = executeToggle(enable)

    fun updateLoopIterations(value: Int) {
        val safeValue = value.coerceAtLeast(0)
        _uiState.update { it.copy(loopIterations = safeValue) }
    }

    fun updateLoopOnDuration(ms: Long) {
        _uiState.update { it.copy(loopOnDurationMs = ms.coerceAtLeast(0)) }
    }

    fun updateLoopOffDuration(ms: Long) {
        _uiState.update { it.copy(loopOffDurationMs = ms.coerceAtLeast(0)) }
    }

    fun startLoop() {
        if (loopJob?.isActive == true) return
        val snapshot = _uiState.value
        if (snapshot.state == BluetoothState.Unavailable) {
            _uiState.update { it.copy(lastError = "设备不支持蓝牙") }
            return
        }
        val iterations = snapshot.loopIterations.coerceAtLeast(1)
        val onMs = snapshot.loopOnDurationMs.coerceAtLeast(0)
        val offMs = snapshot.loopOffDurationMs.coerceAtLeast(0)
        val initialOn =
            snapshot.state == BluetoothState.On || snapshot.state == BluetoothState.TurningOn

        _uiState.update { it.copy(loopRunning = true, loopCompleted = 0, lastError = null) }
        loopJob = viewModelScope.launch {
            var errorMessage: String? = null
            try {
                repeat(iterations) { cycleIdx ->
                    val toggleTargets = listOf(!initialOn, initialOn)
                    toggleTargets.forEachIndexed { stepIdx, targetOn ->
                        _uiState.update { it.copy(inProgress = true) }
                        val result = controller.setEnabled(targetOn)
                        val newState = controller.currentState()
                        _uiState.update {
                            it.copy(
                                state = newState,
                                inProgress = false,
                                lastError = result.message?.takeIf { !result.success }
                            )
                        }
                        if (!result.success) {
                            errorMessage = result.message; return@launch
                        }
                        ensureActive()

                        // 在循环内部，仅在两次切换之间等待
                        val isLastToggleOfCycle = stepIdx == toggleTargets.lastIndex
                        val holdMs = if (targetOn) onMs else offMs
                        if (!isLastToggleOfCycle && holdMs > 0) delay(holdMs)
                    }

                    // 先计数，再做下一循环前的等待
                    _uiState.update { it.copy(loopCompleted = cycleIdx + 1) }
                    val hasMoreCycles = cycleIdx < iterations - 1
                    if (hasMoreCycles) {
                        val holdAfterCycle = if (initialOn) onMs else offMs
                        if (holdAfterCycle > 0) {
                            ensureActive()
                            delay(holdAfterCycle)
                        }
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                errorMessage = e.message ?: "循环中断"
            } finally {
                if (controller.currentState() != if (initialOn) BluetoothState.On else BluetoothState.Off) {
                    controller.setEnabled(initialOn)
                }
                val finalState = controller.currentState()
                _uiState.update {
                    it.copy(
                        state = finalState,
                        loopRunning = false,
                        inProgress = false,
                        lastError = errorMessage
                    )
                }
            }
        }
    }

    fun stopLoop() {
        loopJob?.cancel()
        loopJob = null
    }

    private fun finishLoopWithFailure(message: String? = null) {
        _uiState.update {
            it.copy(
                loopRunning = false,
                inProgress = false,
                lastError = message ?: "循环失败"
            )
        }
    }

    private fun executeToggle(enable: Boolean) {
        if (_uiState.value.inProgress || _uiState.value.loopRunning) return
        _uiState.update { it.copy(inProgress = true, lastError = null) }
        viewModelScope.launch {
            val result: ToggleResult = controller.setEnabled(enable)
            val newState = controller.currentState()
            _uiState.update {
                it.copy(
                    state = newState,
                    inProgress = false,
                    lastError = result.message?.takeIf { !result.success }
                )
            }
        }
    }
}
