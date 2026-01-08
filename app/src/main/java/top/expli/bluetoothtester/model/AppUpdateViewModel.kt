package top.expli.bluetoothtester.model

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import top.expli.bluetoothtester.data.SettingsStore
import top.expli.bluetoothtester.util.AppRuntimeInfo
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

enum class AppUpdateChannel { Stable, Canary }

data class AppUpdateRelease(
    val tagName: String,
    val name: String?,
    val body: String?,
    val htmlUrl: String?,
    val publishedAt: String?,
    val apkAssetUrl: String?
)

data class AppUpdateUiState(
    val channel: AppUpdateChannel,
    val currentVersionName: String,
    val githubCdn: String = "",
    val checking: Boolean = false,
    val lastError: String? = null,
    val latestRelease: AppUpdateRelease? = null,
    val updateAvailable: Boolean = false,
    val lastCheckedAtMs: Long? = null
)

class AppUpdateViewModel(app: Application) : AndroidViewModel(app) {
    private companion object {
        private const val GITHUB_OWNER = "expliyh"
        private const val GITHUB_REPO = "BluetoothTester"
        private const val GITHUB_API = "https://api.github.com"
    }

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val currentVersionName: String = AppRuntimeInfo.versionName(app)

    private val channel: AppUpdateChannel =
        if (isCanaryVersionName(currentVersionName)) AppUpdateChannel.Canary else AppUpdateChannel.Stable

    private val _uiState =
        MutableStateFlow(
            AppUpdateUiState(
                channel = channel,
                currentVersionName = currentVersionName
            )
        )
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    private var checkJob: Job? = null

    init {
        viewModelScope.launch {
            var autoChecked = false
            SettingsStore.observe(getApplication<Application>()).collect { settings ->
                _uiState.update { it.copy(githubCdn = settings.githubCdn) }
                if (!autoChecked && channel == AppUpdateChannel.Canary) {
                    autoChecked = true
                    checkForUpdates()
                }
            }
        }
    }

    fun checkForUpdates() {
        if (checkJob?.isActive == true) return

        checkJob = viewModelScope.launch {
            _uiState.update { it.copy(checking = true, lastError = null) }
            val githubCdn = uiState.value.githubCdn
            val now = System.currentTimeMillis()
            runCatching {
                withContext(Dispatchers.IO) {
                    fetchLatestRelease(
                        owner = GITHUB_OWNER,
                        repo = GITHUB_REPO,
                        channel = channel,
                        githubCdn = githubCdn
                    )
                }
            }.onSuccess { release ->
                val available = isUpdateAvailable(
                    currentVersionName = uiState.value.currentVersionName,
                    latestTagName = release.tagName
                )
                _uiState.update {
                    it.copy(
                        checking = false,
                        latestRelease = release,
                        updateAvailable = available,
                        lastError = null,
                        lastCheckedAtMs = now
                    )
                }
            }.onFailure { t ->
                _uiState.update {
                    it.copy(
                        checking = false,
                        lastError = t.message ?: t.toString(),
                        lastCheckedAtMs = now
                    )
                }
            }
        }
    }

    fun updateGithubCdn(cdn: String) {
        viewModelScope.launch {
            SettingsStore.updateGithubCdn(getApplication<Application>(), cdn)
        }
    }

    fun resolveUrl(originalUrl: String?): String? {
        if (originalUrl.isNullOrBlank()) return null
        return applyGitHubCdn(originalUrl, uiState.value.githubCdn)
    }

    private fun fetchLatestRelease(
        owner: String,
        repo: String,
        channel: AppUpdateChannel,
        githubCdn: String
    ): AppUpdateRelease {
        val release = when (channel) {
            AppUpdateChannel.Stable -> {
                val url = "$GITHUB_API/repos/$owner/$repo/releases/latest"
                val body = httpGet(applyGitHubCdn(url, githubCdn))
                json.decodeFromString(GitHubRelease.serializer(), body)
            }

            AppUpdateChannel.Canary -> {
                val url = "$GITHUB_API/repos/$owner/$repo/releases?per_page=20"
                val body = httpGet(applyGitHubCdn(url, githubCdn))
                val list = json.decodeFromString(ListSerializer(GitHubRelease.serializer()), body)
                list.firstOrNull { !it.draft && it.prerelease }
                    ?: throw IOException("No prerelease found")
            }
        }

        val bestApkAsset = pickBestApkAsset(release.assets)
        return AppUpdateRelease(
            tagName = release.tagName,
            name = release.name,
            body = release.body,
            htmlUrl = release.htmlUrl,
            publishedAt = release.publishedAt,
            apkAssetUrl = bestApkAsset?.downloadUrl
        )
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "BluetoothTester/$currentVersionName")
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = true
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val msg = body.lineSequence().firstOrNull()?.take(200) ?: "HTTP $code"
            throw IOException("HTTP $code: $msg")
        }
        return body
    }

    private fun pickBestApkAsset(assets: List<GitHubAsset>): GitHubAsset? {
        val supportedAbis = Build.SUPPORTED_ABIS?.toList().orEmpty()
        val apkAssets = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        if (apkAssets.isEmpty()) return null

        return apkAssets.maxByOrNull { asset ->
            val name = asset.name.lowercase()
            var score = 0
            if (name.contains("universal")) score += 1_000
            if (name.contains("release")) score += 200
            if (name.contains("debug")) score -= 50

            supportedAbis.forEachIndexed { index, abi ->
                if (name.contains(abi.lowercase())) {
                    score += 150 - index
                }
            }

            score
        }
    }

    private fun isUpdateAvailable(currentVersionName: String, latestTagName: String): Boolean {
        val current = normalizeVersion(currentVersionName)
        val latest = normalizeVersion(latestTagName)
        return current != latest
    }

    private fun normalizeVersion(v: String): String =
        v.trim().removePrefix("v").removePrefix("V")

    private fun isCanaryVersionName(versionName: String): Boolean {
        val v = versionName.trim()
        return v.startsWith("c-") || v.contains("canary", ignoreCase = true) || v.contains(
            "alpha",
            ignoreCase = true
        )
    }

    private fun applyGitHubCdn(originalUrl: String, githubCdn: String): String {
        val cdn = githubCdn.trim()
        if (cdn.isBlank()) return originalUrl

        return if (cdn.contains("{url}")) {
            cdn.replace("{url}", originalUrl)
        } else {
            val prefix = if (cdn.endsWith("/")) cdn else "$cdn/"
            prefix + originalUrl
        }
    }
}

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<GitHubAsset> = emptyList()
)

@Serializable
private data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = ""
)
