package top.expli.bluetoothtester

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Scanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import top.expli.bluetoothtester.ui.theme.BluetoothTesterTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BluetoothTesterTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    // 定义动画参数
    val animationDuration = 300
    val slideOffset = 300

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
                }
            )
        }

        composable<Route.Settings> {
            SettingsScreen(
                onBackClick = {
                    navController.navigateUp()
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
    }
}

@Immutable
data class MenuItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val gradientColors: List<Color> = listOf()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToScan: () -> Unit = {},
    onNavigateToPaired: () -> Unit = {},
    onNavigateToBleScanner: () -> Unit = {},
    onNavigateToClassic: () -> Unit = {}
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val menuItems = listOf(
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
            id = "settings",
            title = "设置",
            description = "应用设置和蓝牙权限管理",
            icon = Icons.Default.Settings
        )
    )

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "蓝牙测试工具",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Medium
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
                scrollBehavior = scrollBehavior
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

            items(menuItems) { item ->
                MainMenuCard(
                    title = item.title,
                    description = item.description,
                    icon = item.icon,
                    onClick = {
                        when (item.id) {
                            "scan" -> onNavigateToScan()
                            "paired" -> onNavigateToPaired()
                            "ble_scanner" -> onNavigateToBleScanner()
                            "classic_bluetooth" -> onNavigateToClassic()
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

//@Immutable
//sealed interface MainUiItem {
//    val key: String
//
//    @Immutable
//    data class Header(val title: String, override val key: String) : MainUiItem
//
//    @Immutable
//    data class MenuCard(
//        val destination: MainDestination,
//        val title: String,
//        val description: String,
//        override val key: String
//    ) : MainUiItem
//}

@Composable
private fun MainMenuCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
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
            // Icon with flat circular background
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Title and description
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Arrow icon
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "进入",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
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