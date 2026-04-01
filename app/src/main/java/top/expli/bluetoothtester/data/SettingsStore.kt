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
import top.expli.bluetoothtester.ui.ThemeOption

private const val STORE_NAME = "app_settings"

val Context.settingsDataStore by preferencesDataStore(name = STORE_NAME)

object SettingsStore {
    private val KEY_THEME = intPreferencesKey("theme_option") // 0=System,1=Light,2=Dark
    private val KEY_DYNAMIC = booleanPreferencesKey("dynamic_color_enabled")
    private val KEY_GITHUB_CDN = stringPreferencesKey("github_cdn")
    val KEY_ACTIVE_CONNECTIONS = booleanPreferencesKey("active_connections")

    data class Settings(
        val theme: ThemeOption = ThemeOption.System,
        val dynamicColorEnabled: Boolean = true,
        val githubCdn: String = ""
    )

    internal object Mapper {
        fun toSettings(themeOrdinal: Int?, dynamicEnabled: Boolean?, githubCdn: String?): Settings {
            return Settings(
                theme = resolveTheme(themeOrdinal),
                dynamicColorEnabled = dynamicEnabled ?: true,
                githubCdn = githubCdn.orEmpty()
            )
        }

        fun resolveTheme(themeOrdinal: Int?): ThemeOption {
            val safeOrdinal = themeOrdinal ?: ThemeOption.System.ordinal
            return ThemeOption.entries.toTypedArray().getOrElse(safeOrdinal) { ThemeOption.System }
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
}
