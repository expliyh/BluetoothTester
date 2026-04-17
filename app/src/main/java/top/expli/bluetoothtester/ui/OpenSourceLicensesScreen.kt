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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri

@Immutable
private data class OssLibrary(
    val name: String,
    val author: String,
    val url: String,
    val license: String
)

private val ossLibraries = listOf(
    OssLibrary(
        name = "AndroidX Core KTX",
        author = "Google",
        url = "https://developer.android.com/jetpack/androidx/releases/core",
        license = "Apache-2.0"
    ),
    OssLibrary(
        name = "AndroidX AppCompat",
        author = "Google",
        url = "https://developer.android.com/jetpack/androidx/releases/appcompat",
        license = "Apache-2.0"
    ),
    OssLibrary(
        name = "AndroidX Activity Compose",
        author = "Google",
        url = "https://developer.android.com/jetpack/androidx/releases/activity",
        license = "Apache-2.0"
    ),
    OssLibrary(
        name = "AndroidX Lifecycle",
        author = "Google",
        url = "https://developer.android.com/jetpack/androidx/releases/lifecycle",
        license = "Apache-2.0"
    ),
    OssLibrary(
        name = "AndroidX Compose",
        author = "Google",
        url = "https://developer.android.com/jetpack/compose",
        license = "Apache-2.0"
    ),
    OssLibrary(
        name = "AndroidX Compose Material 3",
        author = "Google",
        url = "https://developer.android.com/jetpack/androidx/releases/compose-material3",
        license = "Apache-2.0"
    ),
    OssLibrary(
        name = "AndroidX Compose Material Icons Extended",
        author = "Google",
        url = "https://developer.android.com/reference/kotlin/androidx/compose/material/icons/package-summary",
        license = "Apache-2.0"
    ),
    OssLibrary(
        name = "AndroidX Navigation Compose",
        author = "Google",
        url = "https://developer.android.com/jetpack/androidx/releases/navigation",
        license = "Apache-2.0"
    ),
    OssLibrary(
        name = "AndroidX DataStore Preferences",
        author = "Google",
        url = "https://developer.android.com/jetpack/androidx/releases/datastore",
        license = "Apache-2.0"
    ),
    OssLibrary(
        name = "AndroidX Compose Foundation",
        author = "Google",
        url = "https://developer.android.com/jetpack/androidx/releases/compose-foundation",
        license = "Apache-2.0"
    ),
    OssLibrary(
        name = "Material Components for Android",
        author = "Google",
        url = "https://github.com/material-components/material-components-android",
        license = "Apache-2.0"
    ),
    OssLibrary(
        name = "Kotlinx Serialization JSON",
        author = "JetBrains",
        url = "https://github.com/Kotlin/kotlinx.serialization",
        license = "Apache-2.0"
    ),
    OssLibrary(
        name = "Shizuku API",
        author = "RikkaApps",
        url = "https://github.com/RikkaApps/Shizuku-API",
        license = "Apache-2.0"
    ),
    OssLibrary(
        name = "HiddenApiRefinePlugin (Refine Runtime)",
        author = "RikkaApps",
        url = "https://github.com/RikkaApps/HiddenApiRefinePlugin",
        license = "MIT"
    ),
    OssLibrary(
        name = "libsu",
        author = "topjohnwu",
        url = "https://github.com/topjohnwu/libsu",
        license = "Apache-2.0"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenSourceLicensesScreen(onBackClick: () -> Unit) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val context = LocalContext.current

    Surface(color = surfaceColor) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "开放源代码声明",
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = "本应用使用了以下开源软件",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                }

                items(items = ossLibraries, key = { it.name }) { lib ->
                    OssLibraryCard(
                        library = lib,
                        onClick = { openUrl(context, lib.url) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun OssLibraryCard(library: OssLibrary, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = library.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = library.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LicenseBadge(license = library.license)
        }
    }
}

@Composable
private fun LicenseBadge(license: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = license,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        data = url.toUri()
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
