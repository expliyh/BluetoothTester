package top.expli.bluetoothtester.model

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import top.expli.bluetoothtester.privilege.shizuku.BluetoothState

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BluetoothToggleViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiState_normalPath_shouldExposeInitialDefaults() = runTest {
        val viewModel = BluetoothToggleViewModel(RuntimeEnvironment.getApplication())

        val state = viewModel.uiState.value

        assertEquals("loopIterations 初始值错误", 5, state.loopIterations)
        assertEquals("loopOnDurationMs 初始值错误", 1500L, state.loopOnDurationMs)
        assertEquals("loopOffDurationMs 初始值错误", 1500L, state.loopOffDurationMs)
    }

    @Test
    fun updateLoopIterations_boundaryPath_negativeShouldClampToZero() = runTest {
        val viewModel = BluetoothToggleViewModel(RuntimeEnvironment.getApplication())

        viewModel.updateLoopIterations(-7)

        assertEquals("loopIterations 负值应被钳制为 0", 0, viewModel.uiState.value.loopIterations)
    }

    @Test
    fun startLoop_exceptionPath_unavailableShouldSetErrorMessage() = runTest {
        val viewModel = BluetoothToggleViewModel(RuntimeEnvironment.getApplication())

        viewModel.startLoop()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("state 不可用时应保持 Unavailable", BluetoothState.Unavailable, state.state)
        assertFalse("设备不可用时不应启动 loopRunning", state.loopRunning)
        assertNotNull("设备不可用时应写入 lastError", state.lastError)
    }
}
