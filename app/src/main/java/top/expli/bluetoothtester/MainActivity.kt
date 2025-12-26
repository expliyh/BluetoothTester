package top.expli.bluetoothtester

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Scanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.expli.bluetoothtester.shizuku.ShizukuHelper
import top.expli.bluetoothtester.shizuku.ShizukuState
import top.expli.bluetoothtester.shizuku.ShizukuServiceState
import top.expli.bluetoothtester.ui.theme.BluetoothTesterTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BluetoothTesterTheme {
                LaunchedEffect(Unit) {
                    withContext(Dispatchers.IO) {
                        ShizukuHelper.init(
                            applicationContext
                        )
                    }
                }
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    var renderFullUi by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        renderFullUi = true
    }

    // 定义动画参数
    val animationDuration = 300
    val slideOffset = 300

    if (!renderFullUi) {
        Scaffold { inner ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .background(MaterialTheme.colorScheme.surface)
            )
        }
        return
    }

    NavHost(
        navController = navController,
        startDestination = Route.Main,
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { slideOffset },
                animationSpec = tween(animationDuration)
            ) + fadeIn(animationSpec = tween(animationDuration))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { -slideOffset },
                animationSpec = tween(animationDuration)
            ) + fadeOut(animationSpec = tween(animationDuration))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { -slideOffset },
                animationSpec = tween(animationDuration)
            ) + fadeIn(animationSpec = tween(animationDuration))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { slideOffset },
                animationSpec = tween(animationDuration)
            ) + fadeOut(animationSpec = tween(animationDuration))
        }
    ) {
        composable<Route.Main> {
            MainScreen(
                onNavigateToSettings = {
                    navController.navigate(Route.Settings)
                },
                onNavigateToScan = {
                    navController.navigate(Route.Scan)
                },
                onNavigateToPaired = {
                    navController.navigate(Route.PairedDevices)
                },
                onNavigateToBleScanner = {
                    navController.navigate(Route.BleScanner)
                },
                onNavigateToClassic = {
                    navController.navigate(Route.ClassicBluetooth)
                },
                onNavigateToBluetoothToggle = {
                    navController.navigate(Route.BluetoothToggle)
                }
            )
        }

        composable<Route.Settings> {
            SettingsScreen(
                onBackClick = {
                    navController.navigateUp()
                },
                onNavigateToAdvancedPermission = {
                    navController.navigate(Route.AdvancedPermission)
                }
            )
        }

        // TODO: 添加其他界面的 composable
        composable<Route.Scan> {
            PlaceholderScreen(
                title = "扫描设备",
                onBackClick = { navController.navigateUp() }
            )
        }

        composable<Route.PairedDevices> {
            PlaceholderScreen(
                title = "已配对设备",
                onBackClick = { navController.navigateUp() }
            )
        }

        composable<Route.BleScanner> {
            PlaceholderScreen(
                title = "BLE 扫描器",
                onBackClick = { navController.navigateUp() }
            )
        }

        composable<Route.ClassicBluetooth> {
            PlaceholderScreen(
                title = "经典蓝牙",
                onBackClick = { navController.navigateUp() }
            )
        }

        composable<Route.BluetoothToggle> {
            val vm: BluetoothToggleViewModel = viewModel()
            val uiState = vm.uiState.collectAsState()
            BluetoothToggleScreen(
                state = uiState.value.state,
                inProgress = uiState.value.inProgress,
                lastError = uiState.value.lastError,
                loopRunning = uiState.value.loopRunning,
                loopIterations = uiState.value.loopIterations,
                loopCompleted = uiState.value.loopCompleted,
                loopOnDurationMs = uiState.value.loopOnDurationMs,
                loopOffDurationMs = uiState.value.loopOffDurationMs,
                onToggle = { vm.toggle() },
                onStartLoop = { vm.startLoop() },
                onStopLoop = { vm.stopLoop() },
                onLoopIterationsChange = { vm.updateLoopIterations(it) },
                onLoopOnDurationChange = { vm.updateLoopOnDuration(it) },
                onLoopOffDurationChange = { vm.updateLoopOffDuration(it) },
                onBackClick = { navController.navigateUp() }
            )
        }

        composable<Route.AdvancedPermission> {
            AdvancedPermissionScreen(
                onBackClick = { navController.navigateUp() }
            )
        }
    }
}

