package top.expli.bluetoothtester.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Activity
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
    var showAddDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? Activity
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showPermissionRationaleDialog by remember { mutableStateOf(false) }
    var showPermissionSettingsDialog by remember { mutableStateOf(false) }
    var pendingBluetoothAction by remember { mutableStateOf<(() -> Unit)?>(null) }

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
                    onToggleConnection = {
                        val active =
                            state.connectionState == SppConnectionState.Connected || state.connectionState == SppConnectionState.Listening
                        if (active) {
                            vm.disconnect()
                        } else {
                            ensureBluetoothPermissions { vm.connect() }
                        }
                    },
                    onClearChat = { vm.clearChat() }
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
}
