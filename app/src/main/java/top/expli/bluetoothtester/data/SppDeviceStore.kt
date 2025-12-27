package top.expli.bluetoothtester.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.expli.bluetoothtester.model.SppDevice

private const val STORE_NAME = "spp_devices"
private val Context.sppDataStore by preferencesDataStore(name = STORE_NAME)

object SppDeviceStore {
    private val KEY_DEVICES: Preferences.Key<String> = stringPreferencesKey("devices_json")

    fun observe(context: Context): Flow<List<SppDevice>> =
        context.sppDataStore.data.map { prefs ->
            prefs[KEY_DEVICES]?.let { json ->
                runCatching { Json.decodeFromString<List<SppDevice>>(json) }.getOrDefault(emptyList())
            } ?: emptyList()
        }

    suspend fun save(context: Context, devices: List<SppDevice>) {
        context.sppDataStore.edit { prefs ->
            prefs[KEY_DEVICES] = Json.encodeToString(devices)
        }
    }
}
