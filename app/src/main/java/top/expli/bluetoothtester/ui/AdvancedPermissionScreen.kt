package top.expli.bluetoothtester.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.expli.bluetoothtester.privilege.shizuku.ShizukuHelper
import top.expli.bluetoothtester.privilege.shizuku.ShizukuState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedPermissionScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val shizukuState by ShizukuHelper.stateFlow.collectAsState()

    Surface(color = MaterialTheme.colorScheme.surface) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "高级权限",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    )
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Text(
                        text = "Shizuku",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                }

                item {
                    ShizukuCard(
                        state = shizukuState,
                        onRequestPermission = {
                            ShizukuHelper.requestPermission { /* stateFlow 会自动更新 */ }
                        },
                        onOpenApp = { ShizukuHelper.launchManagerApp(context) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Root / ADB (预留)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                    )
                }

                item {
                    PlaceholderCard(
                        icon = Icons.Default.Build,
                        title = "Root 支持",
                        description = "计划中：通过 root 运行需要的命令"
                    )
                }

                item {
                    PlaceholderCard(
                        icon = Icons.Default.BugReport,
                        title = "ADB 支持",
                        description = "计划中：通过 adb 授权执行命令"
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    InfoCard(
                        icon = Icons.Default.Info,
                        title = "说明",
                        lines = listOf(
                            "仅支持 Shizuku v11+",
                            "未安装或未运行时将提示前往管理器",
                            "撤销授权请在 Shizuku 管理器中操作"
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ShizukuCard(
    state: ShizukuState,
    onRequestPermission: () -> Unit,
    onOpenApp: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RowTitle(icon = Icons.Default.Shield, title = "Shizuku 权限")

            when (state) {
                ShizukuState.NotInstalled -> {
                    StatusText("未安装 Shizuku，点击打开管理器安装", MaterialTheme.colorScheme.error)
                    Button(onClick = onOpenApp) {
                        Text(text = "打开 Shizuku 管理器")
                    }
                }

                ShizukuState.NotRunning -> {
                    StatusText("Shizuku 未运行，请在管理器中启动", MaterialTheme.colorScheme.error)
                    Button(onClick = onOpenApp) {
                        Text(text = "打开 Shizuku 管理器")
                    }
                }

                ShizukuState.NoPermission -> {
                    StatusText("未授权，需请求 Shizuku 权限", MaterialTheme.colorScheme.tertiary)
                    Button(onClick = onRequestPermission) {
                        Text(text = "请求授权")
                    }
                }

                ShizukuState.Granted -> {
                    StatusText("已授权", MaterialTheme.colorScheme.primary)
                    Button(onClick = onOpenApp) {
                        Text(text = "打开 Shizuku 管理器")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RowTitle(icon = icon, title = title)
            StatusText(description, MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoCard(
    icon: ImageVector,
    title: String,
    lines: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RowTitle(icon = icon, title = title)
            lines.forEach { line ->
                StatusText(line, MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RowTitle(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(8.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StatusText(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = color
    )
}
