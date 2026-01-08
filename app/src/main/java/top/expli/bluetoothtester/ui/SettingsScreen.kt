package top.expli.bluetoothtester.ui

import android.content.Context
import android.content.Intent
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import top.expli.bluetoothtester.model.AppUpdateUiState

enum class ThemeOption { System, Light, Dark }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onNavigateToAdvancedPermission: () -> Unit,
    themeOption: ThemeOption,
    onThemeChange: (ThemeOption) -> Unit,
    dynamicColorEnabled: Boolean,
    onDynamicColorChange: (Boolean) -> Unit,
    updateState: AppUpdateUiState,
    onCheckForUpdates: () -> Unit,
    onUpdateGithubCdn: (String) -> Unit,
    resolveUrl: (String?) -> String?
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val context = LocalContext.current
    val resolveUrlState by rememberUpdatedState(resolveUrl)
    val onUpdateGithubCdnState by rememberUpdatedState(onUpdateGithubCdn)

    var showUpdateDialog by remember { mutableStateOf(false) }
    var showGithubCdnDialog by remember { mutableStateOf(false) }
    var githubCdnDraft by remember { mutableStateOf(updateState.githubCdn) }

    Surface(color = surfaceColor) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "设置",
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
                    SettingsSectionHeader(title = "通用")
                }

                item {
                    var notificationsEnabled by remember { mutableStateOf(true) }
                    @Suppress("ASSIGNED_VALUE_IS_NEVER_READ")
                    SettingsToggleItem(
                        icon = Icons.Default.Notifications,
                        title = "通知",
                        description = "接收蓝牙设备连接通知",
                        checked = notificationsEnabled,
                        onCheckedChange = { notificationsEnabled = it }
                    )
                }

                item {
                    var autoReconnect by remember { mutableStateOf(false) }
                    SettingsToggleItem(
                        icon = Icons.Default.Bluetooth,
                        title = "自动重连",
                        description = "断开时自动尝试重新连接设备",
                        checked = autoReconnect,
                        onCheckedChange = {}
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsSectionHeader(title = "外观")
                }

                item {
                    SettingsClickableItem(
                        icon = Icons.Default.Palette,
                        title = "主题",
                        description = "浅色 / 深色 / 跟随系统",
                        onClick = { }
                    )
                }

                item {
                    ThemeSelector(current = themeOption, onSelect = onThemeChange)
                }

                item {
                    SettingsToggleItem(
                        icon = Icons.Default.Palette,
                        title = "动态取色",
                        description = "使用系统壁纸颜色 (Android 12+)",
                        checked = dynamicColorEnabled,
                        onCheckedChange = { onDynamicColorChange(it) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsSectionHeader(title = "隐私与安全")
                }

                item {
                    SettingsClickableItem(
                        icon = Icons.Default.Security,
                        title = "权限管理",
                        description = "查看和管理应用权限",
                        onClick = { /* TODO: 打开权限管理 */ }
                    )
                }

                item {
                    SettingsClickableItem(
                        icon = Icons.Default.Security,
                        title = "高级权限",
                        description = "Shizuku / Root / ADB",
                        onClick = onNavigateToAdvancedPermission
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsSectionHeader(title = "关于")
                }

                item {
                    SettingsClickableItem(
                        icon = Icons.Default.Info,
                        title = "关于应用",
                        description = "版本 ${updateState.currentVersionName}",
                        onClick = { /* TODO: 打开关于页面 */ }
                    )
                }

                item {
                    val (desc, status) = updateDescription(updateState)
                    SettingsClickableItem(
                        icon = Icons.Default.SystemUpdate,
                        title = "检查更新",
                        description = desc,
                        statusText = status,
                        onClick = {
                            onCheckForUpdates()
                            showUpdateDialog = true
                        }
                    )
                }

                item {
                    SettingsClickableItem(
                        icon = Icons.Default.Link,
                        title = "GitHub CDN",
                        description = if (updateState.githubCdn.isBlank()) "未设置（直连 GitHub）" else updateState.githubCdn,
                        onClick = {
                            githubCdnDraft = updateState.githubCdn
                            showGithubCdnDialog = true
                        }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    if (showUpdateDialog) {
        val release = updateState.latestRelease
        val releaseUrl = resolveUrlState(release?.htmlUrl)
        val apkUrl = resolveUrlState(release?.apkAssetUrl)
        val title =
            if (updateState.updateAvailable) "发现新版本" else "检查更新"
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("当前版本：${updateState.currentVersionName}")
                    when {
                        updateState.checking -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Text("正在检查更新…")
                            }
                        }

                        updateState.lastError != null -> {
                            Text("检查失败：${updateState.lastError}")
                        }

                        release != null -> {
                            Text("最新版本：${release.tagName}")
                            release.publishedAt?.let { Text("发布时间：$it") }
                            release.name?.takeIf { it.isNotBlank() }?.let { Text(it) }
                        }

                        else -> Text("暂无结果，请稍后重试。")
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!updateState.checking) {
                        releaseUrl?.let {
                            TextButton(onClick = { openUrl(context, it) }) { Text("发布页") }
                        }
                        if (updateState.updateAvailable) {
                            apkUrl?.let {
                                TextButton(onClick = { openUrl(context, it) }) { Text("下载") }
                            }
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) { Text("关闭") }
            }
        )
    }

    if (showGithubCdnDialog) {
        val normalizedDraft = githubCdnDraft.trim()
        val valid = normalizedDraft.isBlank() ||
                normalizedDraft.startsWith("https://") ||
                normalizedDraft.startsWith("http://")
        AlertDialog(
            onDismissRequest = { showGithubCdnDialog = false },
            title = { Text("设置 GitHub CDN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("用于加速 GitHub API / Release 下载。留空表示直连。")
                    OutlinedTextField(
                        value = githubCdnDraft,
                        onValueChange = { githubCdnDraft = it },
                        singleLine = true,
                        placeholder = { Text("例如：https://ghproxy.com/ 或 https://mirror.ghproxy.com/{url}") },
                        supportingText = {
                            if (!valid) {
                                Text("请输入以 http:// 或 https:// 开头的地址，或留空。")
                            }
                        },
                        isError = !valid,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = valid,
                    onClick = {
                        onUpdateGithubCdnState(githubCdnDraft)
                        showGithubCdnDialog = false
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showGithubCdnDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsToggleItem(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    statusText: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(8.dp)
                )
            }

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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                statusText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

@Composable
private fun SettingsClickableItem(
    icon: ImageVector,
    title: String,
    description: String,
    statusText: String? = null,
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
            defaultElevation = 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(8.dp)
                )
            }

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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                statusText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "查看更多",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ThemeSelector(current: ThemeOption, onSelect: (ThemeOption) -> Unit) {
    val options = listOf(
        ThemeOption.System to "跟随系统",
        ThemeOption.Light to "浅色",
        ThemeOption.Dark to "深色"
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { (option, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RadioButton(
                        selected = current == option,
                        onClick = { onSelect(option) },
                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun updateDescription(state: AppUpdateUiState): Pair<String, String?> {
    if (state.checking) return "正在检查更新…" to null
    state.lastError?.let { err ->
        val short = err.lineSequence().firstOrNull()?.take(80).orEmpty()
        return "检查更新失败" to short
    }
    val latest = state.latestRelease ?: return "点击检查更新" to null
    return if (state.updateAvailable) {
        "发现新版本：${latest.tagName}" to null
    } else {
        "已是最新：${latest.tagName}" to null
    }
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = url.toUri()
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
