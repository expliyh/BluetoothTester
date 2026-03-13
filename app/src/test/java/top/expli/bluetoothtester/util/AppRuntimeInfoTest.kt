package top.expli.bluetoothtester.util

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRuntimeInfoTest {
    @Test
    fun toVersion_normalPath_shouldReadNameAndCode() {
        val info = PackageInfo().apply {
            versionName = "1.2.3"
            longVersionCode = 123L
        }

        val result = AppRuntimeInfo.toVersion(info)

        assertEquals("version.name 解析错误", "1.2.3", result.name)
        assertEquals("version.code 解析错误", 123L, result.code)
    }

    @Test
    fun toVersion_boundaryPath_nullInfoShouldReturnDefaults() {
        val result = AppRuntimeInfo.toVersion(null)

        assertEquals("空 PackageInfo 时 name 默认值错误", "", result.name)
        assertEquals("空 PackageInfo 时 code 默认值错误", 0L, result.code)
    }

    @Test
    fun isDebuggableFlag_exceptionPath_unrelatedBitsShouldNotBeDebuggable() {
        assertFalse(
            "未包含 FLAG_DEBUGGABLE 时不应识别为可调试",
            AppRuntimeInfo.isDebuggableFlag(0x2000)
        )
        assertTrue(
            "包含 FLAG_DEBUGGABLE 时应识别为可调试",
            AppRuntimeInfo.isDebuggableFlag(ApplicationInfo.FLAG_DEBUGGABLE)
        )
    }
}
