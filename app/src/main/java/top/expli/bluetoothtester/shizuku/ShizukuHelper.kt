package top.expli.bluetoothtester.shizuku

import top.expli.bluetoothtester.BuildConfig
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.OnBinderDeadListener
import rikka.shizuku.Shizuku.OnBinderReceivedListener
import rikka.shizuku.Shizuku.UserServiceArgs


enum class ShizukuState { NotInstalled, NotRunning, NoPermission, Granted }

enum class ShizukuServiceState { NotConnected, Binding, Connected }

object ShizukuHelper : PrivilegeHelper {
    private const val TAG = "ShizukuHelper"
    private const val PKG = "moe.shizuku.privileged.api"

    private var userService: IUserService? = null

    private val serviceState = MutableStateFlow(ShizukuServiceState.NotConnected)
    val serviceStateFlow: StateFlow<ShizukuServiceState> = serviceState.asStateFlow()

    private val userServiceArgs: UserServiceArgs = UserServiceArgs(
        ComponentName(
            BuildConfig.APPLICATION_ID, UserService::class.java.getName()
        )
    )
        .daemon(false)
        .processNameSuffix("adb_service")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName?, iBinder: IBinder?) {

            if (iBinder != null && iBinder.pingBinder()) {
                userService = IUserService.Stub.asInterface(iBinder)
                serviceState.value = ShizukuServiceState.Connected
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {
            userService = null
            serviceState.value = ShizukuServiceState.NotConnected
        }
    }

    private var initialized = false

    private val binderListener = object : OnBinderReceivedListener, OnBinderDeadListener {
        override fun onBinderReceived() {
            // attempt to bind service once binder is available
            bindUserService()
        }

        override fun onBinderDead() {
            userService = null
            serviceState.value = ShizukuServiceState.NotConnected
        }
    }

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        // register callbacks so we react to Shizuku availability changes
        runCatching { Shizuku.addBinderReceivedListener(binderListener) }
        runCatching { Shizuku.addBinderDeadListener(binderListener) }
        // if already ready and permitted, bind immediately
        if (isReadySafe() && hasPermissionSafe()) {
            bindUserService()
        }
    }

    private fun bindUserService() {
        if (!isReadySafe() || !hasPermissionSafe()) {
            serviceState.value = ShizukuServiceState.NotConnected
            return
        }
        serviceState.value = ShizukuServiceState.Binding
        runCatching { Shizuku.bindUserService(userServiceArgs, serviceConnection) }
            .onFailure {
                Log.w(TAG, "bindUserService failed", it)
                serviceState.value = ShizukuServiceState.NotConnected
            }
    }

    fun currentState(context: Context, forcePermission: Boolean? = null): ShizukuState {
        if (!isInstalled(context)) return ShizukuState.NotInstalled
        if (!isReadySafe()) return ShizukuState.NotRunning
        val granted = forcePermission ?: hasPermissionSafe()
        return if (granted) ShizukuState.Granted else ShizukuState.NoPermission
    }

    fun currentServiceState(): ShizukuServiceState = serviceState.value

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
            data = "package:$PKG".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure { e: Throwable ->
            Log.w(TAG, "Unable to launch Shizuku manager", e)
        }
    }

    fun getService(context: Context): IUserService? {
        if (!hasPermissionSafe()) {
            Toast.makeText(context, "没有Shizuku权限", Toast.LENGTH_SHORT).show();
            return null
        }

        if (userService != null) {
            return userService
        }

        bindUserService()
        return null
    }

    private fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(PKG, 0)
        true
    } catch (_: Throwable) {
        false
    }

    override suspend fun runCmd(
        context: Context,
        command: String
    ): CommandResult = withContext(Dispatchers.IO) {
        val service = getService(context)
            ?: return@withContext CommandResult(false, error = "Shizuku 服务未连接或未授权")
        return@withContext try {
            val output = service.runShellCommand(command)
            val exit = if (output.startsWith("exit=")) output.substringAfter("exit=")
                .substringBefore('\n').toIntOrNull() else 0
            CommandResult(
                exit == 0,
                exitCode = exit.takeIf { it != 0 },
                error = output.takeIf { exit != 0 })
        } catch (e: Exception) {
            CommandResult(false, error = e.message ?: e.toString())
        }
    }
}
