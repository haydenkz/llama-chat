package com.llamacpp.mobile.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.llamacpp.mobile.domain.model.SamplerSettings
import com.llamacpp.mobile.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class SettingsRepository(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) {
    private val samplersKey = stringPreferencesKey("samplers_json")
    private val themeKey = stringPreferencesKey("theme_mode")
    private val hapticsKey = booleanPreferencesKey("haptics_while_generating")
    private val disabledToolsKey = stringSetPreferencesKey("disabled_tools")

    val themeMode: Flow<ThemeMode> = dataStore.data.map { ThemeMode.from(it[themeKey]) }

    /** Subtle tick per streamed chunk while generating (ChatGPT-style). */
    val hapticsWhileGenerating: Flow<Boolean> = dataStore.data.map { it[hapticsKey] ?: true }

    /** Tools the user has switched off (all tools are on by default). */
    val disabledTools: Flow<Set<String>> = dataStore.data.map { it[disabledToolsKey] ?: emptySet() }

    /** Per-model sampler settings; falls back to defaults when unset. */
    fun sampler(model: String?): Flow<SamplerSettings> = dataStore.data.map { prefs ->
        decodeSamplers(prefs[samplersKey])[model ?: GLOBAL_KEY] ?: SamplerSettings()
    }

    suspend fun saveSampler(model: String?, settings: SamplerSettings) {
        dataStore.edit { prefs ->
            val map = decodeSamplers(prefs[samplersKey]).toMutableMap()
            map[model ?: GLOBAL_KEY] = settings
            prefs[samplersKey] = json.encodeToString(map)
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[themeKey] = mode.wire }
    }

    suspend fun setHapticsWhileGenerating(enabled: Boolean) {
        dataStore.edit { it[hapticsKey] = enabled }
    }

    suspend fun setToolEnabled(name: String, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[disabledToolsKey] ?: emptySet()
            prefs[disabledToolsKey] = if (enabled) current - name else current + name
        }
    }

    private fun decodeSamplers(raw: String?): Map<String, SamplerSettings> =
        raw?.let { runCatching { json.decodeFromString<Map<String, SamplerSettings>>(it) }.getOrNull() }
            ?: emptyMap()

    companion object {
        const val GLOBAL_KEY = "__global__"
    }
}
