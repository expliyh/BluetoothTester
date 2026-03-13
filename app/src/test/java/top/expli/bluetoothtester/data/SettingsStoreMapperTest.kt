package top.expli.bluetoothtester.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import top.expli.bluetoothtester.ui.ThemeOption

@RunWith(Parameterized::class)
class SettingsStoreMapperTest(
    private val themeOrdinal: Int?,
    private val dynamicEnabled: Boolean?,
    private val githubCdn: String?,
    private val expectedTheme: ThemeOption,
    private val expectedDynamic: Boolean,
    private val expectedCdn: String
) {
    @Test
    fun toSettings_shouldMapExpectedFields() {
        val result = SettingsStore.Mapper.toSettings(themeOrdinal, dynamicEnabled, githubCdn)

        assertEquals("theme 字段映射错误", expectedTheme, result.theme)
        assertEquals("dynamicColorEnabled 字段映射错误", expectedDynamic, result.dynamicColorEnabled)
        assertEquals("githubCdn 字段映射错误", expectedCdn, result.githubCdn)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "case[{index}] theme={0},dynamic={1},cdn={2}")
        fun data(): List<Array<Any?>> =
            listOf(
                arrayOf(ThemeOption.Dark.ordinal, false, "https://mirror.example", ThemeOption.Dark, false, "https://mirror.example"),
                arrayOf(-1, null, null, ThemeOption.System, true, ""),
                arrayOf(999, true, "", ThemeOption.System, true, "")
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
