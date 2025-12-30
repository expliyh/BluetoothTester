package top.expli.bluetoothtester.ui

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import top.expli.bluetoothtester.model.SppViewModel
import top.expli.bluetoothtester.ui.navigation.AppNavTransitions
import top.expli.bluetoothtester.ui.permissions.BluetoothPermissions
import top.expli.bluetoothtester.ui.permissions.openAppSettings
import top.expli.bluetoothtester.ui.spp.SppListScreen
import top.expli.bluetoothtester.ui.spp.SppDetailScreen
import top.expli.bluetoothtester.ui.spp.SppRoute
import top.expli.bluetoothtester.ui.spp.AddSppDeviceDialog
import top.expli.bluetoothtester.ui.spp.BondedDeviceItem
import top.expli.bluetoothtester.ui.spp.BondedDevicePickerSheet
import top.expli.bluetoothtester.ui.spp.uniqueKey
import top.expli.bluetoothtester.ui.spp.label
import top.expli.bluetoothtester.model.SppConnectionState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SppScreen(onBackClick: () -> Unit) {
    val vm: SppViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    val latestState by rememberUpdatedState(state)
    var showAddDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? Activity
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showPermissionRationaleDialog by remember { mutableStateOf(false) }
    var showPermissionSettingsDialog by remember { mutableStateOf(false) }
    var pendingBluetoothAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showBondedDevicePicker by remember { mutableStateOf(false) }
    var bondedDevices by remember { mutableStateOf<List<BondedDeviceItem>>(emptyList()) }
    var bondedDevicePickerOnSelect by remember { mutableStateOf<((BondedDeviceItem) -> Unit)?>(null) }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val inDetail = backStackEntry != null && navController.previousBackStackEntry != null

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.all { it }
        if (granted) {
            pendingBluetoothAction?.invoke()
        } else {
            val act = activity
            if (act != null && !BluetoothPermissions.shouldShowRationale(act)) {
                showPermissionSettingsDialog = true
            } else {
                scope.launch { snackbarHostState.showSnackbar("需要蓝牙权限才能继续") }
            }
        }
        pendingBluetoothAction = null
    }

    fun ensureBluetoothPermissions(action: () -> Unit) {
        if (BluetoothPermissions.hasAll(context)) {
            action()
            return
        }
        pendingBluetoothAction = action
        val act = activity
        if (act != null && BluetoothPermissions.shouldShowRationale(act)) {
            showPermissionRationaleDialog = true
        } else {
            bluetoothPermissionLauncher.launch(BluetoothPermissions.required)
        }
    }

    fun requestBondedDevicePick(onPicked: (BondedDeviceItem) -> Unit) {
        ensureBluetoothPermissions {
            val result = runCatching { loadBondedDeviceItems(context) }.getOrElse {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        "读取已绑定设备失败: ${it.message ?: it::class.java.simpleName}"
                    )
                }
                return@ensureBluetoothPermissions
            }
            when (result) {
                is BondedDeviceLoadResult.Success -> {
                    if (result.devices.isEmpty()) {
                        scope.launch {
                            snackbarHostState.showSnackbar("未找到已绑定设备，请先在系统蓝牙设置中配对")
                        }
                    } else {
                        bondedDevices = result.devices
                        bondedDevicePickerOnSelect = onPicked
                        showBondedDevicePicker = true
                    }
                }

                BondedDeviceLoadResult.BluetoothDisabled -> scope.launch {
                    snackbarHostState.showSnackbar("蓝牙未开启，请先开启蓝牙")
                }

                BondedDeviceLoadResult.BluetoothUnavailable -> scope.launch {
                    snackbarHostState.showSnackbar("本机蓝牙不可用")
                }
            }
        }
    }

    LaunchedEffect(state.lastError) {
        state.lastError?.let { msg ->
            snackbarHostState.showSnackbar("错误: $msg")
        }
    }

    BackHandler {
        if (showAddDialog) {
            showAddDialog = false
        } else {
            if (inDetail) navController.navigateUp() else onBackClick()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            val selected = state.selected
            TopAppBar(
                title = {
                    if (!inDetail || selected == null) {
                        Text("SPP 工具")
                    } else {
                        Column {
                            Text(
                                text = selected.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subtitleShowsUuid = selected.address.isBlank()
                            val subtitle = if (!subtitleShowsUuid) {
                                "${selected.role.label()} · ${selected.address}"
                            } else {
                                "${selected.role.label()} · UUID: ${selected.uuid}"
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = if (subtitleShowsUuid) FontFamily.Monospace else null,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (inDetail) navController.navigateUp() else onBackClick() }) {
                        Icon(
                            if (inDetail) Icons.Default.ArrowBack else Icons.Default.Link,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (!inDetail) {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "注册 Socket")
                        }
                    } else {
                        AssistChip(onClick = {}, label = { Text(state.connectionState.label()) })
                    }
                }
            )
        }
    ) { inner ->
        NavHost(
            navController = navController,
            startDestination = SppRoute.List,
            enterTransition = AppNavTransitions.enter,
            exitTransition = AppNavTransitions.exit,
            popEnterTransition = AppNavTransitions.popEnter,
            popExitTransition = AppNavTransitions.popExit
        ) {
            composable<SppRoute.List> {
                SppListScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner)
                        .padding(12.dp),
                    state = state,
                    onOpenDetail = {
                        vm.select(it)
                        navController.navigate(SppRoute.Detail)
                    },
                    onDelete = { vm.remove(it.uniqueKey()) },
                    onAdd = { showAddDialog = true }
                )
            }

            composable<SppRoute.Detail> {
                SppDetailScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner),
                    state = state,
                    onTextChange = { vm.updateSendingText(it) },
                    onPayloadChange = { vm.updatePayloadSize(it) },
                    onSend = { vm.sendOnce() },
                    onToggleSpeedTest = { ensureBluetoothPermissions { vm.toggleSpeedTest() } },
                    onParseIncomingAsTextChange = { vm.updateParseIncomingAsText(it) },
                    onToggleConnection = {
                        val active =
                            state.connectionState == SppConnectionState.Connected || state.connectionState == SppConnectionState.Listening
                        if (active) {
                            vm.disconnect()
                        } else {
                            ensureBluetoothPermissions { vm.connect() }
                        }
                    },
                    onClearChat = { vm.clearChat() },
                    onConnectFromBondedDevice = {
                        requestBondedDevicePick { picked ->
                            val selected = latestState.selected ?: return@requestBondedDevicePick
                            val pickedName = picked.name.ifBlank { picked.address }
                            val resolvedName =
                                if (selected.name.isBlank() || selected.name == selected.address) pickedName else selected.name
                            val updated =
                                selected.copy(name = resolvedName, address = picked.address)
                            ensureBluetoothPermissions {
                                vm.select(updated)
                                vm.connect()
                            }
                        }
                    }
                )
            }
        }
    }

    if (showAddDialog) {
        AddSppDeviceDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { device ->
                vm.addOrUpdate(device)
                showAddDialog = false
            },
            onPickBondedDevice = { onPicked ->
                requestBondedDevicePick(onPicked)
            }
        )
    }

    if (showPermissionRationaleDialog) {
        AlertDialog(
            onDismissRequest = {
                showPermissionRationaleDialog = false
                pendingBluetoothAction = null
            },
            title = { Text("需要蓝牙权限") },
            text = { Text("SPP 连接/监听需要授予“附近设备”相关的蓝牙连接与扫描权限。") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionRationaleDialog = false
                    bluetoothPermissionLauncher.launch(BluetoothPermissions.required)
                }) { Text("去授权") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPermissionRationaleDialog = false
                    pendingBluetoothAction = null
                }) { Text("取消") }
            }
        )
    }

    if (showPermissionSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionSettingsDialog = false },
            title = { Text("权限被拒绝") },
            text = { Text("蓝牙权限可能已被永久拒绝，请前往系统设置手动开启。") },
            confirmButton = {
                TextButton(onClick = {
                    showPermissionSettingsDialog = false
                    openAppSettings(context)
                }) { Text("打开设置") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionSettingsDialog = false }) {
                    Text(
                        "取消"
                    )
                }
            }
        )
    }

    if (showBondedDevicePicker) {
        BondedDevicePickerSheet(
            devices = bondedDevices,
            onDismissRequest = {
                showBondedDevicePicker = false
                bondedDevicePickerOnSelect = null
            },
            onSelect = { picked ->
                showBondedDevicePicker = false
                val cb = bondedDevicePickerOnSelect
                bondedDevicePickerOnSelect = null
                cb?.invoke(picked)
            }
        )
    }
}

private sealed interface BondedDeviceLoadResult {
    data class Success(val devices: List<BondedDeviceItem>) : BondedDeviceLoadResult
    data object BluetoothDisabled : BondedDeviceLoadResult
    data object BluetoothUnavailable : BondedDeviceLoadResult
}

@SuppressLint("MissingPermission")
private fun loadBondedDeviceItems(context: Context): BondedDeviceLoadResult {
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        ?: return BondedDeviceLoadResult.BluetoothUnavailable
    if (!adapter.isEnabled) return BondedDeviceLoadResult.BluetoothDisabled
    val devices = adapter.bondedDevices ?: emptySet()
    return devices
        .asSequence()
        .map { dev ->
            val name = runCatching { dev.name }.getOrNull().orEmpty()
            BondedDeviceItem(
                name = name,
                address = dev.address
            )
        }
        .filter { it.address.isNotBlank() }
        .distinctBy { it.address }
        .sortedWith(
            compareBy<BondedDeviceItem> { it.name.ifBlank { it.address }.lowercase() }
                .thenBy { it.address }
        )
        .toList()
        .let { BondedDeviceLoadResult.Success(it) }
}
