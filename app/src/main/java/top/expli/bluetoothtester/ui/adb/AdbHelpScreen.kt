package top.expli.bluetoothtester.ui.adb

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import top.expli.bluetoothtester.adb.AdbCommandDoc
import top.expli.bluetoothtester.adb.AdbCommandDocs
import top.expli.bluetoothtester.adb.AdbCommandReceiver


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbHelpScreen(onBackClick: () -> Unit) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val groupedCommands = remember { AdbCommandDocs.commands.groupBy { it.group } }
    val groupOrder = remember { listOf("SPP", "GATT", "L2CAP", "扫描/广播", "通用") }

    val surfaceColor = MaterialTheme.colorScheme.surface
    Surface(color = surfaceColor) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "ADB 帮助",
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Intent Action 前缀 + 通用参数格式 ──
                item {
                    ActionPrefixSection(
                        onCopy = { text ->
                            copyToClipboard(context, text)
                            scope.launch { snackbarHostState.showSnackbar("已复制") }
                        }
                    )
                }

                // ── 快速开始 ──
                item {
                    QuickStartSection(
                        onCopy = { text ->
                            copyToClipboard(context, text)
                            scope.launch { snackbarHostState.showSnackbar("已复制") }
                        }
                    )
                }

                // ── 分组命令列表 ──
                items(groupOrder) { group ->
                    val commands = groupedCommands[group] ?: return@items
                    CommandGroupCard(
                        group = group,
                        commands = commands,
                        onCopy = { text ->
                            copyToClipboard(context, text)
                            scope.launch { snackbarHostState.showSnackbar("已复制") }
                        }
                    )
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("ADB Command", text))
}


@Composable
private fun ActionPrefixSection(onCopy: (String) -> Unit) {
    val actionPrefix = AdbCommandReceiver.ACTION
    val fullPrefix = "adb shell am broadcast -W -a $actionPrefix"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Intent Action 前缀",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "所有 ADB 命令通过有序广播发送，统一使用以下 Action：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            CodeBlock(text = actionPrefix, onCopy = onCopy)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "通用命令格式",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "使用 --es 传递字符串参数，command 字段指定操作类型：",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            CodeBlock(
                text = "$fullPrefix --es command \"<命令>\" --es <参数名> \"<参数值>\"",
                onCopy = onCopy
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "使用 -W 标志以获取有序广播返回值（JSON 格式）。结果同时输出到 logcat（TAG: BtTesterADB）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


@Composable
private fun QuickStartSection(onCopy: (String) -> Unit) {
    val actionPrefix = AdbCommandReceiver.ACTION
    val prefix = "adb shell am broadcast -W -a $actionPrefix"
    val steps = listOf(
        "# 1. 注册 SPP 设备" to
                "$prefix --es command \"spp.register\" --es name \"MyDevice\" --es address \"AA:BB:CC:DD:EE:FF\"",
        "# 2. 连接设备" to
                "$prefix --es command \"spp.connect\" --es address \"AA:BB:CC:DD:EE:FF\"",
        "# 3. 发送数据" to
                "$prefix --es command \"spp.send\" --es address \"AA:BB:CC:DD:EE:FF\" --es data \"Hello\"",
        "# 4. 启动测速 (10秒)" to
                "$prefix --es command \"spp.speed_test.start\" --es address \"AA:BB:CC:DD:EE:FF\" --es duration \"10\"",
        "# 5. 停止测速" to
                "$prefix --es command \"spp.speed_test.stop\" --es address \"AA:BB:CC:DD:EE:FF\"",
        "# 6. 断开连接" to
                "$prefix --es command \"spp.disconnect\" --es address \"AA:BB:CC:DD:EE:FF\""
    )
    val fullScript = steps.joinToString("\n\n") { "${it.first}\n${it.second}" }

    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🚀 快速开始",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Row {
                    IconButton(onClick = { onCopy(fullScript) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "复制全部",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "收起" else "展开",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Text(
                text = "完整的端到端 SPP 测试流程：注册 → 连接 → 发送 → 测速 → 断开",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    steps.forEachIndexed { index, (comment, cmd) ->
                        Text(
                            text = comment,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        CodeBlock(text = cmd, onCopy = onCopy)
                        if (index < steps.lastIndex) {
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun CommandGroupCard(
    group: String,
    commands: List<AdbCommandDoc>,
    onCopy: (String) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = group,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${commands.size} 个命令",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    commands.forEachIndexed { index, cmd ->
                        CommandItem(cmd = cmd, onCopy = onCopy)
                        if (index < commands.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun CommandItem(cmd: AdbCommandDoc, onCopy: (String) -> Unit) {
    Column {
        // Command name + description
        Text(
            text = cmd.command,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = cmd.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // ADB command example
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "命令示例",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        CodeBlock(text = cmd.exampleArgs, onCopy = onCopy)

        // Parameter table
        if (cmd.params.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "参数",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            ParamTable(params = cmd.params)
        }

        // Return JSON example
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "返回示例",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        CodeBlock(text = cmd.returnExample, onCopy = onCopy)
    }
}


@Composable
private fun ParamTable(params: List<top.expli.bluetoothtester.adb.AdbParamDoc>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "参数名",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1.2f)
                )
                Text(
                    text = "类型",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.8f)
                )
                Text(
                    text = "必填",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.6f)
                )
                Text(
                    text = "说明",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1.8f)
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
            // Rows
            params.forEach { param ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = param.name,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1.2f)
                    )
                    Text(
                        text = param.type,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.8f)
                    )
                    Text(
                        text = if (param.required) "✓" else param.defaultValue ?: "—",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (param.required) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.6f)
                    )
                    Text(
                        text = param.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1.8f)
                    )
                }
            }
        }
    }
}


@Composable
private fun CodeBlock(text: String, onCopy: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(onClick = { onCopy(text) }, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = "复制",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
