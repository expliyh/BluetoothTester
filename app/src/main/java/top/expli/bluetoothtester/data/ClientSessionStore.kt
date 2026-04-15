package top.expli.bluetoothtester.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import top.expli.bluetoothtester.model.ClientSessionSnapshot
import java.io.File

/**
 * Client 历史会话持久化存储。
 * 使用独立 DataStore 文件（"spp_client_sessions"），全局单实例。
 */
object ClientSessionStore {
    private val KEY_HISTORY: Preferences.Key<String> =
        stringPreferencesKey("client_session_history_json")

    private const val MAX_HISTORY = 20

    @Volatile
    private var instance: DataStore<Preferences>? = null

    private fun dataStore(context: Context): DataStore<Preferences> =
        instance ?: synchronized(this) {
            instance ?: PreferenceDataStoreFactory.create(
                produceFile = {
                    File(context.filesDir, "datastore/spp_client_sessions.preferences_pb")
                }
            ).also { instance = it }
        }

    fun observe(context: Context): Flow<List<ClientSessionSnapshot>> =
        dataStore(context).data.map { prefs ->
            prefs[KEY_HISTORY]?.let { json ->
                runCatching { Json.decodeFromString<List<ClientSessionSnapshot>>(json) }
                    .getOrDefault(emptyList())
            } ?: emptyList()
        }

    suspend fun save(context: Context, history: List<ClientSessionSnapshot>) {
        dataStore(context).edit { prefs ->
            prefs[KEY_HISTORY] = Json.encodeToString(history)
        }
    }

    /** 添加快照，自动按 disconnectedAt 降序排列并裁剪到 [MAX_HISTORY] 条（保留最新）。 */
    suspend fun addSnapshot(context: Context, snapshot: ClientSessionSnapshot) {
        dataStore(context).edit { prefs ->
            val current = prefs[KEY_HISTORY]?.let { json ->
                runCatching { Json.decodeFromString<List<ClientSessionSnapshot>>(json) }
                    .getOrDefault(emptyList())
            } ?: emptyList()

            val updated = (listOf(snapshot) + current)
                .sortedByDescending { it.disconnectedAt }
                .take(MAX_HISTORY)

            prefs[KEY_HISTORY] = Json.encodeToString(updated)
        }
    }

    suspend fun removeSnapshot(context: Context, sessionId: String) {
        dataStore(context).edit { prefs ->
            val current = prefs[KEY_HISTORY]?.let { json ->
                runCatching { Json.decodeFromString<List<ClientSessionSnapshot>>(json) }
                    .getOrDefault(emptyList())
            } ?: emptyList()

            val updated = current.filter { it.sessionId != sessionId }
            prefs[KEY_HISTORY] = Json.encodeToString(updated)
        }
    }

    suspend fun clearAll(context: Context) {
        dataStore(context).edit { prefs ->
            prefs.remove(KEY_HISTORY)
        }
    }
}
