package top.expli.bluetoothtester.model

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import top.expli.bluetoothtester.data.SettingsStore

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AppUpdateViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiState_normalPath_shouldHaveInitialVersionAndChannel() = runTest {
        val app = RuntimeEnvironment.getApplication()
        SettingsStore.updateGithubCdn(app, "")

        val viewModel = AppUpdateViewModel(app)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("初始 githubCdn 应为空", "", state.githubCdn)
        assertEquals("checking 初始状态应为 false", false, state.checking)
    }

    @Test
    fun updateGithubCdn_boundaryPath_blankShouldKeepOriginalUrl() = runTest {
        val app = RuntimeEnvironment.getApplication()
        val viewModel = AppUpdateViewModel(app)

        viewModel.updateGithubCdn("   ")
        advanceUntilIdle()

        val resolved = viewModel.resolveUrl("https://api.github.com/repos")
        assertEquals("空白 githubCdn 不应改写 URL", "https://api.github.com/repos", resolved)
    }

    @Test
    fun resolveUrl_exceptionPath_nullInputShouldReturnNull() = runTest {
        val app = RuntimeEnvironment.getApplication()
        val viewModel = AppUpdateViewModel(app)

        val resolved = viewModel.resolveUrl(null)

        assertNull("originalUrl 为 null 时 resolveUrl 应返回 null", resolved)
    }
}
