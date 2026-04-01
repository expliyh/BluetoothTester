package top.expli.bluetoothtester.adb

import android.content.Context

/**
 * 检查 App 是否在前台且模块已初始化。
 * 使用简单的静态布尔值，由 Activity 生命周期设置。
 */
object AppStateChecker {

    @Volatile
    var isInForeground: Boolean = false

    fun isAppReady(@Suppress("UNUSED_PARAMETER") context: Context): Boolean {
        return isInForeground
    }
}
