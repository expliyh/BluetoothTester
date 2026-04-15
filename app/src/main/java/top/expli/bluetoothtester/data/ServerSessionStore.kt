package top.expli.bluetoothtester.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import top.expli.bluetoothtester.model.ServerSessionSnapshot
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Each Server Tab uses an independent DataStore file for session history persistence.
 * DataStore name: "spp_server_session_{tabId}"
 */
object ServerSessionStore {
    private val KEY_HISTORY: Preferences.Key<String> = stringPreferencesKey("session_history_json")

    private val delegates = ConcurrentHashMap<String, DataStore<Preferences>>()

    private fun dataStore(context: Context, tabId: String): DataStore<Preferences> =
        delegates.computeIfAbsent(tabId) {
            PreferenceDataStoreFactory.create(
                produceFile = {
                    File(context.filesDir, "datastore/spp_server_session_$tabId.preferences_pb")
                }
            )
        }

    fun observe(context: Context, tabId: String): Flow<List<ServerSessionSnapshot>> =
        dataStore(context, tabId).data.map { prefs ->
            prefs[KEY_HISTORY]?.let { json ->
                runCatching { Json.decodeFromString<List<ServerSessionSnapshot>>(json) }.getOrDefault(emptyList())
            } ?: emptyList()
        }

    suspend fun save(context: Context, tabId: String, history: List<ServerSessionSnapshot>) {
        dataStore(context, tabId).edit { prefs ->
            prefs[KEY_HISTORY] = Json.encodeToString(history)
        }
    }

    suspend fun delete(context: Context, tabId: String) {
        // 1. Clear preferences data
        val store = delegates[tabId]
        store?.edit { it.clear() }

        // 2. Remove reference from delegates Map
        delegates.remove(tabId)

        // 3. Delete disk file
        val file = context.preferencesDataStoreFile("spp_server_session_$tabId")
        file.delete()
    }
}
