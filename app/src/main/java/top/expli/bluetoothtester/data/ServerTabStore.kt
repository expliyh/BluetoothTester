package top.expli.bluetoothtester.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import top.expli.bluetoothtester.model.ServerTabConfig

private const val STORE_NAME = "spp_server_tabs"
private val Context.serverTabDataStore by preferencesDataStore(name = STORE_NAME)

object ServerTabStore {
    private val KEY_TABS: Preferences.Key<String> = stringPreferencesKey("server_tabs_json")

    fun observe(context: Context): Flow<List<ServerTabConfig>> =
        context.serverTabDataStore.data.map { prefs ->
            prefs[KEY_TABS]?.let { json ->
                runCatching { Json.decodeFromString<List<ServerTabConfig>>(json) }.getOrDefault(emptyList())
            } ?: emptyList()
        }

    suspend fun save(context: Context, tabs: List<ServerTabConfig>) {
        context.serverTabDataStore.edit { prefs ->
            prefs[KEY_TABS] = Json.encodeToString(tabs)
        }
    }
}
