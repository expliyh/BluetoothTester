package top.expli.bluetoothtester.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager

object AppRuntimeInfo {
    data class Version(
        val name: String,
        val code: Long
    )

    internal fun toVersion(info: PackageInfo?): Version {
        if (info == null) return Version(name = "", code = 0)
        return Version(
            name = info.versionName.orEmpty(),
            code = info.longVersionCode
        )
    }

    internal fun isDebuggableFlag(flags: Int): Boolean {
        return (flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    fun version(context: Context): Version {
        val appContext = context.applicationContext
        val pkg = appContext.packageName
        return runCatching {
            val info =
                appContext.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            toVersion(info)
        }.getOrElse {
            Version(name = "", code = 0)
        }
    }

    fun versionName(context: Context): String = version(context).name

    fun versionCode(context: Context): Long = version(context).code

    fun isDebuggable(context: Context): Boolean {
        val appInfo = context.applicationContext.applicationInfo
        return isDebuggableFlag(appInfo.flags)
    }
}
