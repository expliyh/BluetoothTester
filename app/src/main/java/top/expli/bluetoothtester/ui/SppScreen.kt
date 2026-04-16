package top.expli.bluetoothtester.ui

import android.annotation.SuppressLint
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import top.expli.bluetoothtester.model.SppViewModel
import top.expli.bluetoothtester.ui.permissions.BluetoothPermissions
import top.expli.bluetoothtester.ui.permissions.openAppSettings
import top.expli.bluetoothtester.ui.common.BondedDeviceItem
import top.expli.bluetoothtester.ui.common.BondedDeviceLoadResult
import top.expli.bluetoothtester.ui.common.DevicePickerSheet
import top.expli.bluetoothtester.ui.common.loadBondedDeviceItems
import top.expli.bluetoothtester.ui.spp.AddServerDialog
import top.expli.bluetoothtester.ui.spp.ClientTabPage
import top.expli.bluetoothtester.ui.spp.ServerTabPage
import java.util.concurrent.atomic.AtomicReference

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SppScreen(onBackClick: () -> Unit) {
    val vm: SppViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showAddServerDialog by remember { mutableStateOf(false) }
    var showPermissionRationaleDialog by remember { mutableStateOf(false) }
    var showPermissionSettingsDialog by remember { mutableStateOf(false) }
    var showBondedDevicePicker by remember { mutableStateOf(false) }
    var bondedDevices by remember { mutableStateOf<List<BondedDeviceItem>>(emptyList()) }
    val pendingBluetoothAction = remember { AtomicReference<(() -> Unit)?>(null) }
    val bondedDevicePickerOnSelect = remember { AtomicReference<((String, String?) -> Unit)?>(null) }

    // Delete confirmation state
    var deleteTabId by remember { mutableStateOf<String?>(null) }

    // Client tab detail state
    var clientIsInDetail by remember { mutableStateOf(false) }
    var clientDetailBackHandler: (() -> Unit)? by remember { mutableStateOf(null) }

    // Server tab detail state
    var serverIsInDetail by remember { mutableStateOf(false) }
    var serverDetailBackHandler: (() -> Unit)? by remember { mutableStateOf(null) }
    var serverDetailAddress by remember { mutableStateOf<String?>(null) }
    var serverDetailIsHistory by remember { mutableStateOf(false) }

    // Scroll to latest callback
    var scrollToLatest: (() -> Unit)? by remember { mutableStateOf(null) }

    val serverTabs = state.serverTabs
    val pageCount = 1 + serverTabs.size // Client + Server tabs

    val pagerState = rememberPagerState(pageCount = { pageCount })

    // Sync pager when a new server tab is added (auto-switch to it)
    var pendingScrollToTab by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(serverTabs.size) {
        pendingScrollToTab?.let { tabId ->
            val index = serverTabs.indexOfFirst { it.tabId == tabId } + 1  // +1 for Client tab at index 0
            if (index > 0) {
                pagerState.animateScrollToPage(index)
                pendingScrollToTab = null
            }
            // index <= 0 时不清除，等待下一次 serverTabs 更新
        }
    }

    // Select server tab when pager page changes
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (page > 0 && page <= serverTabs.size) {
                val tab = serverTabs[page - 1]
                vm.selectServerTab(tab.tabId)
            }
        }
    }

    // Permission launcher
    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.all { it }
        val pendingAction = pendingBluetoothAction.getAndSet(null)
        if (granted) {
            pendingAction?.invoke()
        } else {
            if (activity != null && !BluetoothPermissions.shouldShowRationale(activity)) {
                showPermissionSettingsDialog = true
            } else {
                scope.launch { snackbarHostState.showSnackbar("需要蓝牙权限才能继续") }
            }
        }
    }

    fun ensureBluetoothPermissions(action: () -> Unit) {
        if (BluetoothPermissions.hasAll(context)) {
            action()
            return
        }
        pendingBluetoothAction.set(action)
        if (activity != null && BluetoothPermissions.shouldShowRationale(activity)) {
            showPermissionRationaleDialog = true
        } else {
            bluetoothPermissionLauncher.launch(BluetoothPermissions.required)
        }
    }

    fun requestBondedDevicePick(onPicked: (String, String?) -> Unit) {
        ensureBluetoothPermissions {
            val result = runCatching { loadBondedDeviceItems(context) }.getOrElse {
                scope.launch {
                    snackbarHostState.showSnackbar("读取已绑定设备失败: ${it.message ?: it::class.java.simpleName}")
                }
                return@ensureBluetoothPermissions
            }
            when (result) {
                is BondedDeviceLoadResult.Success -> {
                    if (result.devices.isEmpty()) {
                        scope.launch { snackbarHostState.showSnackbar("未找到已绑定设备，请先在系统蓝牙设置中配对") }
                    } else {
                        bondedDevices = result.devices
                        bondedDevicePickerOnSelect.set(onPicked)
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

    // Show error snackbar — selectedSession 用于 TopAppBar
    val selectedSession = state.selectedKey?.let { state.sessions[it] }

    // Back handler
    BackHandler {
        if (showAddServerDialog) {
            showAddServerDialog = false
        } else if (pagerState.currentPage == 0 && clientIsInDetail) {
            // Let ClientTabPage handle its own back
        } else if (pagerState.currentPage > 0 && serverIsInDetail) {
            // Let ServerTabPage handle its own back
        } else {
            onBackClick()
        }
    }

    // Determine TopAppBar content based on current tab
    val currentPage = pagerState.currentPage
    val isClientTab = currentPage == 0
    val currentServerTab = if (!isClientTab && currentPage - 1 < serverTabs.size) {
        serverTabs[currentPage - 1]
    } else null

    // 基于当前 pager page 获取对应 session，用于 Snackbar 错误提示
    val currentPageSession = if (isClientTab) {
        state.selectedKey?.let { state.sessions[it] }
    } else {
        currentServerTab?.let { tab -> state.sessions["server:${tab.tabId}"] }
    }
    LaunchedEffect(currentPageSession?.lastError) {
        currentPageSession?.lastError?.let { msg ->
            snackbarHostState.showSnackbar("错误: $msg")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    when {
                        isClientTab && !clientIsInDetail -> {
                            Text("SPP 客户端")
                        }
                        isClientTab && clientIsInDetail -> {
                            val selected = selectedSession?.device
                            if (selected != null) {
                                Column(
                                    modifier = Modifier.combinedClickable(
                                        onClick = {},
                                        onDoubleClick = { scrollToLatest?.invoke() },
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    )
                                ) {
                                    Text(
                                        text = selected.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "客户端 · ${selected.address}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            } else {
                                Text("SPP 客户端")
                            }
                        }
                        currentServerTab != null && serverIsInDetail -> {
                            Column(
                                modifier = Modifier.combinedClickable(
                                    onClick = {},
                                    onDoubleClick = { scrollToLatest?.invoke() },
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                )
                            ) {
                                Text(
                                    text = serverDetailAddress ?: currentServerTab.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (serverDetailIsHistory) "历史 · ${currentServerTab.name}" else "服务端 · ${currentServerTab.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        currentServerTab != null -> {
                            Column(
                                modifier = Modifier.combinedClickable(
                                    onClick = {},
                                    onDoubleClick = { scrollToLatest?.invoke() },
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                )
                            ) {
                                Text(
                                    text = currentServerTab.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "服务端 · UUID: ${currentServerTab.uuid}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        else -> Text("SPP 客户端")
                    }
                },
                navigationIcon = {
                    if (isClientTab && clientIsInDetail) {
                        IconButton(onClick = { clientDetailBackHandler?.invoke() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    } else if (!isClientTab && serverIsInDetail) {
                        IconButton(onClick = { serverDetailBackHandler?.invoke() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    } else {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.Default.Link, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    // No actions for Client tab (connect form or detail)
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            // ── Tab Bar ──
            PrimaryScrollableTabRow(
                selectedTabIndex = currentPage,
                edgePadding = 8.dp
            ) {
                // Client Tab
                Tab(
                    selected = currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("Client") }
                )

                // Server Tabs
                serverTabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = currentPage == index + 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index + 1) } },
                        text = {
                            Text(
                                text = tab.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.combinedClickable(
                                    onClick = { scope.launch { pagerState.animateScrollToPage(index + 1) } },
                                    onLongClick = { deleteTabId = tab.tabId },
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                )
                            )
                        }
                    )
                }

                // "+" Add button (not a real tab page)
                Tab(
                    selected = false,
                    onClick = { showAddServerDialog = true },
                    text = {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "添加 Server",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }

            // ── Pager ──
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1
            ) { page ->
                when (page) {
                    0 -> ClientTabPage(
                        vm = vm,
                        state = state,
                        snackbarHostState = snackbarHostState,
                        ensureBluetoothPermissions = ::ensureBluetoothPermissions,
                        requestBondedDevicePick = ::requestBondedDevicePick,
                        onScrollToLatest = { scrollFn -> scrollToLatest = scrollFn },
                        onIsInDetailChanged = { clientIsInDetail = it },
                        onBackHandler = { handler -> clientDetailBackHandler = handler }
                    )
                    else -> {
                        val tabIndex = page - 1
                        if (tabIndex < serverTabs.size) {
                            ServerTabPage(
                                serverTab = serverTabs[tabIndex],
                                vm = vm,
                                state = state,
                                ensureBluetoothPermissions = ::ensureBluetoothPermissions,
                                onScrollToLatest = { scrollFn -> scrollToLatest = scrollFn },
                                onIsInDetailChanged = { serverIsInDetail = it },
                                onBackHandler = { handler -> serverDetailBackHandler = handler },
                                onDetailInfo = { address, isHistory ->
                                    serverDetailAddress = address
                                    serverDetailIsHistory = isHistory
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Dialogs ──

    if (showAddServerDialog) {
        AddServerDialog(
            onDismiss = { showAddServerDialog = false },
            onConfirm = { uuid, name ->
                val newTabId = vm.addServerTab(uuid, name)
                showAddServerDialog = false
                // 记录待切换的 tabId，由 LaunchedEffect(serverTabs.size) 在状态更新后执行切换
                pendingScrollToTab = newTabId
            }
        )
    }

    // Delete Server Tab confirmation
    deleteTabId?.let { tabId ->
        val tabName = serverTabs.find { it.tabId == tabId }?.name ?: "Server"
        AlertDialog(
            onDismissRequest = { deleteTabId = null },
            title = { Text("删除 Server Tab") },
            text = { Text("确定删除「$tabName」？将停止监听并清理所有会话数据。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeServerTab(tabId)
                    deleteTabId = null
                    // If we were on the deleted tab, go back to Client
                    scope.launch {
                        if (pagerState.currentPage >= pageCount - 1) {
                            pagerState.animateScrollToPage(0)
                        }
                    }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteTabId = null }) { Text("取消") } }
        )
    }

    if (showPermissionRationaleDialog) {
        AlertDialog(
            onDismissRequest = {
                @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
                showPermissionRationaleDialog = false
                pendingBluetoothAction.set(null)
            },
            title = { Text("需要蓝牙权限") },
            text = { Text("SPP 连接/监听需要授予附近设备相关的蓝牙连接与扫描权限。") },
            confirmButton = {
                TextButton(onClick = {
                    @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
                    showPermissionRationaleDialog = false
                    bluetoothPermissionLauncher.launch(BluetoothPermissions.required)
                }) { Text("去授权") }
            },
            dismissButton = {
                TextButton(onClick = {
                    @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
                    showPermissionRationaleDialog = false
                    pendingBluetoothAction.set(null)
                }) { Text("取消") }
            }
        )
    }

    if (showPermissionSettingsDialog) {
        @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
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
                TextButton(onClick = { showPermissionSettingsDialog = false }) { Text("取消") }
            }
        )
    }

    if (showBondedDevicePicker) {
        DevicePickerSheet(
            bondedDevices = bondedDevices,
            showBonded = true,
            showScanned = false,
            onDismissRequest = {
                @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
                showBondedDevicePicker = false
                bondedDevicePickerOnSelect.set(null)
            },
            onSelect = { addr, name, _ ->
                @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
                showBondedDevicePicker = false
                bondedDevicePickerOnSelect.getAndSet(null)?.invoke(addr, name)
            }
        )
    }
}
