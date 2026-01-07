package top.expli.bluetoothtester.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import top.expli.bluetoothtester.ui.ThemeOption

private const val STORE_NAME = "app_settings"

val Context.settingsDataStore by preferencesDataStore(name = STORE_NAME)

object SettingsStore {
    private val KEY_THEME = intPreferencesKey("theme_option") // 0=System,1=Light,2=Dark
    private val KEY_DYNAMIC = booleanPreferencesKey("dynamic_color_enabled")

    data class Settings(
        val theme: ThemeOption = ThemeOption.System,
        val dynamicColorEnabled: Boolean = true
    )

    fun observe(context: Context): Flow<Settings> =
        context.settingsDataStore.data.map { prefs ->
            val themeOrdinal = prefs[KEY_THEME] ?: ThemeOption.System.ordinal
            val dynamic = prefs[KEY_DYNAMIC] ?: true
            Settings(
                theme = ThemeOption.entries.toTypedArray()
                    .getOrElse(themeOrdinal) { ThemeOption.System },
                dynamicColorEnabled = dynamic
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
}
