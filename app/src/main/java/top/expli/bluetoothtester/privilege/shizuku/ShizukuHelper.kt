package top.expli.bluetoothtester.privilege.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.Shizuku.OnBinderDeadListener
import rikka.shizuku.Shizuku.OnBinderReceivedListener
import rikka.shizuku.Shizuku.UserServiceArgs
import top.expli.bluetoothtester.privilege.PrivilegeHelper
import top.expli.bluetoothtester.util.AppRuntimeInfo


enum class ShizukuState { NotInstalled, NotRunning, NoPermission, Granted }

enum class ShizukuServiceState { NotConnected, Binding, Connected }

object ShizukuHelper : PrivilegeHelper {
    private const val TAG = "ShizukuHelper"
    private const val PKG = "moe.shizuku.privileged.api"

    private var userService: IUserService? = null

    private val serviceStateFlowInternal = MutableStateFlow(ShizukuServiceState.NotConnected)
    val serviceStateFlow: StateFlow<ShizukuServiceState> = serviceStateFlowInternal.asStateFlow()

    private val stateFlowInternal = MutableStateFlow(ShizukuState.NotInstalled)
    val stateFlow: StateFlow<ShizukuState> = stateFlowInternal.asStateFlow()

    private var appContext: Context? = null

    private var userServiceArgs: UserServiceArgs? = null

    private fun buildUserServiceArgs(context: Context): UserServiceArgs {
        val versionCode = AppRuntimeInfo.versionCode(context)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()
        val debuggable = AppRuntimeInfo.isDebuggable(context)
        val pkg = context.applicationContext.packageName
        return UserServiceArgs(
            ComponentName(pkg, UserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("adb_service")
            .debuggable(debuggable)
            .version(versionCode)
    }

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName?, iBinder: IBinder?) {
            Log.w(TAG, "onServiceConnected: CONNECTED")

            if (iBinder != null && iBinder.pingBinder()) {
                userService = IUserService.Stub.asInterface(iBinder)
                serviceStateFlowInternal.value = ShizukuServiceState.Connected
            }
        }

        override fun onServiceDisconnected(componentName: ComponentName?) {
            userService = null
            serviceStateFlowInternal.value = ShizukuServiceState.NotConnected
        }
    }

    private var initialized = false

    private val binderListener = object : OnBinderReceivedListener, OnBinderDeadListener {
        override fun onBinderReceived() {
            appContext?.let { refreshState(it) }
            bindUserService()
        }

        override fun onBinderDead() {
            userService = null
            serviceStateFlowInternal.value = ShizukuServiceState.NotConnected
            appContext?.let { stateFlowInternal.value = currentState(it) }
        }
    }

    fun refreshState(context: Context) {
        val state = currentState(context)
        stateFlowInternal.value = state
        if (state == ShizukuState.Granted) {
            bindUserService()
        } else {
            userService = null
            serviceStateFlowInternal.value = ShizukuServiceState.NotConnected
        }
    }

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        userServiceArgs = buildUserServiceArgs(appContext!!)
        refreshState(appContext!!)
        // register callbacks so we react to Shizuku availability changes
        runCatching { Shizuku.addBinderReceivedListener(binderListener) }
        runCatching { Shizuku.addBinderDeadListener(binderListener) }
        // if already ready and permitted, bind immediately
        if (isReadySafe() && hasPermissionSafe()) {
            bindUserService()
        }
    }

    private fun bindUserService() {
        Log.d(TAG, "bindUserService: BIND")
        if (serviceStateFlowInternal.value == ShizukuServiceState.Binding || serviceStateFlowInternal.value == ShizukuServiceState.Connected) return
        if (!isReadySafe() || !hasPermissionSafe()) {
            serviceStateFlowInternal.value = ShizukuServiceState.NotConnected
            return
        }
        serviceStateFlowInternal.value = ShizukuServiceState.Binding
        val args = userServiceArgs ?: appContext?.let {
            buildUserServiceArgs(it).also { built ->
                userServiceArgs = built
            }
        }
        if (args == null) {
            serviceStateFlowInternal.value = ShizukuServiceState.NotConnected
            return
        }
        val bound = runCatching {
            Shizuku.bindUserService(args, serviceConnection)
            true
        }.onFailure {
            Log.w(TAG, "bindUserService failed", it)
        }.getOrDefault(false)
        if (!bound) {
            serviceStateFlowInternal.value = ShizukuServiceState.NotConnected
        }
    }

    fun currentState(context: Context, forcePermission: Boolean? = null): ShizukuState {
        if (!isInstalled(context)) return ShizukuState.NotInstalled
        if (!isReadySafe()) return ShizukuState.NotRunning
        val granted = forcePermission ?: hasPermissionSafe()
        return if (granted) ShizukuState.Granted else ShizukuState.NoPermission
    }

    fun isReadySafe(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermissionSafe(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
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
                    val granted = grantResult == PackageManager.PERMISSION_GRANTED
                    appContext?.let {
                        stateFlowInternal.value = currentState(it, forcePermission = granted)
                        if (granted) {
                            bindUserService()
                        } else {
                            userService = null
                            serviceStateFlowInternal.value = ShizukuServiceState.NotConnected
                        }
                    }
                    callback(granted)
                    Shizuku.removeRequestPermissionResultListener(this)
                }
            }
        })
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
            Toast.makeText(context, "没有Shizuku权限", Toast.LENGTH_SHORT).show()
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
