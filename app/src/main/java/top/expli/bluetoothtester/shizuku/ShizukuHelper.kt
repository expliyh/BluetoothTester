package top.expli.bluetoothtester.shizuku

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import rikka.shizuku.Shizuku

enum class ShizukuState { NotInstalled, NotRunning, NoPermission, Granted }

object ShizukuHelper {
    private const val TAG = "ShizukuHelper"
    private const val PKG = "moe.shizuku.privileged.api"

    fun currentState(context: Context, forcePermission: Boolean? = null): ShizukuState {
        if (!isInstalled(context)) return ShizukuState.NotInstalled
        if (!isReadySafe()) return ShizukuState.NotRunning
        val granted = forcePermission ?: hasPermissionSafe()
        return if (granted) ShizukuState.Granted else ShizukuState.NoPermission
    }

    fun isReadySafe(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermissionSafe(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestPermission(callback: (Boolean) -> Unit) {
        if (!isReadySafe()) {
            callback(false)
            return
        }
        val code = (System.currentTimeMillis() and 0xFFFF).toInt()
        runCatching { Shizuku.requestPermission(code) }.onFailure {
            Log.w(TAG, "requestPermission failed", it)
            callback(false)
            return
        }
        Shizuku.addRequestPermissionResultListener(object :
            Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode == code) {
                    callback(grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED)
                    Shizuku.removeRequestPermissionResultListener(this)
                }
            }
        })
    }

    fun observeState(context: Context, onChange: (ShizukuState) -> Unit) {
        val listener = object : Shizuku.OnBinderReceivedListener, Shizuku.OnBinderDeadListener {
            override fun onBinderReceived() {
                onChange(currentState(context))
            }

            override fun onBinderDead() {
                onChange(currentState(context))
            }
        }
        runCatching { Shizuku.addBinderReceivedListener(listener) }
        runCatching { Shizuku.addBinderDeadListener(listener) }
    }

    fun launchManagerApp(context: Context) {
        val intent = Intent().apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("package:$PKG")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure { e: Throwable ->
            Log.w(TAG, "Unable to launch Shizuku manager", e)
        }
    }

    private fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PKG, 0)
        true
    } catch (_: Throwable) {
        false
    }
}
