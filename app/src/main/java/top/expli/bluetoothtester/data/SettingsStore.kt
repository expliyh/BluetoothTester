package top.expli.bluetoothtester.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import top.expli.bluetoothtester.model.SecurityMode
import top.expli.bluetoothtester.ui.ThemeOption
import top.expli.bluetoothtester.ui.theme.ThemePreset

private const val STORE_NAME = "app_settings"

val Context.settingsDataStore by preferencesDataStore(name = STORE_NAME)

object SettingsStore {
    private val KEY_THEME = intPreferencesKey("theme_option") // 0=System,1=Light,2=Dark
    private val KEY_DYNAMIC = booleanPreferencesKey("dynamic_color_enabled")
    private val KEY_THEME_PRESET = stringPreferencesKey("theme_preset")
    private val KEY_GITHUB_CDN = stringPreferencesKey("github_cdn")
    val KEY_ACTIVE_CONNECTIONS = booleanPreferencesKey("active_connections")
    private val KEY_LOCAL_SOCKET_DEBUG = booleanPreferencesKey("local_socket_debug_mode")
    private val KEY_DEV_MODE_UNLOCKED = booleanPreferencesKey("dev_mode_unlocked")
    private val KEY_CLIENT_DEFAULT_SECURITY_MODE = stringPreferencesKey("client_default_security_mode")

    data class Settings(
        val theme: ThemeOption = ThemeOption.System,
        val dynamicColorEnabled: Boolean = true,
        val themePreset: ThemePreset = ThemePreset.Default,
        val githubCdn: String = ""
    )

    internal object Mapper {
        fun toSettings(
            themeOrdinal: Int?,
            dynamicEnabled: Boolean?,
            themePresetName: String?,
            githubCdn: String?
        ): Settings {
            return Settings(
                theme = resolveTheme(themeOrdinal),
                dynamicColorEnabled = dynamicEnabled ?: true,
                themePreset = resolveThemePreset(themePresetName),
                githubCdn = githubCdn.orEmpty()
            )
        }

        fun resolveTheme(themeOrdinal: Int?): ThemeOption {
            val safeOrdinal = themeOrdinal ?: ThemeOption.System.ordinal
            return ThemeOption.entries.toTypedArray().getOrElse(safeOrdinal) { ThemeOption.System }
        }

        fun resolveThemePreset(name: String?): ThemePreset {
            return name?.let { n ->
                ThemePreset.entries.find { it.name == n }
            } ?: ThemePreset.Default
        }

        fun normalizeGithubCdn(cdn: String): String? {
            val normalized = cdn.trim()
            return normalized.ifBlank { null }
        }
    }

    fun observe(context: Context): Flow<Settings> =
        context.settingsDataStore.data.map { prefs ->
            Mapper.toSettings(
                themeOrdinal = prefs[KEY_THEME],
                dynamicEnabled = prefs[KEY_DYNAMIC],
                themePresetName = prefs[KEY_THEME_PRESET],
                githubCdn = prefs[KEY_GITHUB_CDN]
            )
        }

    suspend fun updateTheme(context: Context, theme: ThemeOption) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_THEME] = theme.ordinal
        }
    }

    suspend fun updateDynamic(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_DYNAMIC] = enabled
        }
    }

    suspend fun updateThemePreset(context: Context, preset: ThemePreset) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_THEME_PRESET] = preset.name
        }
    }

    suspend fun updateGithubCdn(context: Context, cdn: String) {
        context.settingsDataStore.edit { prefs ->
            val normalized = Mapper.normalizeGithubCdn(cdn)
            if (normalized == null) {
                prefs.remove(KEY_GITHUB_CDN)
            } else {
                prefs[KEY_GITHUB_CDN] = normalized
            }
        }
    }

    suspend fun setActiveConnections(context: Context, active: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_ACTIVE_CONNECTIONS] = active
        }
    }

    suspend fun wasActiveConnections(context: Context): Boolean {
        val prefs = context.settingsDataStore.data.first()
        return prefs[KEY_ACTIVE_CONNECTIONS] ?: false
    }

    fun observeLocalSocketDebug(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs ->
            prefs[KEY_LOCAL_SOCKET_DEBUG] ?: false
        }

    suspend fun updateLocalSocketDebug(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_LOCAL_SOCKET_DEBUG] = enabled
        }
    }

    fun observeDevModeUnlocked(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs ->
            prefs[KEY_DEV_MODE_UNLOCKED] ?: false
        }

    suspend fun updateDevModeUnlocked(context: Context, unlocked: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_DEV_MODE_UNLOCKED] = unlocked
        }
    }

    fun observeClientDefaultSecurityMode(context: Context): Flow<SecurityMode> =
        context.settingsDataStore.data.map { prefs ->
            val raw = prefs[KEY_CLIENT_DEFAULT_SECURITY_MODE] ?: "Secure"
            try { SecurityMode.valueOf(raw) } catch (_: Exception) { SecurityMode.Secure }
        }

    suspend fun updateClientDefaultSecurityMode(context: Context, mode: SecurityMode) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_CLIENT_DEFAULT_SECURITY_MODE] = mode.name
        }
    }
}
