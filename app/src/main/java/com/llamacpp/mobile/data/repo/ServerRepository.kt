package com.llamacpp.mobile.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.llamacpp.mobile.domain.model.ServerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class ServerRepository(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) {
    private val serversKey = stringPreferencesKey("servers_json")
    private val activeKey = stringPreferencesKey("active_server_id")

    val servers: Flow<List<ServerConfig>> = dataStore.data.map { prefs ->
        val decoded = prefs[serversKey]
            ?.let { runCatching { json.decodeFromString<List<ServerConfig>>(it) }.getOrNull() }
            ?: emptyList()
        decoded.ifEmpty { listOf(defaultServer()) }
    }

    val activeServer: Flow<ServerConfig> = combine(dataStore.data, servers) { prefs, list ->
        val id = prefs[activeKey]
        list.firstOrNull { it.id == id } ?: list.first()
    }

    suspend fun upsert(server: ServerConfig) {
        dataStore.edit { prefs ->
            val current = prefs[serversKey]
                ?.let { runCatching { json.decodeFromString<List<ServerConfig>>(it) }.getOrNull() }
                ?: emptyList()
            val merged = current.toMutableList()
            val index = merged.indexOfFirst { it.id == server.id }
            if (index >= 0) merged[index] = server else merged.add(server)
            prefs[serversKey] = json.encodeToString(merged)
            if (prefs[activeKey] == null) prefs[activeKey] = server.id
        }
    }

    suspend fun delete(id: String) {
        dataStore.edit { prefs ->
            val current = prefs[serversKey]
                ?.let { runCatching { json.decodeFromString<List<ServerConfig>>(it) }.getOrNull() }
                ?: emptyList()
            val merged = current.filterNot { it.id == id }.ifEmpty { listOf(defaultServer()) }
            prefs[serversKey] = json.encodeToString(merged)
            if (prefs[activeKey] == id) prefs[activeKey] = merged.first().id
        }
    }

    suspend fun setActive(id: String) {
        dataStore.edit { it[activeKey] = id }
    }

    companion object {
        const val DEFAULT_SERVER_ID = "default-local"
        const val DEFAULT_BASE_URL = "http://192.168.2.3:8080"

        fun defaultServer() = ServerConfig(
            id = DEFAULT_SERVER_ID,
            name = "Local · 192.168.2.3",
            baseUrl = DEFAULT_BASE_URL,
        )
    }
}