@Immutable
data class MenuItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val gradientColors: List<Color> = listOf(),
    val requirePrivilege: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToScan: () -> Unit = {},
    onNavigateToPaired: () -> Unit = {},
    onNavigateToBleScanner: () -> Unit = {},
    onNavigateToClassic: () -> Unit = {},
    onNavigateToBluetoothToggle: () -> Unit = {}
) {
    val context = LocalContext.current
    var shizukuState by remember { mutableStateOf<ShizukuState?>(null) }
    val serviceState by ShizukuHelper.serviceStateFlow.collectAsState()
    var renderMain by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        renderMain = true
    }
    LaunchedEffect(context) {
        val appCtx = context.applicationContext
        shizukuState = withContext(Dispatchers.IO) { ShizukuHelper.currentState(appCtx) }
        ShizukuHelper.observeState(appCtx) { state ->
            shizukuState = state
        }
    }
    val resolvedShizukuState = shizukuState ?: ShizukuState.NotRunning

    val menuItems = remember {
        listOf(
            MenuItem(
                id = "scan",
                title = "扫描设备",
                description = "搜索附近的蓝牙设备",
                icon = Icons.AutoMirrored.Filled.BluetoothSearching
            ),
            MenuItem(
                id = "paired",
                title = "已配对设备",
                description = "查看和管理已配对的蓝牙设备",
                icon = Icons.Default.Devices
            ),
            MenuItem(
                id = "ble_scanner",
                title = "BLE 扫描器",
                description = "扫描低功耗蓝牙设备并查看详细信息",
                icon = Icons.Default.Scanner
            ),
            MenuItem(
                id = "classic_bluetooth",
                title = "经典蓝牙",
                description = "测试经典蓝牙连接和数据传输",
                icon = Icons.Default.Bluetooth
            ),
            MenuItem(
                id = "bluetooth_toggle",
                title = "蓝牙开关",
                description = "通过 Shizuku 控制蓝牙开关",
                icon = Icons.Default.ToggleOn,
                requirePrivilege = true
            ),
            MenuItem(
                id = "settings",
                title = "设置",
                description = "应用设置和蓝牙权限管理",
                icon = Icons.Default.Settings
            )
        )
    }

    if (!renderMain) {
        Scaffold { inner ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner)
                    .background(MaterialTheme.colorScheme.surface)
            )
        }
        return
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "蓝牙测试工具",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium
                        )
                        ShizukuStatusChip(
                            permissionState = resolvedShizukuState,
                            serviceState = serviceState
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "功能菜单",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                )
            }

            items(
                items = menuItems,
                key = { it.id }
            ) { item ->
                MainMenuCard(
                    title = item.title,
                    description = item.description,
                    icon = item.icon,
                    requirePrivilege = item.requirePrivilege,
                    isBinding = item.requirePrivilege && serviceState == ShizukuServiceState.Binding,
                    isPrivilegedMissing = item.requirePrivilege && (resolvedShizukuState != ShizukuState.Granted || serviceState != ShizukuServiceState.Connected),
                    onClick = {
                        if (item.requirePrivilege && (resolvedShizukuState != ShizukuState.Granted || serviceState != ShizukuServiceState.Connected)) return@MainMenuCard
                        when (item.id) {
                            "scan" -> onNavigateToScan()
                            "paired" -> onNavigateToPaired()
                            "ble_scanner" -> onNavigateToBleScanner()
                            "classic_bluetooth" -> onNavigateToClassic()
                            "bluetooth_toggle" -> onNavigateToBluetoothToggle()
                            "settings" -> onNavigateToSettings()
                        }
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun ShizukuStatusChip(permissionState: ShizukuState, serviceState: ShizukuServiceState) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusChip(
            label = when (permissionState) {
                ShizukuState.Granted -> "特权: 已授权"
                ShizukuState.NoPermission -> "特权: 未授权"
                ShizukuState.NotRunning -> "特权: 未运行"
                ShizukuState.NotInstalled -> "特权: 未安装"
            }, color = when (permissionState) {
                ShizukuState.Granted -> MaterialTheme.colorScheme.primary
                ShizukuState.NoPermission -> MaterialTheme.colorScheme.tertiary
                ShizukuState.NotRunning -> MaterialTheme.colorScheme.error
                ShizukuState.NotInstalled -> MaterialTheme.colorScheme.error
            }
        )

        StatusChip(
            label = when (serviceState) {
                ShizukuServiceState.Connected -> "服务: 已连接"
                ShizukuServiceState.Binding -> "服务: 连接中"
                ShizukuServiceState.NotConnected -> "服务: 未连接"
            }, color = when (serviceState) {
                ShizukuServiceState.Connected -> MaterialTheme.colorScheme.primary
                ShizukuServiceState.Binding -> MaterialTheme.colorScheme.secondary
                ShizukuServiceState.NotConnected -> MaterialTheme.colorScheme.error
            }
        )
    }
}

@Composable
private fun StatusChip(label: String, color: Color) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))
    ) {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun MainMenuCard(
    title: String,
    description: String,
    icon: ImageVector,
    requirePrivilege: Boolean,
    isBinding: Boolean,
    isPrivilegedMissing: Boolean,
    onClick: () -> Unit
) {
    val disabled = isPrivilegedMissing
    val textColor =
        if (disabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface
    val descColor =
        if (disabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f) else MaterialTheme.colorScheme.onSurfaceVariant
    val iconTint =
        if (disabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onPrimaryContainer

    Card(
        onClick = { if (!disabled) onClick() },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (disabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                if (isBinding) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = descColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "进入",
                tint = if (disabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    BluetoothTesterTheme {
        Greeting("Android")
    }
}