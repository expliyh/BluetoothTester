package top.expli.bluetoothtester.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import top.expli.bluetoothtester.ui.ThemeOption
import top.expli.bluetoothtester.ui.theme.ThemePreset

@RunWith(Parameterized::class)
class SettingsStoreMapperTest(
    private val themeOrdinal: Int?,
    private val dynamicEnabled: Boolean?,
    private val themePresetName: String?,
    private val githubCdn: String?,
    private val expectedTheme: ThemeOption,
    private val expectedDynamic: Boolean,
    private val expectedPreset: ThemePreset,
    private val expectedCdn: String
) {
    @Test
    fun toSettings_shouldMapExpectedFields() {
        val result = SettingsStore.Mapper.toSettings(themeOrdinal, dynamicEnabled, themePresetName, githubCdn)

        assertEquals("theme 字段映射错误", expectedTheme, result.theme)
        assertEquals("dynamicColorEnabled 字段映射错误", expectedDynamic, result.dynamicColorEnabled)
        assertEquals("themePreset 字段映射错误", expectedPreset, result.themePreset)
        assertEquals("githubCdn 字段映射错误", expectedCdn, result.githubCdn)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "case[{index}] theme={0},dynamic={1},preset={2},cdn={3}")
        fun data(): List<Array<Any?>> =
            listOf(
                arrayOf(ThemeOption.Dark.ordinal, false, "Purple", "https://mirror.example", ThemeOption.Dark, false, ThemePreset.Purple, "https://mirror.example"),
                arrayOf(-1, null, null, null, ThemeOption.System, true, ThemePreset.Default, ""),
                arrayOf(999, true, "InvalidPreset", "", ThemeOption.System, true, ThemePreset.Default, "")
            )
    }
}

class SettingsStoreNormalizeGithubCdnTest {
    @Test
    fun normalizeGithubCdn_normalPath_shouldTrimValue() {
        val result = SettingsStore.Mapper.normalizeGithubCdn("  https://cdn.example/path  ")
        assertEquals("githubCdn 规范化失败", "https://cdn.example/path", result)
    }

    @Test
    fun normalizeGithubCdn_boundaryPath_blankShouldReturnNull() {
        val result = SettingsStore.Mapper.normalizeGithubCdn("   ")
        assertNull("空白 githubCdn 应清空存储", result)
    }

    @Test
    fun normalizeGithubCdn_exceptionLikePath_newlineAndTabsShouldStillTrim() {
        val result = SettingsStore.Mapper.normalizeGithubCdn("\n\thttps://cdn.example/{url}\t")
        assertEquals("包含换行/制表符时 githubCdn 规范化失败", "https://cdn.example/{url}", result)
    }
}
