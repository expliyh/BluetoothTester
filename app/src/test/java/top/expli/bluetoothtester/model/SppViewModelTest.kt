package top.expli.bluetoothtester.model

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SppViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiState_normalPath_selectShouldCreateSessionAndSelectedKey() = runTest {
        val viewModel = SppViewModel(RuntimeEnvironment.getApplication())
        val device = SppDevice(name = "device-A", address = "AA:BB:CC:DD:EE:01", role = SppRole.Client)

        viewModel.select(device)

        val state = viewModel.uiState.value
        assertEquals("selectedKey 更新错误", device.address, state.selectedKey)
        assertEquals("sessions 中应创建一个会话", 1, state.sessions.size)
    }

    @Test
    fun updatePayloadSize_boundaryPath_zeroShouldClampToOne() = runTest {
        val viewModel = SppViewModel(RuntimeEnvironment.getApplication())
        val device = SppDevice(name = "device-B", address = "AA:BB:CC:DD:EE:02")
        viewModel.select(device)

        viewModel.updatePayloadSize(0)

        val session = viewModel.uiState.value.sessions[device.address]
        assertEquals("payloadSize 低于边界时应被钳制为 1", 1, session?.receiveBufferSize)
    }

    @Test
    fun toggleSpeedTest_exceptionPath_withoutConnectionShouldReportError() = runTest {
        val viewModel = SppViewModel(RuntimeEnvironment.getApplication())
        val device = SppDevice(name = "device-C", address = "AA:BB:CC:DD:EE:03")
        viewModel.select(device)

        viewModel.toggleSpeedTest()

        val session = viewModel.uiState.value.sessions[device.address]
        assertNotNull("未连接触发测速时应写入 lastError", session?.lastError)
    }
}
