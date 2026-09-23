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
    private val pistonUrlKey = stringPreferencesKey("piston_url")
    private val onboardingKey = booleanPreferencesKey("onboarding_completed")

    val themeMode: Flow<ThemeMode> = dataStore.data.map { ThemeMode.from(it[themeKey]) }

    /** Subtle tick per streamed chunk while generating. */
    val hapticsWhileGenerating: Flow<Boolean> = dataStore.data.map { it[hapticsKey] ?: true }

    /** Tools the user has switched off (all tools are on by default). */
    val disabledTools: Flow<Set<String>> = dataStore.data.map { it[disabledToolsKey] ?: emptySet() }

    /** Base URL of a self-hosted Piston instance used by the Python tool. */
    val pistonUrl: Flow<String> = dataStore.data.map { it[pistonUrlKey] ?: "" }

    /** False until the user has completed first-run setup. */
    val onboardingCompleted: Flow<Boolean> = dataStore.data.map { it[onboardingKey] ?: false }

    /** Per-model sampler settings, or null when the user hasn't customized them. */
    fun savedSampler(model: String?): Flow<SamplerSettings?> = dataStore.data.map { prefs ->
        (runCatching { decodeOrNull(prefs[samplersKey]) }.getOrNull() ?: emptyMap())[model ?: GLOBAL_KEY]
    }

    suspend fun saveSampler(model: String?, settings: SamplerSettings) {
        dataStore.edit { prefs ->
            val map = currentSamplers(prefs) ?: return@edit
            map[model ?: GLOBAL_KEY] = settings
            prefs[samplersKey] = json.encodeToString(map)
        }
    }

    suspend fun clearSampler(model: String?) {
        dataStore.edit { prefs ->
            val map = currentSamplers(prefs) ?: return@edit
            map.remove(model ?: GLOBAL_KEY)
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

    suspend fun setPistonUrl(url: String) {
        dataStore.edit { it[pistonUrlKey] = url.trim() }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[onboardingKey] = completed }
    }

    /** Returns null when the key is absent; throws when present but undecodable. */
    private fun decodeOrNull(raw: String?): Map<String, SamplerSettings>? =
        raw?.let { json.decodeFromString<Map<String, SamplerSettings>>(it) }

    /** Mutable copy of the stored samplers, or null when the stored blob is undecodable (mutation must abort). */
    private fun currentSamplers(prefs: Preferences): MutableMap<String, SamplerSettings>? =
        runCatching { decodeOrNull(prefs[samplersKey]) }
            .getOrElse { return null }
            ?.toMutableMap()
            ?: mutableMapOf()

    companion object {
        const val GLOBAL_KEY = "__global__"
    }
}
