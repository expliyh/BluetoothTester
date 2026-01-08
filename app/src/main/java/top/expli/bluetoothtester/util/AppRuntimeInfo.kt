package top.expli.bluetoothtester.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

object AppRuntimeInfo {
    data class Version(
        val name: String,
        val code: Long
    )

    fun version(context: Context): Version {
        val appContext = context.applicationContext
        val pkg = appContext.packageName
        return runCatching {
            val info =
                appContext.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            Version(
                name = info.versionName.orEmpty(),
                code = info.longVersionCode
            )
        }.getOrElse {
            Version(name = "", code = 0)
        }
    }

    fun versionName(context: Context): String = version(context).name

    fun versionCode(context: Context): Long = version(context).code

    fun isDebuggable(context: Context): Boolean {
        val appInfo = context.applicationContext.applicationInfo
        return (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }
}
